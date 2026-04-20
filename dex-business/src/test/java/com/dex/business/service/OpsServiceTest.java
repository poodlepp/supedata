package com.dex.business.service;

import com.dex.business.service.blockchain.UniV3PoolService;
import com.dex.data.entity.Price;
import com.dex.infrastructure.blockchain.univ3.UniV3PoolSummary;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsServiceTest {

    @Test
    void shouldBuildOpsOverviewWithAlertsAndMetrics() {
        PriceService priceService = mock(PriceService.class);
        UniV3PoolService uniV3PoolService = mock(UniV3PoolService.class);
        PrometheusMetrics prometheusMetrics = mock(PrometheusMetrics.class);

        long now = System.currentTimeMillis();
        when(priceService.getSupportedPairs()).thenReturn(List.of("ETH-USDC"));
        when(priceService.getLatestPrice("ETH-USDC")).thenReturn(Optional.of(
                new Price(1L, "ETH-USDC", new BigDecimal("3200.1234"), now - 5_000, LocalDateTime.now())
        ));
        when(priceService.detectAnomalies("ETH-USDC", 1.0)).thenReturn(List.of(
                Map.of("changePct", "1.5000", "timestamp", now - 1_000)
        ));
        when(uniV3PoolService.getSummary()).thenReturn(UniV3PoolSummary.builder()
                .status("RUNNING")
                .latestBlock(1000L)
                .safeLatestBlock(995L)
                .latestCommittedBlock(900L)
                .syncLag(95L)
                .latestEventTime(now - 240_000)
                .totalEvents(120L)
                .poolName("UniV3 WETH/USDC")
                .poolAddress("0xpool")
                .eventCounts(Map.of("SWAP", 50L))
                .build());
        when(prometheusMetrics.snapshot()).thenReturn(Map.of(
                "sseSubscribers", 2L,
                "eventsProcessedTotal", 50L,
                "kafkaPublishedTotal", 10L,
                "kafkaConsumedTotal", 10L,
                "kafkaFailuresTotal", 0L,
                "replayTotal", 0L
        ));

        OpsService opsService = new OpsService(priceService, uniV3PoolService, prometheusMetrics);

        Map<String, Object> overview = opsService.getOverview();

        assertNotNull(overview.get("stream"));
        assertNotNull(overview.get("processing"));
        assertFalse(((List<?>) overview.get("prices")).isEmpty());
        assertFalse(((List<?>) overview.get("alerts")).isEmpty());
        assertEquals("/api/v1/ops/replay", ((Map<?, ?>) overview.get("architecture")).get("manualReplayPath"));
    }

    @Test
    void shouldRecordReplayJobAndReturnSuccess() {
        PriceService priceService = mock(PriceService.class);
        UniV3PoolService uniV3PoolService = mock(UniV3PoolService.class);
        PrometheusMetrics prometheusMetrics = mock(PrometheusMetrics.class);

        when(uniV3PoolService.replayFromBlock(900L, 920L, "repair gap")).thenReturn(Map.of(
                "status", "SUCCESS",
                "effectiveFromBlock", 900L,
                "effectiveToBlock", 995L
        ));
        when(uniV3PoolService.getSummary()).thenReturn(UniV3PoolSummary.builder()
                .status("RUNNING")
                .build());
        when(priceService.getSupportedPairs()).thenReturn(List.of());
        when(prometheusMetrics.snapshot()).thenReturn(Map.of());

        OpsService opsService = new OpsService(priceService, uniV3PoolService, prometheusMetrics);

        Map<String, Object> job = opsService.triggerReplay(900L, 920L, "repair gap");
        Map<String, Object> overview = opsService.getOverview();

        assertEquals("SUCCESS", job.get("status"));
        assertNotNull(job.get("result"));
        assertEquals(1, ((List<?>) overview.get("replayJobs")).size());
    }
}
