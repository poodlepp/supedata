package com.dex.business.service;

import com.dex.data.entity.LiquidityPool;
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
import java.util.Optional;

/**
 * 路由优化服务
 *
 * 当前阶段先提供可解释、可验证的演示版报价能力：
 * - 直接池报价
 * - 通过 USDC/ETH 的两跳报价
 * - 输出路径、预估输出、价格影响、gas 与淘汰原因
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final BigDecimal FEE_RATE = new BigDecimal("0.003");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal GAS_PER_HOP_USD = new BigDecimal("1.20");

    private final LiquidityService liquidityService;

    public Map<String, Object> quote(String from, String to, BigDecimal amountIn) {
        String source = normalize(from);
        String target = normalize(to);
        BigDecimal normalizedAmountIn = amountIn == null || amountIn.signum() <= 0
                ? BigDecimal.ONE
                : amountIn;

        List<LiquidityPool> pools = liquidityService.getAllPools();
        List<RouteCandidate> candidates = buildCandidates(source, target, normalizedAmountIn, pools);
        candidates.sort(Comparator.comparing(RouteCandidate::netOutput).reversed());

        RouteCandidate best = candidates.stream().filter(RouteCandidate::viable).findFirst().orElse(null);

        return Map.of(
                "fromToken", source,
                "toToken", target,
                "amountIn", normalizedAmountIn,
                "best", best == null ? null : toMap(best),
                "candidates", candidates.stream().map(this::toMap).toList(),
                "generatedAt", System.currentTimeMillis()
        );
    }

    private List<RouteCandidate> buildCandidates(String from, String to, BigDecimal amountIn, List<LiquidityPool> pools) {
        List<RouteCandidate> result = new ArrayList<>();

        result.add(buildDirectCandidate(from, to, amountIn, pools));

        for (String bridge : List.of("USDC", "ETH", "DAI", "BTC")) {
            if (bridge.equals(from) || bridge.equals(to)) {
                continue;
            }
            result.add(buildTwoHopCandidate(from, bridge, to, amountIn, pools));
        }

        return result;
    }

    private RouteCandidate buildDirectCandidate(String from, String to, BigDecimal amountIn, List<LiquidityPool> pools) {
        Optional<SwapQuote> quote = quoteAcrossPool(from, to, amountIn, pools);
        if (quote.isEmpty()) {
            return RouteCandidate.rejected(List.of(from, to), "NO_POOL", "未找到直连池");
        }
        return RouteCandidate.accepted(
                List.of(from, to),
                quote.get().amountOut,
                quote.get().grossAmountOut,
                quote.get().priceImpactPct,
                GAS_PER_HOP_USD,
                "DIRECT_POOL",
                List.of(quote.get().poolAddress)
        );
    }

    private RouteCandidate buildTwoHopCandidate(String from, String bridge, String to, BigDecimal amountIn, List<LiquidityPool> pools) {
        Optional<SwapQuote> first = quoteAcrossPool(from, bridge, amountIn, pools);
        if (first.isEmpty()) {
            return RouteCandidate.rejected(List.of(from, bridge, to), "NO_FIRST_HOP", "首跳缺少可用池");
        }

        Optional<SwapQuote> second = quoteAcrossPool(bridge, to, first.get().amountOut, pools);
        if (second.isEmpty()) {
            return RouteCandidate.rejected(List.of(from, bridge, to), "NO_SECOND_HOP", "第二跳缺少可用池");
        }

        BigDecimal totalGas = GAS_PER_HOP_USD.multiply(new BigDecimal("2"));
        BigDecimal weightedImpact = first.get().priceImpactPct.add(second.get().priceImpactPct);

        return RouteCandidate.accepted(
                List.of(from, bridge, to),
                second.get().amountOut,
                second.get().grossAmountOut,
                weightedImpact,
                totalGas,
                "TWO_HOP",
                List.of(first.get().poolAddress, second.get().poolAddress)
        );
    }

    private Optional<SwapQuote> quoteAcrossPool(String from, String to, BigDecimal amountIn, List<LiquidityPool> pools) {
        return pools.stream()
                .map(pool -> quotePool(pool, from, to, amountIn))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(q -> q.amountOut));
    }

    private Optional<SwapQuote> quotePool(LiquidityPool pool, String from, String to, BigDecimal amountIn) {
        String token0 = normalize(pool.getToken0());
        String token1 = normalize(pool.getToken1());

        boolean forward = token0.equals(from) && token1.equals(to);
        boolean reverse = token1.equals(from) && token0.equals(to);
        if (!forward && !reverse) {
            return Optional.empty();
        }

        BigDecimal reserveIn = forward ? pool.getReserve0() : pool.getReserve1();
        BigDecimal reserveOut = forward ? pool.getReserve1() : pool.getReserve0();
        if (reserveIn == null || reserveOut == null || reserveIn.signum() <= 0 || reserveOut.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal amountInAfterFee = amountIn.multiply(ONE.subtract(FEE_RATE));
        BigDecimal numerator = amountInAfterFee.multiply(reserveOut);
        BigDecimal denominator = reserveIn.add(amountInAfterFee);
        if (denominator.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal amountOut = numerator.divide(denominator, 8, RoundingMode.HALF_UP);
        BigDecimal spotPrice = reserveOut.divide(reserveIn, 8, RoundingMode.HALF_UP);
        BigDecimal grossAmountOut = amountIn.multiply(spotPrice).setScale(8, RoundingMode.HALF_UP);
        BigDecimal priceImpact = grossAmountOut.signum() == 0
                ? BigDecimal.ZERO
                : grossAmountOut.subtract(amountOut)
                    .divide(grossAmountOut, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

        return Optional.of(new SwapQuote(pool.getPoolAddress(), amountOut, grossAmountOut, priceImpact));
    }

    private Map<String, Object> toMap(RouteCandidate candidate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("path", candidate.path());
        map.put("viable", candidate.viable());
        map.put("type", candidate.type());
        map.put("reasonCode", candidate.reasonCode());
        map.put("reason", candidate.reason());
        map.put("poolAddresses", candidate.poolAddresses());
        map.put("amountOut", candidate.amountOut());
        map.put("grossAmountOut", candidate.grossAmountOut());
        map.put("gasCostUsd", candidate.gasCostUsd());
        map.put("priceImpactPct", candidate.priceImpactPct());
        map.put("netScore", candidate.netOutput());
        return map;
    }

    private String normalize(String token) {
        return token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
    }

    private record SwapQuote(String poolAddress, BigDecimal amountOut, BigDecimal grossAmountOut, BigDecimal priceImpactPct) {
    }

    private record RouteCandidate(
            List<String> path,
            boolean viable,
            String type,
            String reasonCode,
            String reason,
            List<String> poolAddresses,
            BigDecimal amountOut,
            BigDecimal grossAmountOut,
            BigDecimal gasCostUsd,
            BigDecimal priceImpactPct
    ) {
        static RouteCandidate accepted(List<String> path,
                                       BigDecimal amountOut,
                                       BigDecimal grossAmountOut,
                                       BigDecimal priceImpactPct,
                                       BigDecimal gasCostUsd,
                                       String type,
                                       List<String> poolAddresses) {
            return new RouteCandidate(path, true, type, null, "可用路径", poolAddresses, amountOut, grossAmountOut, gasCostUsd, priceImpactPct);
        }

        static RouteCandidate rejected(List<String> path, String reasonCode, String reason) {
            return new RouteCandidate(path, false, "REJECTED", reasonCode, reason, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        BigDecimal netOutput() {
            return amountOut.subtract(gasCostUsd);
        }
    }
}
