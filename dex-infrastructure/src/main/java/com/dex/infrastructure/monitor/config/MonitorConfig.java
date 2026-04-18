package com.dex.infrastructure.monitor.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控配置
 */
@Configuration
public class MonitorConfig {

    public MonitorConfig(MeterRegistry meterRegistry) {
        AtomicLong startupGauge = new AtomicLong(System.currentTimeMillis());
        Gauge.builder("dex.application.start_time", startupGauge, AtomicLong::doubleValue)
                .description("Application start time in epoch millis")
                .register(meterRegistry);
    }
}
