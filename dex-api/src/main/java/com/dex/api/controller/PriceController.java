package com.dex.api.controller;

import com.dex.business.service.PriceService;
import com.dex.common.model.ApiResponse;
import com.dex.data.entity.Price;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 价格API控制器
 */
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {
    private final PriceService priceService;

    @GetMapping("/{pair}")
    public ApiResponse<?> getPrice(@PathVariable("pair") String pair) {
        return priceService.getLatestPrice(pair)
                .map(price -> ApiResponse.success(price))
                .orElse(ApiResponse.success(null));
    }

    @GetMapping("/{pair}/history")
    public ApiResponse<?> getPriceHistory(@PathVariable("pair") String pair) {
        return ApiResponse.success(priceService.getPriceHistory(pair));
    }
}
