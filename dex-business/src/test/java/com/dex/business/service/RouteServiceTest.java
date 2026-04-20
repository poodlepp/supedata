package com.dex.business.service;

import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import com.dex.infrastructure.blockchain.univ3.UniV3RealPoolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RouteServiceTest {

    private UniV3RealPoolService realPoolService;
    private RouteService routeService;

    private static final RealPoolSnapshot WETH_USDC_500 = RealPoolSnapshot.builder()
            .poolAddress("0x88e6A0c2dDD26FEEb64F039a2c41296FcB3f5640")
            .poolName("Uniswap V3 USDC/WETH 0.05%")
            .dex("Uniswap V3")
            .token0Symbol("USDC").token1Symbol("WETH")
            .token0Address("0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
            .token1Address("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2")
            .token0Decimals(6).token1Decimals(18)
            .fee(500).blockNumber(20000000L).blockTimestamp(1_712_000_000_000L)
            .priceToken0InToken1(new BigDecimal("0.000322"))
            .priceToken1InToken0(new BigDecimal("3105"))
            .reserve0(new BigDecimal("1200000")).reserve1(new BigDecimal("386"))
            .build();

    private static final RealPoolSnapshot WETH_USDC_3000 = RealPoolSnapshot.builder()
            .poolAddress("0x8ad599c3A0ff1De082011EFDDc58f1908eb6e6D8")
            .poolName("Uniswap V3 USDC/WETH 0.30%")
            .dex("Uniswap V3")
            .token0Symbol("USDC").token1Symbol("WETH")
            .token0Address("0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")
            .token1Address("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2")
            .token0Decimals(6).token1Decimals(18)
            .fee(3000).blockNumber(20000000L).blockTimestamp(1_712_000_000_000L)
            .priceToken0InToken1(new BigDecimal("0.000321"))
            .priceToken1InToken0(new BigDecimal("3115"))
            .reserve0(new BigDecimal("800000")).reserve1(new BigDecimal("257"))
            .build();

    @BeforeEach
    void setUp() {
        realPoolService = mock(UniV3RealPoolService.class);
        routeService = new RouteService(realPoolService);
    }

    private static Map<String, Object> singleQuoteResult(String poolAddress, String poolName, int fee,
                                                          String amountOut, String priceImpact) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("supported", true);
        m.put("amountOut", new BigDecimal(amountOut));
        m.put("grossAmountOut", new BigDecimal(amountOut).add(BigDecimal.ONE));
        m.put("priceImpactPct", new BigDecimal(priceImpact));
        m.put("poolAddress", poolAddress);
        m.put("poolName", poolName);
        m.put("fee", fee);
        m.put("blockNumber", 20000000L);
        m.put("blockTimestamp", 1_712_000_000_000L);
        m.put("source", "uniswap-v3-quoter-v1");
        m.put("dex", "Uniswap V3");
        m.put("tokenIn", "WETH");
        m.put("tokenOut", "USDC");
        return m;
    }

    @Test
    void quoteShouldReturnBestCandidateWhenDirectPoolExists() {
        when(realPoolService.findAllPoolsByPair(anyString(), anyString()))
                .thenReturn(List.of());
        when(realPoolService.findAllPoolsByPair("WETH", "USDC"))
                .thenReturn(List.of(WETH_USDC_500, WETH_USDC_3000));

        Map<String, Object> singleResult = singleQuoteResult(
                WETH_USDC_500.getPoolAddress(), WETH_USDC_500.getPoolName(), 500, "3100", "0.16");
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleResult);
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(
                        WETH_USDC_3000.getPoolAddress(), WETH_USDC_3000.getPoolName(), 3000, "3090", "0.22"));
        when(realPoolService.quoteMultiHopExactInput(anyList(), any()))
                .thenReturn(Map.of("supported", false, "reason", "HOP_FAILED", "message", "no pool"));

        Map<String, Object> result = routeService.quote("ETH", "USDC", BigDecimal.ONE);

        assertNotNull(result.get("best"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertEquals(3, candidates.size());
        assertEquals(500, ((Map<?, ?>) result.get("best")).get("fee"));
        assertEquals("multi-path-real-pools", result.get("mode"));
    }

    @Test
    void compareShouldReturnRankedAndEliminatedLists() {
        when(realPoolService.findAllPoolsByPair(anyString(), anyString()))
                .thenReturn(List.of());
        when(realPoolService.findAllPoolsByPair("WETH", "USDC"))
                .thenReturn(List.of(WETH_USDC_500));

        Map<String, Object> singleResult = singleQuoteResult(
                WETH_USDC_500.getPoolAddress(), WETH_USDC_500.getPoolName(), 500, "3100", "0.16");
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleResult);
        when(realPoolService.quoteMultiHopExactInput(anyList(), any()))
                .thenReturn(Map.of("supported", false, "reason", "NO_POOL", "message", "no pool"));

        Map<String, Object> result = routeService.compare("ETH", "USDC", BigDecimal.ONE);

        assertNotNull(result.get("ranked"));
        assertNotNull(result.get("eliminated"));
        assertTrue((int) result.get("viableCount") >= 0);
    }

    @Test
    void quoteShouldReturnUnsupportedWhenNoPools() {
        when(realPoolService.findAllPoolsByPair(anyString(), anyString())).thenReturn(List.of());
        when(realPoolService.quoteMultiHopExactInput(anyList(), any()))
                .thenReturn(Map.of("supported", false, "reason", "NO_POOL", "message", "no pool"));

        Map<String, Object> result = routeService.quote("UNKNOWN", "TOKEN", BigDecimal.ONE);

        assertNull(result.get("best"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) result.get("candidates");
        assertFalse(candidates.isEmpty());
        assertFalse((Boolean) candidates.getFirst().get("viable"));
    }

    @Test
    void ethShouldBeNormalizedToWeth() {
        when(realPoolService.findAllPoolsByPair(anyString(), anyString()))
                .thenReturn(List.of());
        when(realPoolService.findAllPoolsByPair("WETH", "USDC"))
                .thenReturn(List.of(WETH_USDC_500));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult("0xpool", "test", 500, "3100", "0.16"));
        when(realPoolService.quoteMultiHopExactInput(anyList(), any()))
                .thenReturn(Map.of("supported", false, "reason", "NO_POOL", "message", "no pool"));

        Map<String, Object> result = routeService.quote("ETH", "USDC", BigDecimal.ONE);
        assertEquals("WETH", result.get("fromToken"));
    }

    @Test
    void compareShouldKeepFeeTierQuotesDistinct() {
        when(realPoolService.findAllPoolsByPair(anyString(), anyString()))
                .thenReturn(List.of());
        when(realPoolService.findAllPoolsByPair("WETH", "USDC"))
                .thenReturn(List.of(WETH_USDC_500, WETH_USDC_3000));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_500), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(
                        WETH_USDC_500.getPoolAddress(), WETH_USDC_500.getPoolName(), 500, "3100", "0.16"));
        when(realPoolService.quoteExactInputSingle(eq(WETH_USDC_3000), eq("WETH"), eq("USDC"), any()))
                .thenReturn(singleQuoteResult(
                        WETH_USDC_3000.getPoolAddress(), WETH_USDC_3000.getPoolName(), 3000, "3120", "0.18"));
        when(realPoolService.quoteMultiHopExactInput(anyList(), any()))
                .thenReturn(Map.of("supported", false, "reason", "NO_POOL", "message", "no pool"));

        Map<String, Object> result = routeService.compare("ETH", "USDC", BigDecimal.ONE);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) result.get("ranked");
        assertEquals(2, ranked.size());
        assertEquals(3000, ranked.getFirst().get("fee"));
        assertEquals(new BigDecimal("3120"), ranked.getFirst().get("amountOut"));
        assertEquals(500, ranked.get(1).get("fee"));
    }
}
