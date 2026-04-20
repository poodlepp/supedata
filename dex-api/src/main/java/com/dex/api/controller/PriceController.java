package com.dex.api.controller;

import com.dex.business.service.PriceService;
import com.dex.common.model.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 价格 API：返回当前支持范围内的真实价格、历史快照、K 线和异常检测。
 */
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
public class PriceController {
    private final PriceService priceService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAllPrices() {
        return ApiResponse.success(priceService.getSupportedPairs().stream()
                .map(pair -> priceService.getLatestPrice(pair)
                        .<Map<String, Object>>map(price -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("pair", price.getPair());
                            item.put("price", price.getPrice());
                            item.put("timestamp", price.getTimestamp());
                            item.put("createdAt", price.getCreatedAt());
                            item.put("source", "ethereum-mainnet-uniswap-v3");
                            item.put("isReal", true);
                            return item;
                        })
                        .orElseGet(() -> {
                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("pair", pair);
                            item.put("price", null);
                            item.put("timestamp", null);
                            item.put("createdAt", null);
                            item.put("source", "unavailable");
                            item.put("isReal", false);
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

    @GetMapping("/{pair}/candles")
    public ApiResponse<?> getCandles(@PathVariable("pair") String pair,
                                     @RequestParam(value = "minutes", defaultValue = "5") int minutes) {
        return ApiResponse.success(priceService.getCandles(pair, minutes));
    }

    @GetMapping("/{pair}/anomalies")
    public ApiResponse<?> getAnomalies(@PathVariable("pair") String pair,
                                       @RequestParam(value = "threshold", defaultValue = "1.0") double threshold) {
        return ApiResponse.success(priceService.detectAnomalies(pair, threshold));
    }
}
