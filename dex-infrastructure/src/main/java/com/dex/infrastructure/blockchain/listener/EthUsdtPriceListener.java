package com.dex.infrastructure.blockchain.listener;

import com.dex.infrastructure.blockchain.provider.BlockchainProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ETH/USDT 价格监听器
 * 
 * 职责：
 * - 定期从区块链获取 ETH/USDT 价格
 * - 通知所有注册的监听器
 * - 处理错误和重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EthUsdtPriceListener {

    private final BlockchainProvider blockchainProvider;
    private final List<PriceListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final String PAIR = "ETH/USDT";
    private static final long POLL_INTERVAL_SECONDS = 10;

    private volatile ScheduledFuture<?> pollingTask;

    /**
     * 启动价格监听
     */
    public synchronized void start() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            log.info("ETH/USDT price listener already running on {}", blockchainProvider.getNetworkName());
            return;
        }

        log.info("Starting ETH/USDT price listener on {}", blockchainProvider.getNetworkName());
        pollingTask = scheduler.scheduleAtFixedRate(this::pollPrice, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 停止价格监听
     */
    public synchronized void stop() {
        if (pollingTask == null) {
            return;
        }

        log.info("Stopping ETH/USDT price listener");
        pollingTask.cancel(false);
        pollingTask = null;
    }

    /**
     * 注册价格监听器
     */
    public void subscribe(PriceListener listener) {
        if (listener.getPair().equals(PAIR) && !listeners.contains(listener)) {
            listeners.add(listener);
            log.info("Listener registered for {}", PAIR);
        }
    }

    /**
     * 取消注册价格监听器
     */
    public void unsubscribe(PriceListener listener) {
        listeners.remove(listener);
        log.info("Listener unregistered for {}", PAIR);
    }

    /**
     * 轮询价格
     */
    private void pollPrice() {
        blockchainProvider.getEthUsdtPrice()
                .thenAccept(price -> {
                    long timestamp = System.currentTimeMillis();
                    log.debug("ETH/USDT price updated: {} at {}", price, timestamp);

                    listeners.forEach(listener -> {
                        try {
                            listener.onPriceUpdate(PAIR, price, timestamp);
                        } catch (Exception e) {
                            log.error("Error notifying listener", e);
                            listener.onError(PAIR, e);
                        }
                    });
                })
                .exceptionally(e -> {
                    log.error("Failed to poll ETH/USDT price", e);
                    listeners.forEach(listener -> listener.onError(PAIR, (Exception) e));
                    return null;
                });
    }
}
