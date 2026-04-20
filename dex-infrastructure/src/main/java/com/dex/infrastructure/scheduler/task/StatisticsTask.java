package com.dex.infrastructure.scheduler.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计数据计算定时任务
 */
@Component
public class StatisticsTask {

    @Scheduled(fixedDelay = 60000)
    public void calculateStatistics() {
        // 具体的统计预热任务放在上层模块实现，避免基础设施模块反向依赖业务模块。
    }
}
