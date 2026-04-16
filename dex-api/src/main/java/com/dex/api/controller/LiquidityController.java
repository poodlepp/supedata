package com.dex.api.controller;

import com.dex.business.service.LiquidityService;
import com.dex.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 流动性API控制器
 */
@RestController
@RequestMapping("/api/v1/liquidity")
@RequiredArgsConstructor
public class LiquidityController {
    private final LiquidityService liquidityService;

    @GetMapping("/{pool}")
    public ApiResponse<?> getLiquidity(@PathVariable("pool") String pool) {
        return liquidityService.getPoolByAddress(pool)
                .map(p -> ApiResponse.success(p))
                .orElse(ApiResponse.success(null));
    }

    @GetMapping("/pools")
    public ApiResponse<?> getAllPools() {
        return ApiResponse.success(liquidityService.getAllPools());
    }

    @GetMapping("/pair/{token0}/{token1}")
    public ApiResponse<?> getPoolsByPair(@PathVariable("token0") String token0, @PathVariable("token1") String token1) {
        return ApiResponse.success(liquidityService.getPoolsByTokenPair(token0, token1));
    }
}
