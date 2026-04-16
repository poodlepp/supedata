package com.dex.infrastructure.blockchain.listener;

import io.reactivex.disposables.Disposable;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.websocket.WebSocketService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Uniswap V2 Swap 事件监听器
 */
@Slf4j
@Component
public class UniswapV2SwapListener {

    private static final String ETH_USDT_PAIR = "0x0d4a11d5eeaac28ec3f61d100daF4d40471f1852";
    private static final String PAIR_NAME = "ETH/USDT";
    private static final String WETH_SYMBOL = "WETH";
    private static final String USDT_SYMBOL = "USDT";
    private static final int USDT_DECIMALS = 6;
    private static final int WETH_DECIMALS = 18;
    private static final int MAX_EVENTS = 120;
    private static final long LOOKBACK_BLOCKS = 30;
    private static final long WS_CONNECT_TIMEOUT_SECONDS = 5;

    private static final Event SWAP_EVENT = new Event(
            "Swap",
            Arrays.asList(
                    TypeReference.create(Address.class, true),
                    TypeReference.create(Uint256.class),
                    TypeReference.create(Uint256.class),
                    TypeReference.create(Uint256.class),
                    TypeReference.create(Uint256.class),
                    TypeReference.create(Address.class, true)
            )
    );

    private final Web3j web3j;
    private final Web3j wsWeb3j;
    private final WebSocketService webSocketService;
    private final List<SwapEventListener> listeners = new CopyOnWriteArrayList<>();
    private final Deque<SwapEventData> recentEvents = new ArrayDeque<>();
    private final Set<String> seenEventKeys = new HashSet<>();
    private final Map<BigInteger, Long> blockTimestampCache = new LinkedHashMap<>();
    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService wsConnectExecutor = Executors.newSingleThreadExecutor();

    private volatile Disposable swapSubscription;
    private volatile boolean wsConnected;
    private volatile String activeTransport = "idle";

    public UniswapV2SwapListener(
            Web3j web3j,
            @Qualifier("wsWeb3j") Web3j wsWeb3j,
            WebSocketService webSocketService
    ) {
        this.web3j = web3j;
        this.wsWeb3j = wsWeb3j;
        this.webSocketService = webSocketService;
    }

    public synchronized void start() {
        if (swapSubscription != null && !swapSubscription.isDisposed()) {
            log.info("Uniswap V2 swap listener already running via {}", activeTransport);
            return;
        }

        log.info("Starting Uniswap V2 swap listener for pair {}", ETH_USDT_PAIR);
        startupExecutor.execute(this::initializeListenerAsync);
    }

    public synchronized void stop() {
        if (swapSubscription != null) {
            log.info("Stopping Uniswap V2 swap listener ({})", activeTransport);
            swapSubscription.dispose();
            swapSubscription = null;
        }
        activeTransport = "idle";
    }

    public void subscribe(SwapEventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(SwapEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized List<SwapEventData> getRecentEvents() {
        return recentEvents.stream().toList();
    }

    @PreDestroy
    public void destroy() {
        stop();
        startupExecutor.shutdownNow();
        wsConnectExecutor.shutdownNow();
        closeWebSocketQuietly();
    }

    private void initializeListenerAsync() {
        bootstrapRecentEvents();

        if (ensureWebSocketConnected()) {
            subscribeToWebSocketStream();
            return;
        }

        log.warn("WebSocket unavailable, falling back to HTTP Flowable polling");
        subscribeToHttpFlowable();
    }

    private synchronized void bootstrapRecentEvents() {
        try {
            recentEvents.clear();
            seenEventKeys.clear();
            blockTimestampCache.clear();

            BigInteger latestBlock = web3j.ethBlockNumber().send().getBlockNumber();
            BigInteger fromBlock = latestBlock.subtract(BigInteger.valueOf(LOOKBACK_BLOCKS)).max(BigInteger.ZERO);
            EthFilter filter = new EthFilter(DefaultBlockParameter.valueOf(fromBlock), DefaultBlockParameter.valueOf(latestBlock), ETH_USDT_PAIR);
            filter.addSingleTopic(EventEncoder.encode(SWAP_EVENT));

            List<Log> logs = web3j.ethGetLogs(filter).send().getLogs().stream()
                    .map(result -> (Log) result.get())
                    .toList();

            logs.forEach(this::handleLog);
            log.info("Bootstrapped {} listener-visible swap events", recentEvents.size());
        } catch (Exception e) {
            log.error("Failed to bootstrap recent swap events", e);
        }
    }

    private synchronized boolean ensureWebSocketConnected() {
        if (wsConnected) {
            return true;
        }

        CompletableFuture<Boolean> connectFuture = CompletableFuture.supplyAsync(() -> {
            try {
                webSocketService.connect();
                return true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, wsConnectExecutor);

        try {
            boolean connected = connectFuture.get(WS_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            wsConnected = connected;
            return connected;
        } catch (TimeoutException e) {
            connectFuture.cancel(true);
            wsConnected = false;
            closeWebSocketQuietly();
            log.warn("WebSocket connect timed out after {} seconds", WS_CONNECT_TIMEOUT_SECONDS);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            connectFuture.cancel(true);
            wsConnected = false;
            log.warn("WebSocket connect interrupted", e);
            return false;
        } catch (ExecutionException e) {
            connectFuture.cancel(true);
            wsConnected = false;
            log.error("Failed to connect to WebSocket", e.getCause());
            return false;
        }
    }

    private void subscribeToWebSocketStream() {
        EthFilter filter = new EthFilter(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, ETH_USDT_PAIR);
        filter.addSingleTopic(EventEncoder.encode(SWAP_EVENT));
        activeTransport = "websocket";

        swapSubscription = wsWeb3j.ethLogFlowable(filter)
                .subscribe(
                        this::handleLog,
                        error -> {
                            log.error("Uniswap V2 swap WebSocket subscription error", error);
                            listeners.forEach(listener -> listener.onError(new RuntimeException(error)));
                            fallbackToHttpFlowable();
                        }
                );
    }

    private void subscribeToHttpFlowable() {
        EthFilter filter = new EthFilter(DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, ETH_USDT_PAIR);
        filter.addSingleTopic(EventEncoder.encode(SWAP_EVENT));
        activeTransport = "http-flowable";

        swapSubscription = web3j.ethLogFlowable(filter)
                .subscribe(
                        this::handleLog,
                        error -> {
                            log.error("Uniswap V2 swap HTTP Flowable subscription error", error);
                            listeners.forEach(listener -> listener.onError(new RuntimeException(error)));
                        }
                );
        log.info("Uniswap V2 swap listener is now running via HTTP Flowable polling");
    }

    private synchronized void fallbackToHttpFlowable() {
        stop();
        closeWebSocketQuietly();
        subscribeToHttpFlowable();
    }

    private void closeWebSocketQuietly() {
        try {
            webSocketService.close();
        } catch (Exception e) {
            log.debug("Ignoring WebSocket close error", e);
        } finally {
            wsConnected = false;
        }
    }

    private void handleLog(Log logEntry) {
        SwapEventData eventData = decodeSwapEvent(logEntry);
        if (eventData == null) {
            return;
        }

        if (storeEvent(eventData, buildEventKey(logEntry))) {
            notifyListeners(eventData);
        }
    }

    private SwapEventData decodeSwapEvent(Log logEntry) {
        try {
            List<String> topics = logEntry.getTopics();
            if (topics == null || topics.size() < 3) {
                return null;
            }

            List<Type> decodedValues = FunctionReturnDecoder.decode(logEntry.getData(), SWAP_EVENT.getNonIndexedParameters());
            if (decodedValues.size() < 4) {
                return null;
            }

            BigInteger amount0In = (BigInteger) decodedValues.get(0).getValue();
            BigInteger amount1In = (BigInteger) decodedValues.get(1).getValue();
            BigInteger amount0Out = (BigInteger) decodedValues.get(2).getValue();
            BigInteger amount1Out = (BigInteger) decodedValues.get(3).getValue();

            boolean isSellEth = amount0In.signum() > 0 && amount1Out.signum() > 0;
            boolean isBuyEth = amount1In.signum() > 0 && amount0Out.signum() > 0;
            if (!isSellEth && !isBuyEth) {
                return null;
            }

            BigDecimal wethAmount = isSellEth ? toDecimal(amount0In, WETH_DECIMALS) : toDecimal(amount0Out, WETH_DECIMALS);
            BigDecimal usdtAmount = isSellEth ? toDecimal(amount1Out, USDT_DECIMALS) : toDecimal(amount1In, USDT_DECIMALS);
            BigDecimal price = wethAmount.signum() == 0 ? BigDecimal.ZERO : usdtAmount.divide(wethAmount, 8, RoundingMode.HALF_UP);

            return SwapEventData.builder()
                    .pair(PAIR_NAME)
                    .pairAddress(ETH_USDT_PAIR)
                    .txHash(logEntry.getTransactionHash())
                    .blockNumber(logEntry.getBlockNumber().longValue())
                    .timestamp(resolveBlockTimestamp(logEntry.getBlockNumber()))
                    .sender(topicToAddress(topics.get(1)))
                    .recipient(topicToAddress(topics.get(2)))
                    .tokenInSymbol(isSellEth ? WETH_SYMBOL : USDT_SYMBOL)
                    .tokenOutSymbol(isSellEth ? USDT_SYMBOL : WETH_SYMBOL)
                    .amountIn(isSellEth ? wethAmount.doubleValue() : usdtAmount.doubleValue())
                    .amountOut(isSellEth ? usdtAmount.doubleValue() : wethAmount.doubleValue())
                    .price(price.doubleValue())
                    .side(isSellEth ? "SELL_ETH" : "BUY_ETH")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to decode swap event: txHash={}", logEntry.getTransactionHash(), e);
            return null;
        }
    }

    private synchronized boolean storeEvent(SwapEventData eventData, String eventKey) {
        if (seenEventKeys.contains(eventKey)) {
            return false;
        }

        recentEvents.addFirst(eventData);
        seenEventKeys.add(eventKey);

        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.removeLast();
        }

        if (recentEvents.size() == MAX_EVENTS) {
            rebuildSeenEventKeys();
        }

        return true;
    }

    private synchronized void rebuildSeenEventKeys() {
        seenEventKeys.clear();
        recentEvents.forEach(event -> seenEventKeys.add(event.getTxHash() + ":" + event.getBlockNumber()));
    }

    private void notifyListeners(SwapEventData eventData) {
        listeners.forEach(listener -> {
            try {
                listener.onSwapEvent(eventData);
            } catch (Exception e) {
                listener.onError(e);
            }
        });
    }

    private long resolveBlockTimestamp(BigInteger blockNumber) {
        Long cachedTimestamp = getCachedBlockTimestamp(blockNumber);
        if (cachedTimestamp != null) {
            return cachedTimestamp;
        }

        try {
            EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false).send().getBlock();
            long resolvedTimestamp = block == null ? System.currentTimeMillis() : block.getTimestamp().longValue() * 1000;
            cacheBlockTimestamp(blockNumber, resolvedTimestamp);
            return resolvedTimestamp;
        } catch (Exception e) {
            log.debug("Failed to resolve block timestamp for {}", blockNumber, e);
            return System.currentTimeMillis();
        }
    }

    private synchronized Long getCachedBlockTimestamp(BigInteger blockNumber) {
        return blockTimestampCache.get(blockNumber);
    }

    private synchronized void cacheBlockTimestamp(BigInteger blockNumber, long timestamp) {
        blockTimestampCache.put(blockNumber, timestamp);
        if (blockTimestampCache.size() > MAX_EVENTS) {
            BigInteger oldestBlock = blockTimestampCache.keySet().iterator().next();
            blockTimestampCache.remove(oldestBlock);
        }
    }

    private String topicToAddress(String topic) {
        return "0x" + topic.substring(topic.length() - 40);
    }

    private BigDecimal toDecimal(BigInteger value, int decimals) {
        return new BigDecimal(value).divide(BigDecimal.TEN.pow(decimals), 8, RoundingMode.HALF_UP);
    }

    private String buildEventKey(Log logEntry) {
        return logEntry.getTransactionHash() + ":" + logEntry.getBlockNumber();
    }
}
