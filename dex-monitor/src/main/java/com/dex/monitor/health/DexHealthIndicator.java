package com.dex.monitor.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 自定义健康检查
 */
@Component
public class DexHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        // TODO: 实现自定义健康检查逻辑
        return Health.up().build();
    }
}
