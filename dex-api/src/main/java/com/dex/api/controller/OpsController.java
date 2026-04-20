package com.dex.api.controller;

import com.dex.api.service.OpsStreamService;
import com.dex.business.service.OpsService;
import com.dex.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 阶段 5 运维接口：总览、SSE 推送、手动回放。
 */
@RestController
@RequestMapping("/api/v1/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsService opsService;
    private final OpsStreamService opsStreamService;

    @GetMapping("/overview")
    public ApiResponse<?> overview() {
        return ApiResponse.success(opsService.getOverview());
    }

    @GetMapping(path = "/stream/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrices() {
        return opsStreamService.subscribe();
    }

    @PostMapping("/replay")
    public ApiResponse<?> replay(@RequestParam("fromBlock") long fromBlock,
                                 @RequestParam(value = "toBlock", required = false) Long toBlock,
                                 @RequestParam(value = "reason", required = false) String reason) {
        return ApiResponse.success(opsService.triggerReplay(fromBlock, toBlock, reason));
    }
}
