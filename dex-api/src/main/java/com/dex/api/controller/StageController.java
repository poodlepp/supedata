package com.dex.api.controller;

import com.dex.business.service.StageStatusService;
import com.dex.business.service.blockchain.UniV3PoolService;
import com.dex.common.model.ApiResponse;
import com.dex.infrastructure.blockchain.univ3.UniV3PoolSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阶段交付状态控制器：按当前真实落地情况返回状态。
 */
@RestController
@RequestMapping("/api/v1/stages")
@RequiredArgsConstructor
public class StageController {

    private final StageStatusService stageStatusService;
    private final UniV3PoolService uniV3PoolService;

    @GetMapping("/progress")
    public ApiResponse<?> progress() {
        return ApiResponse.success(stageStatusService.getProgress());
    }

    /**
     * 扫描进度端点：返回 UniV3 索引器的同步状态、延迟和检查点信息。
     * 对应 Stage 1 验收标准：扫描进度可查询、能输出当前同步延迟。
     */
    @GetMapping("/scan-progress")
    public ApiResponse<?> scanProgress() {
        UniV3PoolSummary summary = uniV3PoolService.getSummary();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", summary.getStatus());
        payload.put("latestBlock", summary.getLatestBlock());
        payload.put("safeLatestBlock", summary.getSafeLatestBlock());
        payload.put("latestCommittedBlock", summary.getLatestCommittedBlock());
        payload.put("startBlock", summary.getStartBlock());
        payload.put("syncLag", summary.getSyncLag());
        payload.put("totalEvents", summary.getTotalEvents());
        payload.put("eventCounts", summary.getEventCounts());
        payload.put("latestEventTime", summary.getLatestEventTime());
        payload.put("poolAddress", summary.getPoolAddress());
        payload.put("poolName", summary.getPoolName());
        payload.put("syncDelayBlocks", summary.getSyncLag());
        payload.put("errorMessage", summary.getErrorMessage());
        payload.put("generatedAt", System.currentTimeMillis());
        return ApiResponse.success(payload);
    }
}
