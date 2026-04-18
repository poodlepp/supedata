package com.dex.api.controller;

import com.dex.business.service.RouteService;
import com.dex.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 路由API控制器
 */
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping("/best")
    public ApiResponse<?> getBestRoute(@RequestParam("from") String from,
                                       @RequestParam("to") String to,
                                       @RequestParam(value = "amountIn", required = false) BigDecimal amountIn) {
        return ApiResponse.success(routeService.quote(from, to, amountIn));
    }

    @GetMapping("/quote")
    public ApiResponse<?> quote(@RequestParam("from") String from,
                                @RequestParam("to") String to,
                                @RequestParam(value = "amountIn", required = false) BigDecimal amountIn) {
        return ApiResponse.success(routeService.quote(from, to, amountIn));
    }
}
