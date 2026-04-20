package com.dex.business.service;

import com.dex.business.service.blockchain.UniV3PoolService;
import com.dex.data.entity.Price;
import com.dex.infrastructure.blockchain.univ3.UniV3PoolSummary;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 阶段 5 运维服务：聚合实时链路、延迟、告警、回放与指标信息。
 */
@Service
@RequiredArgsConstructor
public class OpsService {

    private static final long DATA_DELAY_WARN_MS = 60_000L;
    private static final long DATA_DELAY_CRITICAL_MS = 180_000L;
    private static final long SYNC_LAG_WARN_BLOCKS = 20L;
    private static final long SYNC_LAG_CRITICAL_BLOCKS = 80L;
    private static final int MAX_REPLAY_JOBS = 10;

    private final PriceService priceService;
    private final UniV3PoolService uniV3PoolService;
    private final PrometheusMetrics prometheusMetrics;

    private final ConcurrentLinkedDeque<Map<String, Object>> replayJobs = new ConcurrentLinkedDeque<>();

    /**
     * 供监控页拉取的运维总览：把实时推送链路、批处理链路、告警与最近回放统一放在一处。
     */
    public Map<String, Object> getOverview() {
        UniV3PoolSummary summary = uniV3PoolService.getSummary();
        List<Map<String, Object>> latestPrices = latestPrices();
        List<Map<String, Object>> alerts = alerts(summary);
        prometheusMetrics.recordOpsAlerts(alerts.size(), criticalCount(alerts));
        Map<String, Object> metrics = prometheusMetrics.snapshot();
        long generatedAt = System.currentTimeMillis();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stream", streamStatus(metrics, generatedAt));
        payload.put("processing", processingStatus(summary, generatedAt));
        payload.put("prices", latestPrices);
        payload.put("alerts", alerts);
        payload.put("metrics", metrics);
        payload.put("replayJobs", List.copyOf(replayJobs));
        payload.put("architecture", Map.of(
                "pushPath", "/api/v1/ops/stream/prices",
                "batchPath", "/api/v1/ops/overview",
                "manualReplayPath", "/api/v1/ops/replay",
                "note", "推送链路使用 SSE，回放和总览查询走独立 HTTP 接口，避免互相阻塞"
        ));
        payload.put("generatedAt", generatedAt);
        return payload;
    }

    /**
     * SSE 实时推送载荷：只保留最重要的价格、延迟和告警信息，避免事件体过大。
     */
    public Map<String, Object> buildRealtimeSnapshot() {
        UniV3PoolSummary summary = uniV3PoolService.getSummary();
        List<Map<String, Object>> alerts = alerts(summary);
        prometheusMetrics.recordOpsAlerts(alerts.size(), criticalCount(alerts));
        long generatedAt = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "OPS_SNAPSHOT");
        payload.put("prices", latestPrices());
        payload.put("processing", processingStatus(summary, generatedAt));
        payload.put("alerts", alerts);
        payload.put("generatedAt", generatedAt);
        return payload;
    }

    /**
     * 手动触发回放：当前实现从指定块回滚并重扫到当前 safe latest，优先保证可解释和稳定。
     */
    public Map<String, Object> triggerReplay(long fromBlock, Long toBlock, String reason) {
        long startedAt = System.nanoTime();
        if (fromBlock <= 0) {
            throw new IllegalArgumentException("fromBlock must be positive");
        }
        if (toBlock != null && toBlock < fromBlock) {
            throw new IllegalArgumentException("toBlock must be greater than or equal to fromBlock");
        }

        Map<String, Object> job = new LinkedHashMap<>();
        job.put("jobId", UUID.randomUUID().toString());
        job.put("status", "RUNNING");
        job.put("requestedFromBlock", fromBlock);
        job.put("requestedToBlock", toBlock);
        job.put("reason", reason == null || reason.isBlank() ? "manual replay" : reason.trim());
        job.put("requestedAt", System.currentTimeMillis());
        pushReplayJob(job);

        try {
            Map<String, Object> result = uniV3PoolService.replayFromBlock(fromBlock, toBlock, reason);
            job.put("status", "SUCCESS");
            job.put("completedAt", System.currentTimeMillis());
            job.put("result", result);
            prometheusMetrics.recordReplayExecution("success", replayWindow(fromBlock, toBlock), durationMs(startedAt));
            return job;
        } catch (RuntimeException ex) {
            job.put("status", "FAILED");
            job.put("completedAt", System.currentTimeMillis());
            job.put("error", ex.getMessage());
            prometheusMetrics.recordReplayExecution("failed", replayWindow(fromBlock, toBlock), durationMs(startedAt));
            throw ex;
        }
    }

    private Map<String, Object> streamStatus(Map<String, Object> metrics, long generatedAt) {
        Map<String, Object> stream = new LinkedHashMap<>();
        long subscribers = ((Number) metrics.getOrDefault("sseSubscribers", 0L)).longValue();
        stream.put("transport", "SSE");
        stream.put("status", subscribers > 0 ? "LIVE" : "IDLE");
        stream.put("subscribers", subscribers);
        stream.put("endpoint", "/api/v1/ops/stream/prices");
        stream.put("lastSnapshotAt", generatedAt);
        return stream;
    }

    private Map<String, Object> processingStatus(UniV3PoolSummary summary, long generatedAt) {
        Long latestEventTime = summary.getLatestEventTime();
        long latestEventLagMs = latestEventTime == null ? -1L : Math.max(0L, generatedAt - latestEventTime);
        Map<String, Object> processing = new LinkedHashMap<>();
        processing.put("status", summary.getStatus());
        processing.put("latestBlock", summary.getLatestBlock());
        processing.put("safeLatestBlock", summary.getSafeLatestBlock());
        processing.put("latestCommittedBlock", summary.getLatestCommittedBlock());
        processing.put("syncLagBlocks", summary.getSyncLag());
        processing.put("latestEventTime", latestEventTime);
        processing.put("latestEventLagMs", latestEventLagMs < 0 ? null : latestEventLagMs);
        processing.put("latestEventLagLabel", latestEventLagMs < 0 ? "unknown" : lagLabel(latestEventLagMs));
        processing.put("totalEvents", summary.getTotalEvents());
        processing.put("eventCounts", summary.getEventCounts());
        processing.put("errorMessage", summary.getErrorMessage());
        processing.put("poolName", summary.getPoolName());
        processing.put("poolAddress", summary.getPoolAddress());
        return processing;
    }

    private List<Map<String, Object>> latestPrices() {
        return priceService.getSupportedPairs().stream()
                .map(pair -> priceService.getLatestPrice(pair)
                        .map(price -> toPriceItem(pair, price))
                        .orElseGet(() -> unavailablePrice(pair)))
                .sorted(Comparator.comparing(item -> item.get("pair").toString()))
                .toList();
    }

    private Map<String, Object> toPriceItem(String pair, Price price) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pair", pair);
        item.put("price", price.getPrice().setScale(4, RoundingMode.HALF_UP));
        item.put("timestamp", price.getTimestamp());
        item.put("freshnessMs", Math.max(0L, System.currentTimeMillis() - price.getTimestamp()));
        item.put("source", "ethereum-mainnet-uniswap-v3");
        return item;
    }

    private Map<String, Object> unavailablePrice(String pair) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pair", pair);
        item.put("price", null);
        item.put("timestamp", null);
        item.put("freshnessMs", null);
        item.put("source", "unavailable");
        return item;
    }

    private List<Map<String, Object>> alerts(UniV3PoolSummary summary) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        long now = System.currentTimeMillis();

        if (!"RUNNING".equalsIgnoreCase(summary.getStatus())) {
            alerts.add(alert("CRITICAL", "INDEXER_STATUS", "索引器状态异常", summary.getStatus(), now));
        }

        long syncLag = summary.getSyncLag() == null ? 0L : summary.getSyncLag();
        if (syncLag >= SYNC_LAG_CRITICAL_BLOCKS) {
            alerts.add(alert("CRITICAL", "SYNC_LAG", "索引延迟过大", syncLag + " blocks", now));
        } else if (syncLag >= SYNC_LAG_WARN_BLOCKS) {
            alerts.add(alert("WARN", "SYNC_LAG", "索引延迟偏高", syncLag + " blocks", now));
        }

        Long latestEventTime = summary.getLatestEventTime();
        if (latestEventTime == null) {
            alerts.add(alert("WARN", "DATA_DELAY", "尚未收到事件时间戳", "latestEventTime missing", now));
        } else {
            long ageMs = Math.max(0L, now - latestEventTime);
            if (ageMs >= DATA_DELAY_CRITICAL_MS) {
                alerts.add(alert("CRITICAL", "DATA_DELAY", "链上事件数据明显滞后", lagLabel(ageMs), now));
            } else if (ageMs >= DATA_DELAY_WARN_MS) {
                alerts.add(alert("WARN", "DATA_DELAY", "链上事件数据延迟偏高", lagLabel(ageMs), now));
            }
        }

        for (String pair : priceService.getSupportedPairs()) {
            Optional<Map<String, Object>> latestAnomaly = priceService.detectAnomalies(pair, 1.0).stream().reduce((left, right) -> right);
            latestAnomaly.ifPresent(anomaly -> alerts.add(alert(
                    "WARN",
                    "PRICE_JUMP",
                    pair + " 出现价格瞬时跳变",
                    anomaly.get("changePct") + "%",
                    ((Number) anomaly.getOrDefault("timestamp", now)).longValue()
            )));
        }

        return alerts.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> item) -> severityRank(item.get("severity").toString()))
                        .thenComparing(item -> ((Number) item.get("timestamp")).longValue(), Comparator.reverseOrder()))
                .toList();
    }

    private Map<String, Object> alert(String severity, String code, String title, String detail, long timestamp) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("severity", severity);
        alert.put("code", code);
        alert.put("title", title);
        alert.put("detail", detail);
        alert.put("timestamp", timestamp);
        return alert;
    }

    private int severityRank(String severity) {
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 0;
            case "WARN" -> 1;
            default -> 2;
        };
    }

    private String lagLabel(long lagMs) {
        if (lagMs < 1_000) {
            return lagMs + " ms";
        }
        if (lagMs < 60_000) {
            return (lagMs / 1_000) + " s";
        }
        return (lagMs / 60_000) + " min";
    }

    private void pushReplayJob(Map<String, Object> job) {
        replayJobs.removeIf(existing -> existing.get("jobId").equals(job.get("jobId")));
        replayJobs.addFirst(job);
        while (replayJobs.size() > MAX_REPLAY_JOBS) {
            replayJobs.removeLast();
        }
    }

    private int criticalCount(List<Map<String, Object>> alerts) {
        return (int) alerts.stream()
                .filter(item -> "CRITICAL".equals(item.get("severity")))
                .count();
    }

    private long replayWindow(long fromBlock, Long toBlock) {
        if (toBlock == null || toBlock < fromBlock) {
            return 1L;
        }
        return Math.max(1L, toBlock - fromBlock + 1);
    }

    private long durationMs(long startedAt) {
        return java.time.Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
