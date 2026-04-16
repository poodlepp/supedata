package com.dex.api.controller;

import com.dex.common.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 统计API控制器
 */
@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    @GetMapping("/volume")
    public ApiResponse<?> getVolume(@RequestParam("pair") String pair, @RequestParam(value = "period", required = false) String period) {
        // TODO: 实现获取交易量统计
        return ApiResponse.success(null);
    }
}
