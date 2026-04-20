package com.dex.business.service;

import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import com.dex.infrastructure.blockchain.univ3.UniV3RealPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 报价与路由服务：支持同币对多费率层对比 + 多跳路径，返回多候选路径及评分。
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    /** 每跳固定 gas 成本（USD），用于净收益评分 */
    private static final BigDecimal GAS_PER_HOP_USD = new BigDecimal("1.20");
    /** 价格冲击超过此阈值则标记为淘汰 */
    private static final BigDecimal PRICE_IMPACT_THRESHOLD = new BigDecimal("5.0");

    private final UniV3RealPoolService realPoolService;

    // ------------------------------------------------------------------ quote

    public Map<String, Object> quote(String from, String to, BigDecimal amountIn) {
        String source = normalize(from);
        String target = normalize(to);
        BigDecimal safeAmount = amountIn == null || amountIn.signum() <= 0 ? BigDecimal.ONE : amountIn;

        List<Map<String, Object>> candidates = buildCandidates(source, target, safeAmount);
        Map<String, Object> best = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("viable")))
                .max(Comparator.comparing(c -> (BigDecimal) c.get("netScore")))
                .orElse(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromToken", source);
        payload.put("toToken", target);
        payload.put("amountIn", safeAmount);
        payload.put("best", best);
        payload.put("candidates", candidates);
        payload.put("generatedAt", System.currentTimeMillis());
        payload.put("mode", "multi-path-real-pools");
        return payload;
    }

    // ---------------------------------------------------------------- compare

    public Map<String, Object> compare(String from, String to, BigDecimal amountIn) {
        String source = normalize(from);
        String target = normalize(to);
        BigDecimal safeAmount = amountIn == null || amountIn.signum() <= 0 ? BigDecimal.ONE : amountIn;

        List<Map<String, Object>> candidates = buildCandidates(source, target, safeAmount);
        List<Map<String, Object>> viable = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("viable")))
                .sorted(Comparator.comparing((Map<String, Object> c) -> (BigDecimal) c.get("netScore")).reversed())
                .toList();
        List<Map<String, Object>> eliminated = candidates.stream()
                .filter(c -> !Boolean.TRUE.equals(c.get("viable")))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromToken", source);
        payload.put("toToken", target);
        payload.put("amountIn", safeAmount);
        payload.put("viableCount", viable.size());
        payload.put("eliminatedCount", eliminated.size());
        payload.put("ranked", viable);
        payload.put("eliminated", eliminated);
        payload.put("generatedAt", System.currentTimeMillis());
        return payload;
    }

    // --------------------------------------------------------- path building

    private List<Map<String, Object>> buildCandidates(String source, String target, BigDecimal amountIn) {
        List<Map<String, Object>> candidates = new ArrayList<>();

        // 1. 直接路径：同交易对所有费率层
        List<RealPoolSnapshot> directPools = realPoolService.findAllPoolsByPair(source, target);
        for (RealPoolSnapshot pool : directPools) {
            candidates.add(buildDirectCandidate(source, target, amountIn, pool));
        }

        // 2. 多跳路径：通过中间 token
        for (String mid : List.of("DAI", "USDC", "WETH")) {
            if (mid.equals(source) || mid.equals(target)) continue;
            candidates.add(buildMultiHopCandidate(source, mid, target, amountIn));
        }

        // 如果没有任何候选，返回一个不支持的占位
        if (candidates.isEmpty()) {
            candidates.add(unsupportedCandidate(source, target, "PAIR_NOT_SUPPORTED", "当前不支持该交易对"));
        }
        return candidates;
    }

    private Map<String, Object> buildDirectCandidate(String source, String target,
                                                      BigDecimal amountIn, RealPoolSnapshot pool) {
        Map<String, Object> single = realPoolService.quoteExactInputSingle(pool, source, target, amountIn);
        if (!Boolean.TRUE.equals(single.get("supported"))) {
            return unsupportedCandidate(source, target,
                    (String) single.get("reason"), (String) single.get("message"));
        }

        BigDecimal amountOut = (BigDecimal) single.get("amountOut");
        BigDecimal priceImpact = (BigDecimal) single.get("priceImpactPct");
        BigDecimal gasCost = GAS_PER_HOP_USD;
        BigDecimal netScore = amountOut.subtract(gasCost).setScale(8, RoundingMode.HALF_UP);

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("path", List.of(source, target));
        c.put("type", "DIRECT_" + pool.getFee());
        c.put("label", pool.getPoolName());
        c.put("viable", priceImpact.compareTo(PRICE_IMPACT_THRESHOLD) < 0);
        c.put("eliminationReason", priceImpact.compareTo(PRICE_IMPACT_THRESHOLD) >= 0
                ? "价格冲击 " + priceImpact.setScale(2, RoundingMode.HALF_UP) + "% 超过阈值" : null);
        c.put("poolAddresses", List.of(pool.getPoolAddress()));
        c.put("poolName", pool.getPoolName());
        c.put("fee", pool.getFee());
        c.put("hopCount", 1);
        c.put("amountOut", amountOut);
        c.put("grossAmountOut", single.get("grossAmountOut"));
        c.put("gasCostUsd", gasCost);
        c.put("priceImpactPct", priceImpact);
        c.put("netScore", netScore);
        c.put("blockNumber", single.get("blockNumber"));
        c.put("blockTimestamp", single.get("blockTimestamp"));
        c.put("source", single.get("source"));
        c.put("dex", single.get("dex"));
        return c;
    }

    private Map<String, Object> buildMultiHopCandidate(String source, String mid, String target,
                                                        BigDecimal amountIn) {
        // 检查两段路径是否都有池子
        List<RealPoolSnapshot> leg1Pools = realPoolService.findAllPoolsByPair(source, mid);
        List<RealPoolSnapshot> leg2Pools = realPoolService.findAllPoolsByPair(mid, target);
        if (leg1Pools.isEmpty() || leg2Pools.isEmpty()) {
            return unsupportedCandidate(source, target,
                    "NO_INTERMEDIATE_POOL",
                    "缺少中间 token " + mid + " 的池子");
        }

        Map<String, Object> result = realPoolService.quoteMultiHopExactInput(
                List.of(source, mid, target), amountIn);

        if (!Boolean.TRUE.equals(result.get("supported"))) {
            return unsupportedCandidate(source, target,
                    (String) result.get("reason"), (String) result.get("message"));
        }

        BigDecimal amountOut = (BigDecimal) result.get("amountOut");
        BigDecimal priceImpact = (BigDecimal) result.get("priceImpactPct");
        BigDecimal gasCost = GAS_PER_HOP_USD.multiply(new BigDecimal("2")); // 2 跳
        BigDecimal netScore = amountOut.subtract(gasCost).setScale(8, RoundingMode.HALF_UP);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> hops = (List<Map<String, Object>>) result.get("hops");
        List<String> poolAddresses = hops.stream()
                .map(h -> (String) h.get("poolAddress"))
                .toList();

        boolean viable = priceImpact.compareTo(PRICE_IMPACT_THRESHOLD) < 0;
        String eliminationReason = null;
        if (!viable) {
            eliminationReason = "多跳累计价格冲击 " + priceImpact.setScale(2, RoundingMode.HALF_UP) + "% 超过阈值";
        }

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("path", List.of(source, mid, target));
        c.put("type", "MULTI_HOP_VIA_" + mid);
        c.put("label", source + " → " + mid + " → " + target);
        c.put("viable", viable);
        c.put("eliminationReason", eliminationReason);
        c.put("poolAddresses", poolAddresses);
        c.put("hopCount", 2);
        c.put("amountOut", amountOut);
        c.put("grossAmountOut", null);
        c.put("gasCostUsd", gasCost);
        c.put("priceImpactPct", priceImpact);
        c.put("netScore", netScore);
        c.put("blockNumber", result.get("blockNumber"));
        c.put("blockTimestamp", result.get("blockTimestamp"));
        c.put("source", result.get("source"));
        c.put("dex", result.get("dex"));
        c.put("hops", hops);
        return c;
    }

    private Map<String, Object> unsupportedCandidate(String source, String target,
                                                      String reasonCode, String reason) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("path", List.of(source, target));
        c.put("type", "UNSUPPORTED");
        c.put("label", source + " → " + target);
        c.put("viable", false);
        c.put("eliminationReason", reason);
        c.put("reasonCode", reasonCode);
        c.put("poolAddresses", List.of());
        c.put("hopCount", 0);
        c.put("amountOut", BigDecimal.ZERO);
        c.put("grossAmountOut", BigDecimal.ZERO);
        c.put("gasCostUsd", BigDecimal.ZERO);
        c.put("priceImpactPct", BigDecimal.ZERO);
        c.put("netScore", BigDecimal.ZERO);
        c.put("source", "unsupported");
        return c;
    }

    private String normalize(String token) {
        if (token == null) return "";
        String v = token.trim().toUpperCase(Locale.ROOT);
        return v.equals("ETH") ? "WETH" : v;
    }
}
