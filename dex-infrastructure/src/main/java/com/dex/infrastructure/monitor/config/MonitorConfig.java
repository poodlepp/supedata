package com.dex.monitor.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * 监控配置
 */
@Configuration
public class MonitorConfig {

    public MonitorConfig(MeterRegistry meterRegistry) {
        // TODO: 注册自定义指标
    }
}
