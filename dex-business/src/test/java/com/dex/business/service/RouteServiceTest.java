package com.dex.business.service;

import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import com.dex.infrastructure.blockchain.univ3.UniV3RealPoolService;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteServiceTest {

    private static final long NOW = System.currentTimeMillis();

    private UniV3RealPoolService realPoolService;
    private PrometheusMetrics prometheusMetrics;
    private RouteService routeService;

    private static final RealPoolSnapshot WETH_USDC_500 = pool(
            "0xpool-weth-usdc-500", "Uniswap V3 USDC/WETH 0.05%", "USDC", "WETH",
            500, "0.000322", "3105", "1200000", "386", "1000000");
    private static final RealPoolSnapshot WETH_USDC_3000 = pool(
            "0xpool-weth-usdc-3000", "Uniswap V3 USDC/WETH 0.30%", "USDC", "WETH",
            3000, "0.000321", "3115", "800000", "257", "800000");
    private static final RealPoolSnapshot WETH_DAI_500 = pool(
            "0xpool-weth-dai-500", "Uniswap V3 DAI/WETH 0.05%", "DAI", "WETH",
            500, "0.000323", "3095", "1500000", "484", "1500000");
    private static final RealPoolSnapshot WETH_DAI_3000 = pool(
            "0xpool-weth-dai-3000", "Uniswap V3 DAI/WETH 0.30%", "DAI", "WETH",
            3000, "0.000322", "3100", "1800000", "580", "1800000");
    private static final RealPoolSnapshot DAI_USDC_100 = pool(
            "0xpool-dai-usdc-100", "Uniswap V3 DAI/USDC 0.01%", "DAI", "USDC",
            100, "1.0002", "0.9998", "2200000", "2200000", "2200000");
    private static final RealPoolSnapshot DAI_USDC_500 = pool(
            "0xpool-dai-usdc-500", "Uniswap V3 DAI/USDC 0.05%", "DAI", "USDC",
            500, "1.0000", "1.0000", "1400000", "1400000", "1400000");

    @BeforeEach
    void setUp() {
        realPoolService = mock(UniV3RealPoolService.class);
        prometheusMetrics = mock(PrometheusMetrics.class);
        routeService = new RouteService(realPoolService, prometheusMetrics);
    }

    @Test
    void quoteShouldReturnBestDirectPoolWhenDirectRouteWins() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of(WETH_USDC_500, WETH_USDC_3000));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "3100", "3110"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(WETH_USDC_3000, "WETH", "USDC", "3090", "3100"));

        Map<String, Object> result = routeService.quote("ETH", "USDC", BigDecimal.ONE);

        assertEquals("WETH", result.get("fromToken"));
        assertEquals("layered-beam-search-real-pools", result.get("mode"));
        assertNotNull(result.get("best"));
        @SuppressWarnings("unchecked")
        Map<String, Object> best = (Map<String, Object>) result.get("best");
        assertEquals(500, best.get("fee"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertEquals(2, candidates.size());
        assertEquals(new BigDecimal("3098.80000000"), best.get("netScore"));
    }

    @Test
    void quoteShouldChooseMultiHopWhenGasAdjustedOutputIsHigher() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of(WETH_USDC_500, WETH_DAI_3000, DAI_USDC_100));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "3100", "3110"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_DAI_3000), eq("WETH"), eq("DAI"), any()))
                .thenReturn(singleQuoteResult(WETH_DAI_3000, "WETH", "DAI", "3200", "3215"));
        when(realPoolService.quoteExactInputSingle(eq(DAI_USDC_100), eq("DAI"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(DAI_USDC_100, "DAI", "USDC", "3103", "3110"));

        Map<String, Object> result = routeService.quote("ETH", "USDC", BigDecimal.ONE);

        @SuppressWarnings("unchecked")
        Map<String, Object> best = (Map<String, Object>) result.get("best");
        assertEquals(List.of("WETH", "DAI", "USDC"), best.get("path"));
        assertEquals(new BigDecimal("3100.60000000"), best.get("netScore"));
        assertEquals(new BigDecimal("2.40000000"), best.get("gasCostUsd"));
    }

    @Test
    void compareShouldExposeSplitOptionWhenSplitBeatsBestSingleRoute() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of(WETH_USDC_500, WETH_USDC_3000));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), argThat(amountEq("1"))))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "3000", "3020"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), argThat(amountEq("1"))))
                .thenReturn(singleQuoteResult(WETH_USDC_3000, "WETH", "USDC", "2995", "3015"));

        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), argThat(amountEq("0.50"))))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "1565", "1575"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), argThat(amountEq("0.50"))))
                .thenReturn(singleQuoteResult(WETH_USDC_3000, "WETH", "USDC", "1558", "1568"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), argThat(amountEq("0.25"))))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "785", "790"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), argThat(amountEq("0.25"))))
                .thenReturn(singleQuoteResult(WETH_USDC_3000, "WETH", "USDC", "780", "786"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), argThat(amountEq("0.75"))))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "2340", "2355"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), argThat(amountEq("0.75"))))
                .thenReturn(singleQuoteResult(WETH_USDC_3000, "WETH", "USDC", "2325", "2340"));

        Map<String, Object> result = routeService.compare("ETH", "USDC", BigDecimal.ONE);

        @SuppressWarnings("unchecked")
        Map<String, Object> bestExecution = (Map<String, Object>) result.get("bestExecution");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> splitOptions = (List<Map<String, Object>>) result.get("splitOptions");

        assertEquals("SPLIT", bestExecution.get("executionType"));
        assertFalse(splitOptions.isEmpty());
        assertTrue((Boolean) splitOptions.getFirst().get("betterThanBestSingle"));
    }

    @Test
    void compareShouldEnumerateMultiplePoolCombinationsAcrossHops() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of(
                WETH_DAI_500, WETH_DAI_3000, DAI_USDC_100, DAI_USDC_500
        ));

        when(realPoolService.quoteExactInputSingle(eq(WETH_DAI_500), eq("WETH"), eq("DAI"), any()))
                .thenReturn(singleQuoteResult(WETH_DAI_500, "WETH", "DAI", "3200", "3210"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_DAI_3000), eq("WETH"), eq("DAI"), any()))
                .thenReturn(singleQuoteResult(WETH_DAI_3000, "WETH", "DAI", "3210", "3225"));

        when(realPoolService.quoteExactInputSingle(eq(DAI_USDC_100), eq("DAI"), eq("USDC"), argThat(amountEq("3200"))))
                .thenReturn(singleQuoteResult(DAI_USDC_100, "DAI", "USDC", "3099", "3104"));
        when(realPoolService.quoteExactInputSingle(eq(DAI_USDC_500), eq("DAI"), eq("USDC"), argThat(amountEq("3200"))))
                .thenReturn(singleQuoteResult(DAI_USDC_500, "DAI", "USDC", "3097", "3101"));
        when(realPoolService.quoteExactInputSingle(eq(DAI_USDC_100), eq("DAI"), eq("USDC"), argThat(amountEq("3210"))))
                .thenReturn(singleQuoteResult(DAI_USDC_100, "DAI", "USDC", "3105", "3112"));
        when(realPoolService.quoteExactInputSingle(eq(DAI_USDC_500), eq("DAI"), eq("USDC"), argThat(amountEq("3210"))))
                .thenReturn(singleQuoteResult(DAI_USDC_500, "DAI", "USDC", "3100", "3108"));

        Map<String, Object> result = routeService.compare("ETH", "USDC", BigDecimal.ONE);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) result.get("ranked");
        assertEquals(4, ranked.size());
        assertEquals(List.of("0xpool-weth-dai-3000", "0xpool-dai-usdc-100"), ranked.getFirst().get("poolAddresses"));
        assertEquals(new BigDecimal("3102.60000000"), ranked.getFirst().get("netScore"));
    }

    @Test
    void layeredExpansionShouldAvoidRevisitingTokens() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of(WETH_USDC_500, WETH_DAI_3000));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "3100", "3110"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_DAI_3000), eq("WETH"), eq("DAI"), any()))
                .thenReturn(singleQuoteResult(WETH_DAI_3000, "WETH", "DAI", "3200", "3215"));

        Map<String, Object> result = routeService.compare("ETH", "USDC", BigDecimal.ONE);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) result.get("ranked");
        assertEquals(1, ranked.size());
        assertEquals(List.of("WETH", "USDC"), ranked.getFirst().get("path"));
    }

    @Test
    void quoteShouldReturnUnsupportedWhenNoPoolsExist() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of());

        Map<String, Object> result = routeService.quote("UNKNOWN", "TOKEN", BigDecimal.ONE);

        assertNull(result.get("best"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertEquals(1, candidates.size());
        assertFalse((Boolean) candidates.getFirst().get("viable"));
    }

    @Test
    void compareShouldSeparateRankedAndEliminatedByPriceImpact() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of(WETH_USDC_500, WETH_USDC_3000));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "3100", "3110"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(WETH_USDC_3000, "WETH", "USDC", "2800", "3000"));

        Map<String, Object> result = routeService.compare("ETH", "USDC", BigDecimal.ONE);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) result.get("ranked");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> eliminated = (List<Map<String, Object>>) result.get("eliminated");
        assertEquals(1, ranked.size());
        assertEquals(1, eliminated.size());
        assertTrue(((String) eliminated.getFirst().get("eliminationReason")).contains("价格冲击"));
    }

    @Test
    void quoteShouldExposeScoreBreakdownAndFreshnessFields() {
        when(realPoolService.getSupportedPools()).thenReturn(List.of(WETH_USDC_500));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(WETH_USDC_500, "WETH", "USDC", "3100", "3110"));

        Map<String, Object> result = routeService.quote("ETH", "USDC", BigDecimal.ONE);

        @SuppressWarnings("unchecked")
        Map<String, Object> best = (Map<String, Object>) result.get("best");
        @SuppressWarnings("unchecked")
        Map<String, Object> scoreBreakdown = (Map<String, Object>) best.get("scoreBreakdown");

        assertNotNull(scoreBreakdown);
        assertNotNull(best.get("lpFeeCostUsd"));
        assertNotNull(best.get("gasAmount"));
        assertNotNull(best.get("liquidityDepthScore"));
        assertEquals("fresh", best.get("quoteFreshness"));
    }

    private static RealPoolSnapshot pool(String address,
                                         String name,
                                         String token0,
                                         String token1,
                                         int fee,
                                         String priceToken0InToken1,
                                         String priceToken1InToken0,
                                         String reserve0,
                                         String reserve1,
                                         String liquidity) {
        return RealPoolSnapshot.builder()
                .poolAddress(address)
                .poolName(name)
                .dex("Uniswap V3")
                .token0Symbol(token0)
                .token1Symbol(token1)
                .token0Address("0x" + token0.toLowerCase())
                .token1Address("0x" + token1.toLowerCase())
                .token0Decimals("WETH".equals(token0) ? 18 : 6)
                .token1Decimals("WETH".equals(token1) ? 18 : 6)
                .fee(fee)
                .blockNumber(20_000_000L)
                .blockTimestamp(NOW)
                .priceToken0InToken1(new BigDecimal(priceToken0InToken1))
                .priceToken1InToken0(new BigDecimal(priceToken1InToken0))
                .reserve0(new BigDecimal(reserve0))
                .reserve1(new BigDecimal(reserve1))
                .liquidity(new BigDecimal(liquidity))
                .build();
    }

    private static Map<String, Object> singleQuoteResult(RealPoolSnapshot pool,
                                                         String tokenIn,
                                                         String tokenOut,
                                                         String amountOut,
                                                         String grossAmountOut) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("supported", true);
        data.put("tokenIn", tokenIn);
        data.put("tokenOut", tokenOut);
        data.put("amountIn", BigDecimal.ONE);
        data.put("amountOut", new BigDecimal(amountOut));
        data.put("grossAmountOut", new BigDecimal(grossAmountOut));
        data.put("priceImpactPct", priceImpact(amountOut, grossAmountOut));
        data.put("poolAddress", pool.getPoolAddress());
        data.put("poolName", pool.getPoolName());
        data.put("fee", pool.getFee());
        data.put("blockNumber", pool.getBlockNumber());
        data.put("blockTimestamp", NOW);
        data.put("source", "uniswap-v3-quoter-v1");
        data.put("dex", pool.getDex());
        return data;
    }

    private static BigDecimal priceImpact(String amountOut, String grossAmountOut) {
        BigDecimal actual = new BigDecimal(amountOut);
        BigDecimal gross = new BigDecimal(grossAmountOut);
        return gross.subtract(actual)
                .divide(gross, 8, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(6, java.math.RoundingMode.HALF_UP);
    }

    private static org.mockito.ArgumentMatcher<BigDecimal> amountEq(String expected) {
        BigDecimal value = new BigDecimal(expected);
        return amount -> amount != null && amount.compareTo(value) == 0;
    }
}
