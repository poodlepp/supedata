package com.dex.scheduler.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 统计数据计算定时任务
 */
@Component
public class StatisticsTask {

    @Scheduled(fixedDelay = 60000)
    public void calculateStatistics() {
        // TODO: 实现统计数据计算任务（每分钟）
    }
}
