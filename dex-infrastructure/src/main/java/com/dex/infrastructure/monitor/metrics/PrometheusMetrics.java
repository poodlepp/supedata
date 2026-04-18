package com.dex.infrastructure.monitor.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Prometheus指标
 */
@Component
public class PrometheusMetrics {

    private final AtomicLong latestBlockHeight = new AtomicLong(0);
    private final Counter processedCounter;
    private final Timer rpcLatency;

    public PrometheusMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("dex_block_scan_height", latestBlockHeight, AtomicLong::doubleValue)
                .description("Latest scanned block height")
                .register(meterRegistry);
        this.processedCounter = Counter.builder("dex_events_processed_total")
                .description("Total processed events")
                .register(meterRegistry);
        this.rpcLatency = Timer.builder("dex_rpc_latency_ms")
                .description("RPC latency in milliseconds")
                .register(meterRegistry);
    }

    public void recordBlockScanHeight(long height) {
        latestBlockHeight.set(height);
    }

    public void recordRpcLatency(long latency) {
        rpcLatency.record(latency, TimeUnit.MILLISECONDS);
    }

    public void recordEventProcessed() {
        processedCounter.increment();
    }
}
