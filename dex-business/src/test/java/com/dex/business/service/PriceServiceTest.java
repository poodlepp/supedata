package com.dex.business.service;

import com.dex.infrastructure.blockchain.univ3.UniV3RealPoolService;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceServiceTest {

    @Test
    void historyShouldInvertUsdcWethSeriesForEthUsdcRequests() {
        UniV3RealPoolService realPoolService = mock(UniV3RealPoolService.class);
        PrometheusMetrics prometheusMetrics = mock(PrometheusMetrics.class);
        when(realPoolService.getPriceHistory("USDC", "WETH")).thenReturn(List.of(
                new UniV3RealPoolService.PricePoint(1_712_000_000_000L, new BigDecimal("0.000322"), 20_000_000L),
                new UniV3RealPoolService.PricePoint(1_712_000_015_000L, new BigDecimal("0.000321"), 20_000_001L)
        ));

        PriceService priceService = new PriceService(realPoolService, prometheusMetrics);
        List<Map<String, Object>> history = priceService.getPriceHistory("ETH-USDC");

        assertEquals(2, history.size());
        assertEquals("ETH-USDC", history.getFirst().get("pair"));
        BigDecimal firstPrice = (BigDecimal) history.getFirst().get("price");
        BigDecimal secondPrice = (BigDecimal) history.get(1).get("price");
        assertTrue(firstPrice.compareTo(new BigDecimal("3000")) > 0);
        assertTrue(secondPrice.compareTo(firstPrice) > 0);
    }

    @Test
    void historyShouldKeepOriginalDirectionForUsdcEthRequests() {
        UniV3RealPoolService realPoolService = mock(UniV3RealPoolService.class);
        PrometheusMetrics prometheusMetrics = mock(PrometheusMetrics.class);
        when(realPoolService.getPriceHistory("USDC", "WETH")).thenReturn(List.of(
                new UniV3RealPoolService.PricePoint(1_712_000_000_000L, new BigDecimal("0.000322"), 20_000_000L)
        ));

        PriceService priceService = new PriceService(realPoolService, prometheusMetrics);
        List<Map<String, Object>> history = priceService.getPriceHistory("USDC-ETH");

        assertEquals(1, history.size());
        assertEquals("USDC-ETH", history.getFirst().get("pair"));
        assertEquals(new BigDecimal("0.000322"), history.getFirst().get("price"));
    }
}
