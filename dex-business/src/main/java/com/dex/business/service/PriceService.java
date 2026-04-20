package com.dex.business.service;

import com.dex.data.entity.Price;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import com.dex.infrastructure.blockchain.univ3.UniV3RealPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 真实价格服务：仅返回当前支持范围内的主网真实池价格。
 */
@Service
@RequiredArgsConstructor
public class PriceService {

    private final UniV3RealPoolService realPoolService;
    private final PrometheusMetrics prometheusMetrics;

    public Optional<Price> getLatestPrice(String pair) {
        String normalized = normalizePair(pair);
        Optional<Price> latest = switch (normalized) {
            case "ETH-USDC", "WETH-USDC" -> realPoolService.findPoolByPair("WETH", "USDC")
                    .map(pool -> toPrice("ETH-USDC", priceOf(pool, "WETH", "USDC"), pool.getBlockTimestamp()));
            case "ETH-DAI", "WETH-DAI" -> realPoolService.findPoolByPair("WETH", "DAI")
                    .map(pool -> toPrice("ETH-DAI", priceOf(pool, "WETH", "DAI"), pool.getBlockTimestamp()));
            case "USDC-ETH" -> realPoolService.findPoolByPair("WETH", "USDC")
                    .map(pool -> toPrice("USDC-ETH", priceOf(pool, "USDC", "WETH"), pool.getBlockTimestamp()));
            case "DAI-ETH" -> realPoolService.findPoolByPair("WETH", "DAI")
                    .map(pool -> toPrice("DAI-ETH", priceOf(pool, "DAI", "WETH"), pool.getBlockTimestamp()));
            case "DAI-USDC" -> realPoolService.findPoolByPair("DAI", "USDC")
                    .map(pool -> toPrice("DAI-USDC", priceOf(pool, "DAI", "USDC"), pool.getBlockTimestamp()));
            default -> Optional.empty();
        };
        latest.ifPresent(price -> prometheusMetrics.recordPriceFreshness(price.getPair(),
                Math.max(0L, System.currentTimeMillis() - price.getTimestamp())));
        return latest;
    }

    /** 返回价格历史快照列表（内存环形缓冲区，最多 120 条）。 */
    public List<Map<String, Object>> getPriceHistory(String pair) {
        String normalized = normalizePair(pair);
        HistoryQuery query = resolveHistoryQuery(normalized);
        if (query == null) return List.of();

        return realPoolService.getPriceHistory(query.storageToken0(), query.storageToken1()).stream()
                .map(pt -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("pair", query.displayPair());
                    item.put("price", query.invert() ? invert(pt.price()) : pt.price());
                    item.put("timestamp", pt.timestamp());
                    item.put("blockNumber", pt.blockNumber());
                    return item;
                })
                .toList();
    }

    /** 简单 K 线聚合：按分钟分组，返回 open/high/low/close。 */
    public List<Map<String, Object>> getCandles(String pair, int minutes) {
        List<Map<String, Object>> history = getPriceHistory(pair);
        if (history.isEmpty()) return List.of();

        long bucketMs = (long) minutes * 60_000L;
        Map<Long, List<BigDecimal>> buckets = new java.util.TreeMap<>();
        for (Map<String, Object> pt : history) {
            long ts = ((Number) pt.get("timestamp")).longValue();
            long bucket = (ts / bucketMs) * bucketMs;
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add((BigDecimal) pt.get("price"));
        }

        return buckets.entrySet().stream().map(e -> {
            List<BigDecimal> prices = e.getValue();
            BigDecimal open = prices.getFirst();
            BigDecimal close = prices.getLast();
            BigDecimal high = prices.stream().max(BigDecimal::compareTo).orElse(open);
            BigDecimal low = prices.stream().min(BigDecimal::compareTo).orElse(open);
            Map<String, Object> candle = new LinkedHashMap<>();
            candle.put("timestamp", e.getKey());
            candle.put("open", open);
            candle.put("high", high);
            candle.put("low", low);
            candle.put("close", close);
            candle.put("pair", pair);
            return candle;
        }).toList();
    }

    /** 异常检测：价格瞬时跳变超过阈值则返回告警。 */
    public List<Map<String, Object>> detectAnomalies(String pair, double thresholdPct) {
        List<Map<String, Object>> history = getPriceHistory(pair);
        if (history.size() < 2) {
            prometheusMetrics.recordPriceAnomalyCount(displayPair(pair), 0);
            return List.of();
        }

        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            BigDecimal prev = (BigDecimal) history.get(i - 1).get("price");
            BigDecimal curr = (BigDecimal) history.get(i).get("price");
            if (prev.signum() == 0) continue;
            double changePct = curr.subtract(prev).divide(prev, 8, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
            if (Math.abs(changePct) >= thresholdPct) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("pair", pair);
                alert.put("type", "PRICE_JUMP");
                alert.put("prevPrice", prev);
                alert.put("currPrice", curr);
                alert.put("changePct", String.format("%.4f", changePct));
                alert.put("timestamp", history.get(i).get("timestamp"));
                alert.put("blockNumber", history.get(i).get("blockNumber"));
                anomalies.add(alert);
            }
        }
        prometheusMetrics.recordPriceAnomalyCount(displayPair(pair), anomalies.size());
        return anomalies;
    }

    public List<String> getSupportedPairs() {
        List<String> pairs = new ArrayList<>();
        if (realPoolService.findPoolByPair("WETH", "USDC").isPresent()) pairs.add("ETH-USDC");
        if (realPoolService.findPoolByPair("WETH", "DAI").isPresent())  pairs.add("ETH-DAI");
        if (realPoolService.findPoolByPair("DAI", "USDC").isPresent())  pairs.add("DAI-USDC");
        return pairs;
    }

    private HistoryQuery resolveHistoryQuery(String normalized) {
        return switch (normalized) {
            case "ETH-USDC", "WETH-USDC" -> new HistoryQuery("USDC", "WETH", true, "ETH-USDC");
            case "ETH-DAI", "WETH-DAI" -> new HistoryQuery("DAI", "WETH", true, "ETH-DAI");
            case "USDC-ETH" -> new HistoryQuery("USDC", "WETH", false, "USDC-ETH");
            case "DAI-ETH" -> new HistoryQuery("DAI", "WETH", false, "DAI-ETH");
            case "DAI-USDC" -> new HistoryQuery("DAI", "USDC", false, "DAI-USDC");
            default -> null;
        };
    }

    private BigDecimal priceOf(RealPoolSnapshot pool, String base, String quote) {
        if (pool.getToken0Symbol().equals(base) && pool.getToken1Symbol().equals(quote)) {
            return pool.getPriceToken0InToken1();
        }
        return pool.getPriceToken1InToken0();
    }

    private Price toPrice(String pair, BigDecimal value, Long timestamp) {
        long ts = timestamp == null ? System.currentTimeMillis() : timestamp;
        return new Price(
                null,
                pair,
                value,
                ts,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
        );
    }

    private String normalizePair(String pair) {
        if (pair == null) return "";
        String v = pair.trim().toUpperCase(Locale.ROOT).replace('/', '-').replace('_', '-');
        return v.startsWith("ETH-") ? "WETH-" + v.substring(4) : v;
    }

    private String displayPair(String pair) {
        return normalizePair(pair).replace("WETH", "ETH");
    }

    private BigDecimal invert(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.ONE.divide(value, 18, java.math.RoundingMode.HALF_UP);
    }

    private record HistoryQuery(String storageToken0, String storageToken1, boolean invert, String displayPair) {}
}
