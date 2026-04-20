package com.dex.infrastructure.monitor.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus指标
 */
@Component
public class PrometheusMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicLong latestBlockHeight = new AtomicLong(0);
    private final AtomicLong latestCommittedBlock = new AtomicLong(0);
    private final AtomicLong syncLagBlocks = new AtomicLong(0);
    private final AtomicLong latestEventTimestampMs = new AtomicLong(0);
    private final AtomicLong latestEventLagMs = new AtomicLong(0);
    private final AtomicLong lastReplayFromBlock = new AtomicLong(0);
    private final AtomicLong lastReplayRequestedAt = new AtomicLong(0);
    private final AtomicLong sseSubscribers = new AtomicLong(0);
    private final AtomicLong activeAlerts = new AtomicLong(0);
    private final AtomicLong criticalAlerts = new AtomicLong(0);
    private final Map<String, AtomicLong> priceFreshnessByPair = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> routeFreshnessByPair = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> anomalyCountByPair = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter kafkaPublishedCounter;
    private final Counter kafkaConsumedCounter;
    private final Counter kafkaFailureCounter;
    private final Counter replayCounter;
    private final Timer rpcLatency;

    public PrometheusMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("dex_block_scan_height", latestBlockHeight, AtomicLong::doubleValue)
                .description("Latest scanned block height")
                .register(meterRegistry);
        Gauge.builder("dex_committed_block_height", latestCommittedBlock, AtomicLong::doubleValue)
                .description("Latest committed block height")
                .register(meterRegistry);
        Gauge.builder("dex_sync_lag_blocks", syncLagBlocks, AtomicLong::doubleValue)
                .description("Indexer sync lag in blocks")
                .register(meterRegistry);
        Gauge.builder("dex_latest_event_timestamp_ms", latestEventTimestampMs, AtomicLong::doubleValue)
                .description("Latest indexed event timestamp in milliseconds")
                .register(meterRegistry);
        Gauge.builder("dex_latest_event_lag_ms", latestEventLagMs, AtomicLong::doubleValue)
                .description("Age of latest indexed event in milliseconds")
                .register(meterRegistry);
        Gauge.builder("dex_last_replay_from_block", lastReplayFromBlock, AtomicLong::doubleValue)
                .description("Last replay requested from block")
                .register(meterRegistry);
        Gauge.builder("dex_last_replay_requested_at_ms", lastReplayRequestedAt, AtomicLong::doubleValue)
                .description("Last replay request timestamp")
                .register(meterRegistry);
        Gauge.builder("dex_sse_subscribers", sseSubscribers, AtomicLong::doubleValue)
                .description("Current SSE subscriber count")
                .register(meterRegistry);
        Gauge.builder("dex_ops_active_alerts", activeAlerts, AtomicLong::doubleValue)
                .description("Current active alerts")
                .register(meterRegistry);
        Gauge.builder("dex_ops_critical_alerts", criticalAlerts, AtomicLong::doubleValue)
                .description("Current critical alerts")
                .register(meterRegistry);
        this.processedCounter = Counter.builder("dex_events_processed_total")
                .description("Total processed events")
                .register(meterRegistry);
        this.kafkaPublishedCounter = Counter.builder("dex_kafka_published_total")
                .description("Total kafka messages published")
                .register(meterRegistry);
        this.kafkaConsumedCounter = Counter.builder("dex_kafka_consumed_total")
                .description("Total kafka messages consumed")
                .register(meterRegistry);
        this.kafkaFailureCounter = Counter.builder("dex_kafka_failures_total")
                .description("Total kafka publish/consume failures")
                .register(meterRegistry);
        this.replayCounter = Counter.builder("dex_replay_total")
                .description("Total manual replay operations")
                .register(meterRegistry);
        this.rpcLatency = Timer.builder("dex_rpc_latency_ms")
                .description("RPC latency in milliseconds")
                .register(meterRegistry);
    }

    public void recordBlockScanHeight(long height) {
        latestBlockHeight.set(height);
    }

    public void recordCommittedBlock(long height) {
        latestCommittedBlock.set(height);
    }

    public void recordSyncLagBlocks(long lagBlocks) {
        syncLagBlocks.set(Math.max(0L, lagBlocks));
    }

    public void recordLatestEventTimestamp(Long timestampMs) {
        latestEventTimestampMs.set(timestampMs == null ? 0L : timestampMs);
    }

    public void recordLatestEventLagMs(long lagMs) {
        latestEventLagMs.set(Math.max(0L, lagMs));
    }

    public void recordRpcLatency(long latency) {
        rpcLatency.record(latency, TimeUnit.MILLISECONDS);
    }

    public void recordEventProcessed() {
        processedCounter.increment();
    }

    public void recordEventsProcessed(int count) {
        if (count > 0) {
            processedCounter.increment(count);
        }
    }

    public void recordKafkaPublished() {
        kafkaPublishedCounter.increment();
    }

    public void recordKafkaConsumed() {
        kafkaConsumedCounter.increment();
    }

    public void recordKafkaFailure() {
        kafkaFailureCounter.increment();
    }

    public void recordReplay(long fromBlock) {
        replayCounter.increment();
        lastReplayFromBlock.set(Math.max(0L, fromBlock));
        lastReplayRequestedAt.set(System.currentTimeMillis());
    }

    public void recordSseSubscribers(int count) {
        sseSubscribers.set(Math.max(0, count));
    }

    public void recordPriceFreshness(String pair, long freshnessMs) {
        taggedGauge(priceFreshnessByPair, "dex_price_snapshot_freshness_ms", "Latest price snapshot freshness in milliseconds", "pair", pair)
                .set(Math.max(0L, freshnessMs));
    }

    public void recordRouteFreshness(String pair, long freshnessMs) {
        taggedGauge(routeFreshnessByPair, "dex_route_quote_freshness_ms", "Latest selected route freshness in milliseconds", "pair", pair)
                .set(Math.max(0L, freshnessMs));
    }

    public void recordPriceAnomalyCount(String pair, int count) {
        taggedGauge(anomalyCountByPair, "dex_price_anomaly_count", "Current detected anomaly count per pair", "pair", pair)
                .set(Math.max(0, count));
    }

    public void recordOpsAlerts(int total, int critical) {
        activeAlerts.set(Math.max(0, total));
        criticalAlerts.set(Math.max(0, critical));
    }

    public void recordRouteRequest(String operation,
                                   String pair,
                                   String result,
                                   String executionType,
                                   int hopCount,
                                   int candidateCount,
                                   long freshnessMs,
                                   BigDecimal priceImpactPct,
                                   BigDecimal netScore,
                                   long durationMs) {
        Tags baseTags = Tags.of(
                "operation", sanitize(operation),
                "pair", sanitize(pair),
                "result", sanitize(result)
        );
        Counter.builder("dex_route_requests_total")
                .description("Total route quote and compare requests")
                .tags(baseTags)
                .register(meterRegistry)
                .increment();
        Timer.builder("dex_route_request_latency_seconds")
                .description("Route quote and compare request latency")
                .tags(baseTags)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(0L, durationMs), TimeUnit.MILLISECONDS);
        DistributionSummary.builder("dex_route_candidate_count")
                .description("Candidate route count before final ranking")
                .tags("operation", sanitize(operation), "pair", sanitize(pair))
                .register(meterRegistry)
                .record(Math.max(0, candidateCount));
        if (executionType != null && !executionType.isBlank()) {
            Counter.builder("dex_route_best_execution_total")
                    .description("Selected best execution type")
                    .tags(
                            "operation", sanitize(operation),
                            "pair", sanitize(pair),
                            "execution_type", sanitize(executionType),
                            "hop_count", String.valueOf(Math.max(0, hopCount))
                    )
                    .register(meterRegistry)
                    .increment();
        }
        if (priceImpactPct != null) {
            DistributionSummary.builder("dex_route_price_impact_pct")
                    .description("Selected route price impact percentage")
                    .tags("operation", sanitize(operation), "pair", sanitize(pair))
                    .register(meterRegistry)
                    .record(Math.max(0d, priceImpactPct.doubleValue()));
        }
        if (netScore != null) {
            // DistributionSummary 只适合非负分布值；无路由或被淘汰场景统一落为 0。
            DistributionSummary.builder("dex_route_net_score")
                    .description("Selected route net score in output token unit")
                    .tags("operation", sanitize(operation), "pair", sanitize(pair))
                    .register(meterRegistry)
                    .record(Math.max(0d, netScore.doubleValue()));
        }
        recordRouteFreshness(pair, freshnessMs);
    }

    public void recordReplayExecution(String result, long windowBlocks, long durationMs) {
        Counter.builder("dex_replay_jobs_total")
                .description("Total replay jobs by result")
                .tags("result", sanitize(result))
                .register(meterRegistry)
                .increment();
        Timer.builder("dex_replay_duration_seconds")
                .description("Replay job duration")
                .tags("result", sanitize(result))
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(0L, durationMs), TimeUnit.MILLISECONDS);
        DistributionSummary.builder("dex_replay_window_blocks")
                .description("Replay job requested window in blocks")
                .register(meterRegistry)
                .record(Math.max(0L, windowBlocks));
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("latestBlockHeight", latestBlockHeight.get());
        snapshot.put("latestCommittedBlock", latestCommittedBlock.get());
        snapshot.put("syncLagBlocks", syncLagBlocks.get());
        snapshot.put("latestEventTimestampMs", latestEventTimestampMs.get());
        snapshot.put("latestEventLagMs", latestEventLagMs.get());
        snapshot.put("sseSubscribers", sseSubscribers.get());
        snapshot.put("activeAlerts", activeAlerts.get());
        snapshot.put("criticalAlerts", criticalAlerts.get());
        snapshot.put("lastReplayFromBlock", lastReplayFromBlock.get());
        snapshot.put("lastReplayRequestedAt", lastReplayRequestedAt.get());
        snapshot.put("eventsProcessedTotal", Math.round(processedCounter.count()));
        snapshot.put("kafkaPublishedTotal", Math.round(kafkaPublishedCounter.count()));
        snapshot.put("kafkaConsumedTotal", Math.round(kafkaConsumedCounter.count()));
        snapshot.put("kafkaFailuresTotal", Math.round(kafkaFailureCounter.count()));
        snapshot.put("replayTotal", Math.round(replayCounter.count()));
        return snapshot;
    }

    private AtomicLong taggedGauge(Map<String, AtomicLong> store,
                                   String metricName,
                                   String description,
                                   String tagName,
                                   String tagValue) {
        String key = metricName + ":" + tagName + ":" + sanitize(tagValue);
        return store.computeIfAbsent(key, ignored -> {
            AtomicLong gaugeValue = new AtomicLong(0L);
            Gauge.builder(metricName, gaugeValue, AtomicLong::doubleValue)
                    .description(description)
                    .tag(tagName, sanitize(tagValue))
                    .register(meterRegistry);
            return gaugeValue;
        });
    }

    private String sanitize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
