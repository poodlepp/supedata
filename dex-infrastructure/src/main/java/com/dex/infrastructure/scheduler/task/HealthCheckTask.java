package com.dex.infrastructure.scheduler.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 健康检查定时任务
 */
@Component
public class HealthCheckTask {

    @Scheduled(fixedDelay = 5000)
    public void healthCheck() {
        // TODO: 实现健康检查任务（每5秒）
    }
}
