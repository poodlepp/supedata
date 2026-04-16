package com.dex.scheduler.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 区块扫描定时任务
 */
@Component
public class BlockScanTask {

    @Scheduled(fixedDelay = 12000)
    public void scanBlocks() {
        // TODO: 实现区块扫描任务（每12秒）
    }
}
