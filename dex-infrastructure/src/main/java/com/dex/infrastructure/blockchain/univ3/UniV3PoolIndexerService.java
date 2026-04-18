package com.dex.infrastructure.blockchain.univ3;

import com.dex.data.entity.UniV3IndexerCheckpoint;
import com.dex.data.entity.UniV3PoolEvent;
import com.dex.data.repository.UniV3IndexerCheckpointRepository;
import com.dex.data.repository.UniV3PoolEventRepository;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class UniV3PoolIndexerService {

    private static final long CHAIN_ID = 1L;
    private static final String DEFAULT_POOL_NAME = "Uniswap V3 WETH/USDC 0.05%";
    private static final String DEFAULT_POOL_ADDRESS = "0x88e6A0c2dDD26FEEb64F039a2c41296FcB3f5640";
    private static final String WETH_ADDRESS = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2";
    private static final String USDC_ADDRESS = "0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";
    private static final int WETH_DECIMALS = 18;
    private static final int USDC_DECIMALS = 6;

    private static final String TOPIC_SWAP = "0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67";
    private static final String TOPIC_MINT = "0x7a53080ba414158be7ec69b987b5fb7d07dee101fe85488f0853ae16239d0bde";
    private static final String TOPIC_BURN = "0x0c396cd989a39f4459b5fa1aed6a9a8ac9b4ca207b2ca72d728d2b3e6c5630c1";
    private static final String TOPIC_COLLECT = "0x70935338e69775456b1c76c54af912d8b76c5e2f5c84a6f8d4f7b3b8ea5a5f4d";
    private static final String TOPIC_FLASH = "0xbdbd159a03d1b2be2b3bd3bbf45b9e0cb1b578bf3eefec9e3ea364ab539cc73d";
    private static final String TOPIC_INITIALIZE = "0x98636036cb66b5283f1f1cda01d6e6d7f67d9d72e8f3fa9d1f5f9f3f2d95a6d4";

    private final Web3j web3j;
    private final UniV3PoolEventRepository eventRepository;
    private final UniV3IndexerCheckpointRepository checkpointRepository;
    private final UniV3KafkaProducer kafkaProducer;

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Long, Long> blockTimeCache = new ConcurrentHashMap<>();
    private volatile long lastKnownBlockTime = System.currentTimeMillis();

    @Value("${blockchain.univ3.pool-address:" + DEFAULT_POOL_ADDRESS + "}")
    private String poolAddress;

    @Value("${blockchain.univ3.pool-name:" + DEFAULT_POOL_NAME + "}")
    private String poolName;

    @Value("${blockchain.univ3.lookback-blocks:1000}")
    private long lookbackBlocks;

    @Value("${blockchain.univ3.scan-chunk-size:100}")
    private long scanChunkSize;

    @Value("${blockchain.univ3.confirmations:6}")
    private int confirmations;

    @Value("${blockchain.univ3.reorg-window:12}")
    private int reorgWindow;

    @Value("${blockchain.univ3.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void normalize() {
        poolAddress = poolAddress.toLowerCase();
    }

    @Scheduled(initialDelayString = "${blockchain.univ3.initial-delay-ms:3000}", fixedDelayString = "${blockchain.univ3.poll-interval-ms:5000}")
    public void scheduledIndex() {
        if (!enabled) {
            return;
        }
        if (!lock.tryLock()) {
            log.debug("UniV3 indexer still running, skip this round");
            return;
        }
        try {
            syncOnce();
        } catch (Exception e) {
            log.error("UniV3 indexer sync failed", e);
        } finally {
            lock.unlock();
        }
    }

    public UniV3PoolSummary getSummary() {
        long latest = latestBlock();
        long safeLatest = Math.max(0L, latest - confirmations);
        UniV3IndexerCheckpoint checkpoint = ensureCheckpoint(safeLatest);
        return UniV3PoolSummary.builder()
                .chainId(CHAIN_ID)
                .poolAddress(poolAddress)
                .poolName(poolName)
                .latestBlock(latest)
                .safeLatestBlock(safeLatest)
                .latestCommittedBlock(checkpoint.getLastCommittedBlock())
                .startBlock(checkpoint.getStartBlock())
                .syncLag(Math.max(0L, safeLatest - checkpoint.getLastCommittedBlock()))
                .totalEvents(eventRepository.countAll(CHAIN_ID, poolAddress))
                .latestEventTime(eventRepository.findLatestBlockTime(CHAIN_ID, poolAddress))
                .eventCounts(eventRepository.countByType(CHAIN_ID, poolAddress))
                .status(enabled ? "RUNNING" : "DISABLED")
                .build();
    }

    public List<UniV3PoolEvent> getRecentEvents(String eventType, int limit) {
        return eventRepository.findRecentEvents(CHAIN_ID, poolAddress, eventType, limit);
    }

    private void syncOnce() {
        long latest = latestBlock();
        long safeLatest = Math.max(0L, latest - confirmations);
        UniV3IndexerCheckpoint checkpoint = ensureCheckpoint(safeLatest);

        if (safeLatest - checkpoint.getLastCommittedBlock() <= Math.max(reorgWindow * 2L, scanChunkSize)) {
            maybeHandleReorg(checkpoint);
            checkpoint = ensureCheckpoint(safeLatest);
        }

        if (safeLatest <= checkpoint.getLastCommittedBlock()) {
            return;
        }

        long fromBlock = checkpoint.getLastCommittedBlock() + 1;
        while (fromBlock <= safeLatest) {
            long toBlock = Math.min(fromBlock + scanChunkSize - 1, safeLatest);
            List<DecodedEvent> events = scanRange(fromBlock, toBlock);
            Map<Long, List<DecodedEvent>> eventsByBlock = new LinkedHashMap<>();
            for (DecodedEvent event : events) {
                eventsByBlock.computeIfAbsent(event.blockNumber, ignored -> new ArrayList<>()).add(event);
            }

            long currentBlock = fromBlock;
            String lastSeenBlockHash = checkpoint.getLastCommittedBlockHash();
            while (currentBlock <= toBlock) {
                List<DecodedEvent> blockEvents = eventsByBlock.getOrDefault(currentBlock, List.of());
                String blockHash = resolveBlockHash(currentBlock, blockEvents, lastSeenBlockHash);
                kafkaProducer.publishBlock(CHAIN_ID, poolAddress, poolName, currentBlock, blockHash, blockEvents.stream()
                        .sorted(Comparator.comparingInt(DecodedEvent::getTransactionIndex).thenComparingInt(DecodedEvent::getLogIndex))
                        .map(this::toEntity)
                        .toList());
                if (blockHash != null && !blockHash.isBlank()) {
                    lastSeenBlockHash = blockHash;
                }
                checkpointRepository.updateScanProgress(CHAIN_ID, poolAddress, currentBlock);
                currentBlock++;
            }
            fromBlock = toBlock + 1;
        }
    }

    private void maybeHandleReorg(UniV3IndexerCheckpoint checkpoint) {
        long lastCommittedBlock = checkpoint.getLastCommittedBlock();
        if (lastCommittedBlock < checkpoint.getStartBlock()) {
            return;
        }
        long rollbackFrom = Math.max(checkpoint.getStartBlock(), lastCommittedBlock - reorgWindow + 1);
        List<UniV3PoolEventRepository.BlockHashRow> rows = eventRepository.findRecentBlockHashes(CHAIN_ID, poolAddress, rollbackFrom);
        for (UniV3PoolEventRepository.BlockHashRow row : rows) {
            String onChainHash = getBlockHash(row.blockNumber());
            if (onChainHash != null && !onChainHash.equalsIgnoreCase(row.blockHash())) {
                long rollbackTo = Math.max(checkpoint.getStartBlock() - 1, row.blockNumber() - 1);
                log.warn("Detected reorg for pool {} at block {}, rollback to {}", poolAddress, row.blockNumber(), rollbackTo);
                eventRepository.deleteFromBlock(CHAIN_ID, poolAddress, row.blockNumber());
                checkpointRepository.rollbackTo(CHAIN_ID, poolAddress, rollbackTo);
                blockTimeCache.entrySet().removeIf(entry -> entry.getKey() >= row.blockNumber());
                return;
            }
        }
    }

    private UniV3IndexerCheckpoint ensureCheckpoint(long safeLatest) {
        UniV3IndexerCheckpoint checkpoint = checkpointRepository.find(CHAIN_ID, poolAddress);
        if (checkpoint != null) {
            return checkpoint;
        }
        long startBlock = Math.max(0L, safeLatest - lookbackBlocks + 1);
        UniV3IndexerCheckpoint created = new UniV3IndexerCheckpoint();
        created.setChainId(CHAIN_ID);
        created.setPoolAddress(poolAddress);
        created.setPoolName(poolName);
        created.setStartBlock(startBlock);
        created.setLastScannedBlock(startBlock - 1);
        created.setLastCommittedBlock(startBlock - 1);
        created.setLastCommittedBlockHash(null);
        created.setConfirmations(confirmations);
        checkpointRepository.insert(created);
        return checkpointRepository.find(CHAIN_ID, poolAddress);
    }

    private long latestBlock() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber().longValue();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch latest block", e);
        }
    }

    private List<DecodedEvent> scanRange(long fromBlock, long toBlock) {
        try {
            EthFilter filter = new EthFilter(
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                    DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                    poolAddress
            );
            filter.addOptionalTopics(TOPIC_INITIALIZE, TOPIC_MINT, TOPIC_BURN, TOPIC_SWAP, TOPIC_COLLECT, TOPIC_FLASH);
            List<?> results = web3j.ethGetLogs(filter).send().getLogs();
            return results.stream()
                    .map(result -> (EthLog.LogResult) result)
                    .map(result -> decode((Log) result.get()))
                    .flatMap(Optional::stream)
                    .sorted(Comparator
                            .comparingLong((DecodedEvent event) -> event.blockNumber)
                            .thenComparingInt(event -> event.transactionIndex)
                            .thenComparingInt(event -> event.logIndex))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to scan range " + fromBlock + "-" + toBlock, e);
        }
    }

    private Optional<DecodedEvent> decode(Log log) {
        if (log.getTopics() == null || log.getTopics().isEmpty()) {
            return Optional.empty();
        }
        String topic0 = log.getTopics().getFirst();
        List<String> words = splitWords(log.getData());
        long blockNumber = log.getBlockNumber().longValue();
        int txIndex = log.getTransactionIndex().intValue();
        int logIndex = log.getLogIndex().intValue();
        String eventUid = CHAIN_ID + ":" + poolAddress + ":" + blockNumber + ":" + txIndex + ":" + logIndex;
        DecodedEvent.DecodedEventBuilder builder = DecodedEvent.builder()
                .eventUid(eventUid)
                .chainId(CHAIN_ID)
                .poolAddress(poolAddress)
                .poolName(poolName)
                .blockNumber(blockNumber)
                .blockHash(log.getBlockHash())
                .transactionHash(log.getTransactionHash())
                .transactionIndex(txIndex)
                .logIndex(logIndex)
                .blockTime(resolveBlockTime(blockNumber));

        if (TOPIC_SWAP.equalsIgnoreCase(topic0) && words.size() >= 5 && log.getTopics().size() >= 3) {
            BigInteger amount0 = parseSigned(words.get(0), 256);
            BigInteger amount1 = parseSigned(words.get(1), 256);
            String sqrtPriceX96 = parseUnsigned(words.get(2)).toString();
            String liquidity = parseUnsigned(words.get(3)).toString();
            int tick = parseSigned(words.get(4), 24).intValue();
            String sender = topicToAddress(log.getTopics().get(1));
            String recipient = topicToAddress(log.getTopics().get(2));
            String summary = buildSwapSummary(amount0, amount1, tick);
            return Optional.of(builder
                    .eventType("SWAP")
                    .sender(sender)
                    .recipient(recipient)
                    .amount0(amount0.toString())
                    .amount1(amount1.toString())
                    .sqrtPriceX96(sqrtPriceX96)
                    .liquidity(liquidity)
                    .tick(tick)
                    .summary(summary)
                    .build());
        }

        if (TOPIC_MINT.equalsIgnoreCase(topic0) && words.size() >= 4 && log.getTopics().size() >= 4) {
            String owner = topicToAddress(log.getTopics().get(1));
            int tickLower = parseTopicSigned(log.getTopics().get(2), 24).intValue();
            int tickUpper = parseTopicSigned(log.getTopics().get(3), 24).intValue();
            String sender = wordToAddress(words.get(0));
            String amount = parseUnsigned(words.get(1)).toString();
            String amount0 = parseUnsigned(words.get(2)).toString();
            String amount1 = parseUnsigned(words.get(3)).toString();
            return Optional.of(builder
                    .eventType("MINT")
                    .sender(sender)
                    .ownerAddress(owner)
                    .tickLower(tickLower)
                    .tickUpper(tickUpper)
                    .amount(amount)
                    .amount0(amount0)
                    .amount1(amount1)
                    .summary("Mint [" + tickLower + ", " + tickUpper + "] amount=" + amount)
                    .build());
        }

        if (TOPIC_BURN.equalsIgnoreCase(topic0) && words.size() >= 3 && log.getTopics().size() >= 4) {
            String owner = topicToAddress(log.getTopics().get(1));
            int tickLower = parseTopicSigned(log.getTopics().get(2), 24).intValue();
            int tickUpper = parseTopicSigned(log.getTopics().get(3), 24).intValue();
            String amount = parseUnsigned(words.get(0)).toString();
            String amount0 = parseUnsigned(words.get(1)).toString();
            String amount1 = parseUnsigned(words.get(2)).toString();
            return Optional.of(builder
                    .eventType("BURN")
                    .ownerAddress(owner)
                    .tickLower(tickLower)
                    .tickUpper(tickUpper)
                    .amount(amount)
                    .amount0(amount0)
                    .amount1(amount1)
                    .summary("Burn [" + tickLower + ", " + tickUpper + "] amount=" + amount)
                    .build());
        }

        if (TOPIC_COLLECT.equalsIgnoreCase(topic0) && words.size() >= 3 && log.getTopics().size() >= 4) {
            String owner = topicToAddress(log.getTopics().get(1));
            int tickLower = parseTopicSigned(log.getTopics().get(2), 24).intValue();
            int tickUpper = parseTopicSigned(log.getTopics().get(3), 24).intValue();
            String recipient = wordToAddress(words.get(0));
            String amount0 = parseUnsigned(words.get(1)).toString();
            String amount1 = parseUnsigned(words.get(2)).toString();
            return Optional.of(builder
                    .eventType("COLLECT")
                    .ownerAddress(owner)
                    .recipient(recipient)
                    .tickLower(tickLower)
                    .tickUpper(tickUpper)
                    .amount0(amount0)
                    .amount1(amount1)
                    .summary("Collect [" + tickLower + ", " + tickUpper + "]")
                    .build());
        }

        if (TOPIC_FLASH.equalsIgnoreCase(topic0) && words.size() >= 4 && log.getTopics().size() >= 3) {
            String sender = topicToAddress(log.getTopics().get(1));
            String recipient = topicToAddress(log.getTopics().get(2));
            return Optional.of(builder
                    .eventType("FLASH")
                    .sender(sender)
                    .recipient(recipient)
                    .amount0(parseUnsigned(words.get(0)).toString())
                    .amount1(parseUnsigned(words.get(1)).toString())
                    .paid0(parseUnsigned(words.get(2)).toString())
                    .paid1(parseUnsigned(words.get(3)).toString())
                    .summary("Flash amount0=" + parseUnsigned(words.get(0)) + ", amount1=" + parseUnsigned(words.get(1)))
                    .build());
        }

        if (TOPIC_INITIALIZE.equalsIgnoreCase(topic0) && words.size() >= 2) {
            return Optional.of(builder
                    .eventType("INITIALIZE")
                    .sqrtPriceX96(parseUnsigned(words.get(0)).toString())
                    .tick(parseSigned(words.get(1), 24).intValue())
                    .summary("Initialize tick=" + parseSigned(words.get(1), 24).intValue())
                    .build());
        }

        return Optional.empty();
    }

    private UniV3PoolEvent toEntity(DecodedEvent event) {
        UniV3PoolEvent entity = new UniV3PoolEvent();
        entity.setEventUid(event.eventUid);
        entity.setChainId(event.chainId);
        entity.setPoolAddress(event.poolAddress);
        entity.setPoolName(event.poolName);
        entity.setEventType(event.eventType);
        entity.setBlockNumber(event.blockNumber);
        entity.setBlockHash(event.blockHash);
        entity.setTransactionHash(event.transactionHash);
        entity.setTransactionIndex(event.transactionIndex);
        entity.setLogIndex(event.logIndex);
        entity.setBlockTime(event.blockTime);
        entity.setSender(event.sender);
        entity.setRecipient(event.recipient);
        entity.setOwnerAddress(event.ownerAddress);
        entity.setTickLower(event.tickLower);
        entity.setTickUpper(event.tickUpper);
        entity.setAmount(event.amount);
        entity.setAmount0(event.amount0);
        entity.setAmount1(event.amount1);
        entity.setSqrtPriceX96(event.sqrtPriceX96);
        entity.setLiquidity(event.liquidity);
        entity.setTick(event.tick);
        entity.setPaid0(event.paid0);
        entity.setPaid1(event.paid1);
        entity.setSummary(event.summary);
        return entity;
    }

    private String resolveBlockHash(long blockNumber, List<DecodedEvent> blockEvents, String fallbackBlockHash) {
        if (!blockEvents.isEmpty() && blockEvents.getFirst().blockHash != null) {
            return blockEvents.getFirst().blockHash;
        }
        return fallbackBlockHash;
    }

    private String getBlockHash(long blockNumber) {
        try {
            EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)), false)
                    .send()
                    .getBlock();
            return block == null ? null : block.getHash();
        } catch (Exception e) {
            log.warn("Failed to fetch block hash for {}, skip hash check this round", blockNumber, e);
            return null;
        }
    }

    private long resolveBlockTime(long blockNumber) {
        return blockTimeCache.computeIfAbsent(blockNumber, key -> {
            try {
                EthBlock.Block block = web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(key)), false)
                        .send()
                        .getBlock();
                long blockTime = block == null ? lastKnownBlockTime : block.getTimestamp().longValue() * 1000L;
                lastKnownBlockTime = blockTime;
                return blockTime;
            } catch (Exception e) {
                log.warn("Failed to fetch block time for {}, reuse last known block time", key, e);
                return lastKnownBlockTime;
            }
        });
    }

    private static List<String> splitWords(String data) {
        String clean = Numeric.cleanHexPrefix(data);
        List<String> words = new ArrayList<>();
        for (int i = 0; i + 64 <= clean.length(); i += 64) {
            words.add(clean.substring(i, i + 64));
        }
        return words;
    }

    private static BigInteger parseUnsigned(String word) {
        return new BigInteger(word, 16);
    }

    private static BigInteger parseSigned(String word, int bitSize) {
        BigInteger value = new BigInteger(word, 16);
        if (value.testBit(bitSize - 1)) {
            value = value.subtract(BigInteger.ONE.shiftLeft(bitSize));
        }
        return value;
    }

    private static BigInteger parseTopicSigned(String topic, int bitSize) {
        return parseSigned(Numeric.cleanHexPrefix(topic), bitSize);
    }

    private static String topicToAddress(String topic) {
        String clean = Numeric.cleanHexPrefix(topic);
        return "0x" + clean.substring(clean.length() - 40).toLowerCase();
    }

    private static String wordToAddress(String word) {
        return "0x" + word.substring(word.length() - 40).toLowerCase();
    }

    private static String buildSwapSummary(BigInteger amount0, BigInteger amount1, int tick) {
        String tokenIn;
        String tokenOut;
        BigDecimal amountIn;
        BigDecimal amountOut;
        if (amount0.signum() < 0 && amount1.signum() > 0) {
            tokenIn = "WETH";
            tokenOut = "USDC";
            amountIn = decimal(amount0.abs(), WETH_DECIMALS);
            amountOut = decimal(amount1, USDC_DECIMALS);
        } else if (amount1.signum() < 0 && amount0.signum() > 0) {
            tokenIn = "USDC";
            tokenOut = "WETH";
            amountIn = decimal(amount1.abs(), USDC_DECIMALS);
            amountOut = decimal(amount0, WETH_DECIMALS);
        } else {
            tokenIn = amount0.signum() < 0 ? "WETH" : "USDC";
            tokenOut = amount0.signum() < 0 ? "USDC" : "WETH";
            amountIn = decimal(amount0.abs(), tokenIn.equals("WETH") ? WETH_DECIMALS : USDC_DECIMALS);
            amountOut = decimal(amount1.abs(), tokenOut.equals("WETH") ? WETH_DECIMALS : USDC_DECIMALS);
        }
        return tokenIn + " → " + tokenOut + " | in=" + amountIn.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                + " | out=" + amountOut.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                + " | tick=" + tick;
    }

    private static BigDecimal decimal(BigInteger raw, int decimals) {
        return new BigDecimal(raw).divide(BigDecimal.TEN.pow(decimals), 18, RoundingMode.HALF_UP);
    }

    @Data
    @Builder
    private static class DecodedEvent {
        private String eventUid;
        private Long chainId;
        private String poolAddress;
        private String poolName;
        private String eventType;
        private long blockNumber;
        private String blockHash;
        private String transactionHash;
        private int transactionIndex;
        private int logIndex;
        private long blockTime;
        private String sender;
        private String recipient;
        private String ownerAddress;
        private Integer tickLower;
        private Integer tickUpper;
        private String amount;
        private String amount0;
        private String amount1;
        private String sqrtPriceX96;
        private String liquidity;
        private Integer tick;
        private String paid0;
        private String paid1;
        private String summary;
    }
}
