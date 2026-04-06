package com.dex.monitor.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Prometheus指标
 */
@Component
public class PrometheusMetrics {

    private final MeterRegistry meterRegistry;

    public PrometheusMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordBlockScanHeight(long height) {
        // TODO: 记录区块扫描高度
    }

    public void recordRpcLatency(long latency) {
        // TODO: 记录RPC延迟
    }

    public void recordEventProcessed() {
        // TODO: 记录处理事件数
    }
}
