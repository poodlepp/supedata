package com.dex.api.controller;

import com.dex.common.model.ApiResponse;
import org.springframework.web.bind.annotation.*;

/**
 * 价格API控制器
 */
@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    @GetMapping("/{pair}")
    public ApiResponse<?> getPrice(@PathVariable String pair) {
        // TODO: 实现获取交易对价格
        return ApiResponse.success(null);
    }
}
