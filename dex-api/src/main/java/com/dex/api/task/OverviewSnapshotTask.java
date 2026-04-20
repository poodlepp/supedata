package com.dex.api.task;

import com.dex.business.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 首页总览快照预热任务：把重计算从查询链路挪到后台定时刷新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverviewSnapshotTask {

    private final StatisticsService statisticsService;

    @Scheduled(initialDelay = 5000, fixedDelay = 60000)
    public void refreshOverviewSnapshot() {
        try {
            statisticsService.refreshOverview();
        } catch (Exception e) {
            log.warn("Failed to refresh dashboard overview snapshot", e);
        }
    }
}
