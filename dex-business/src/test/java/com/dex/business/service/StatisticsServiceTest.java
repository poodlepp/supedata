package com.dex.business.service;

import com.dex.data.entity.Price;
import com.dex.data.service.DataCacheService;
import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    @Test
    void shouldBuildAndCacheOverviewSnapshotOnCacheMiss() {
        LiquidityService liquidityService = mock(LiquidityService.class);
        PriceService priceService = mock(PriceService.class);
        StageStatusService stageStatusService = mock(StageStatusService.class);
        DataCacheService dataCacheService = mock(DataCacheService.class);

        when(dataCacheService.get("dex:stats:overview:v1", Map.class)).thenReturn(Optional.empty());
        when(priceService.getSupportedPairs()).thenReturn(List.of("ETH-USDC"));
        when(priceService.getLatestPrice("ETH-USDC")).thenReturn(Optional.of(
                new Price(1L, "ETH-USDC", new BigDecimal("3100.1234"), 1_712_345_678_000L, LocalDateTime.now())
        ));
        when(liquidityService.getAllRealPools()).thenReturn(List.of(
                RealPoolSnapshot.builder()
                        .poolAddress("0xpool")
                        .poolName("Uniswap V3 USDC/WETH 0.05%")
                        .dex("Uniswap V3")
                        .token0Symbol("USDC")
                        .token1Symbol("WETH")
                        .fee(500)
                        .blockNumber(12345678L)
                        .blockTimestamp(1_712_345_678_000L)
                        .priceToken0InToken1(new BigDecimal("0.000322"))
                        .priceToken1InToken0(new BigDecimal("3105"))
                        .reserve0(new BigDecimal("1200000"))
                        .reserve1(new BigDecimal("386"))
                        .source("ethereum-mainnet-uniswap-v3-pool")
                        .build()
        ));
        when(stageStatusService.getSummary()).thenReturn(Map.of(
                "totalStages", 7,
                "doneStages", 5,
                "stagePreview", List.of()
        ));

        StatisticsService statisticsService = new StatisticsService(
                liquidityService,
                priceService,
                stageStatusService,
                dataCacheService
        );

        Map<String, Object> overview = statisticsService.getOverview();

        Map<?, ?> market = (Map<?, ?>) overview.get("market");
        Map<?, ?> metricBoundary = (Map<?, ?>) overview.get("metricBoundary");
        assertEquals(1, market.get("poolCount"));
        assertEquals(1, market.get("pricePairCount"));
        assertEquals(new BigDecimal("2400000.00"), overview.get("totalTvlUsdEstimate"));
        assertFalse(((List<?>) overview.get("highlights")).isEmpty());
        assertFalse(((List<?>) metricBoundary.get("included")).isEmpty());
        assertNotNull(overview.get("stageSummary"));

        verify(dataCacheService).put(eq("dex:stats:overview:v1"), eq(overview), any(Duration.class));
    }
}
