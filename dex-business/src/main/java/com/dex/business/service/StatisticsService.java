package com.dex.business.service;

import com.dex.data.entity.LiquidityPool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final PriceService priceService;
    private final LiquidityService liquidityService;

    public Map<String, Object> getVolume(String pair, String period) {
        BigDecimal latestPrice = priceService.getLatestPrice(pair)
                .map(value -> value.getPrice())
                .orElse(BigDecimal.ZERO);

        BigDecimal multiplier = switch (period == null ? "24h" : period.toLowerCase()) {
            case "1h" -> new BigDecimal("12");
            case "7d" -> new BigDecimal("2200");
            default -> new BigDecimal("320");
        };

        BigDecimal estimatedVolume = latestPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pair", pair);
        payload.put("period", period == null || period.isBlank() ? "24h" : period);
        payload.put("estimatedVolumeUsd", estimatedVolume);
        payload.put("methodology", "demo-estimate-from-latest-price");
        payload.put("generatedAt", System.currentTimeMillis());
        return payload;
    }

    public Map<String, Object> getOverview() {
        List<LiquidityPool> pools = liquidityService.getAllPools();
        BigDecimal totalTvl = pools.stream()
                .map(pool -> safe(pool.getReserve0()).add(safe(pool.getReserve1())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("poolCount", pools.size());
        overview.put("totalTvlEstimate", totalTvl);
        overview.put("topPools", pools.stream()
                .map(pool -> Map.of(
                        "poolAddress", pool.getPoolAddress(),
                        "pair", pool.getToken0() + "/" + pool.getToken1(),
                        "reserveScore", safe(pool.getReserve0()).add(safe(pool.getReserve1())).setScale(2, RoundingMode.HALF_UP)
                ))
                .toList());
        overview.put("generatedAt", System.currentTimeMillis());
        return overview;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
