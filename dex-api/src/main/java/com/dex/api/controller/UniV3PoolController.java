package com.dex.api.controller;

import com.dex.business.service.blockchain.UniV3PoolService;
import com.dex.common.model.ApiResponse;
import com.dex.data.entity.UniV3PoolEvent;
import com.dex.infrastructure.blockchain.univ3.UniV3PoolSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/univ3")
@RequiredArgsConstructor
public class UniV3PoolController {

    private final UniV3PoolService uniV3PoolService;

    @GetMapping("/summary")
    public ApiResponse<UniV3PoolSummary> summary() {
        return ApiResponse.success(uniV3PoolService.getSummary());
    }

    @GetMapping("/events")
    public ApiResponse<List<UniV3PoolEvent>> events(@RequestParam(name = "eventType", required = false) String eventType,
                                                    @RequestParam(name = "limit", defaultValue = "20") Integer limit) {
        try {
            int safeLimit = Math.max(1, Math.min(limit, 200));
            return ApiResponse.success(uniV3PoolService.getRecentEvents(eventType, safeLimit));
        } catch (Exception e) {
            log.error("Failed to query UniV3 events", e);
            return ApiResponse.error("Failed to load UniV3 events: " + e.getMessage());
        }
    }
}
