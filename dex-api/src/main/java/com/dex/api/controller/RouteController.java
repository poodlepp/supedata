package com.dex.api.controller;

import com.dex.common.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 路由API控制器
 */
@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    @GetMapping("/best")
    public ApiResponse<?> getBestRoute(@RequestParam("from") String from, @RequestParam("to") String to) {
        // TODO: 实现获取最优路由
        return ApiResponse.success(null);
    }
}
