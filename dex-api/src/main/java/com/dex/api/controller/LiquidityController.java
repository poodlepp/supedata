package com.dex.api.controller;

import com.dex.common.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 流动性API控制器
 */
@RestController
@RequestMapping("/api/v1/liquidity")
public class LiquidityController {

    @GetMapping("/{pool}")
    public ApiResponse<?> getLiquidity(@PathVariable String pool) {
        // TODO: 实现获取流动性信息
        return ApiResponse.success(null);
    }
}
