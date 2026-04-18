package com.dex.infrastructure.scheduler.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 价格缓存更新定时任务
 */
@Component
public class PriceCacheTask {

    @Scheduled(fixedDelay = 30000)
    public void updatePriceCache() {
        // TODO: 实现价格缓存更新任务（每30秒）
    }
}
