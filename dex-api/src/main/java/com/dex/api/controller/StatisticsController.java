package com.dex.api.controller;

import com.dex.business.service.StatisticsService;
import com.dex.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计API控制器
 */
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/volume")
    public ApiResponse<?> getVolume(@RequestParam("pair") String pair,
                                    @RequestParam(value = "period", required = false) String period) {
        return ApiResponse.success(statisticsService.getVolume(pair, period));
    }

    @GetMapping("/overview")
    public ApiResponse<?> getOverview() {
        return ApiResponse.success(statisticsService.getOverview());
    }
}
