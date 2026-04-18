package com.dex.api.controller;

import com.dex.business.service.PriceService;
import com.dex.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 价格API控制器
 */
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {
    private final PriceService priceService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAllPrices() {
        List<String> pairs = List.of("ETH-USDC", "BTC-USDC", "DAI-USDC");
        return ApiResponse.success(pairs.stream()
                .map(pair -> priceService.getLatestPrice(pair)
                        .<Map<String, Object>>map(price -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("pair", price.getPair());
                            item.put("price", price.getPrice());
                            item.put("timestamp", price.getTimestamp());
                            item.put("createdAt", price.getCreatedAt());
                            return item;
                        })
                        .orElseGet(() -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("pair", pair);
                            item.put("price", null);
                            item.put("timestamp", null);
                            item.put("createdAt", null);
                            return item;
                        }))
                .toList());
    }

    @GetMapping("/{pair}")
    public ApiResponse<?> getPrice(@PathVariable("pair") String pair) {
        return priceService.getLatestPrice(pair)
                .map(ApiResponse::success)
                .orElse(ApiResponse.success(null));
    }

    @GetMapping("/{pair}/history")
    public ApiResponse<?> getPriceHistory(@PathVariable("pair") String pair) {
        return ApiResponse.success(priceService.getPriceHistory(pair));
    }
}
