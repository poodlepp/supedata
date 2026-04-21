package com.dex.business.service;

import com.dex.data.entity.Price;
import com.dex.data.service.DataCacheService;
import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 真实统计服务：只返回当前能由真实池状态可靠计算出来的指标。
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private static final String DASHBOARD_CACHE_KEY = "dex:stats:overview:v1";
    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofSeconds(45);

    private final LiquidityService liquidityService;
    private final PriceService priceService;
    private final StageStatusService stageStatusService;
    private final DataCacheService dataCacheService;

    public Map<String, Object> getOverview() {
        return dataCacheService.get(DASHBOARD_CACHE_KEY, Map.class)
                .map(this::normalizeOverview)
                .orElseGet(this::refreshOverview);
    }

    public Map<String, Object> refreshOverview() {
        List<RealPoolSnapshot> pools = liquidityService.getAllRealPools();
        List<Map<String, Object>> prices = priceService.getSupportedPairs().stream()
                .map(priceService::getLatestPrice)
                .flatMap(Optional::stream)
                .map(this::toPriceItem)
                .toList();
        BigDecimal totalTvlUsd = pools.stream()
                .map(this::estimatePoolTvlUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        List<String> supportedPairs = pools.stream().map(this::displayPair).toList();
        long latestBlockNumber = pools.stream()
                .map(RealPoolSnapshot::getBlockNumber)
                .filter(block -> block != null)
                .max(Long::compareTo)
                .orElse(0L);
        long generatedAt = System.currentTimeMillis();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("market", marketSummary(pools, prices.size(), totalTvlUsd, latestBlockNumber, generatedAt));
        overview.put("poolCount", pools.size());
        overview.put("supportedPairs", supportedPairs);
        overview.put("totalTvlUsdEstimate", totalTvlUsd);
        overview.put("prices", prices);
        overview.put("topPools", pools.stream()
                .sorted((left, right) -> estimatePoolTvlUsd(right).compareTo(estimatePoolTvlUsd(left)))
                .map(pool -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("poolAddress", pool.getPoolAddress());
            item.put("poolName", pool.getPoolName());
            item.put("pair", displayPair(pool));
            item.put("dex", pool.getDex());
            item.put("fee", pool.getFee());
            item.put("reserve0", pool.getReserve0());
            item.put("reserve1", pool.getReserve1());
            item.put("priceToken0InToken1", pool.getPriceToken0InToken1());
            item.put("priceToken1InToken0", pool.getPriceToken1InToken0());
            item.put("blockNumber", pool.getBlockNumber());
            item.put("blockTimestamp", pool.getBlockTimestamp());
            item.put("source", pool.getSource());
            item.put("tvlUsdEstimate", estimatePoolTvlUsd(pool).setScale(2, RoundingMode.HALF_UP));
            return item;
        }).toList());
        overview.put("highlights", highlights(pools, prices.size(), latestBlockNumber, totalTvlUsd));
        overview.put("metricBoundary", metricBoundary(supportedPairs, generatedAt));
        overview.put("stageSummary", stageStatusService.getSummary());
        overview.put("generatedAt", generatedAt);
        overview.put("cache", Map.of(
                "key", DASHBOARD_CACHE_KEY,
                "ttlSeconds", DASHBOARD_CACHE_TTL.toSeconds(),
                "strategy", "redis-with-local-fallback"
        ));

        dataCacheService.put(DASHBOARD_CACHE_KEY, overview, DASHBOARD_CACHE_TTL);
        return overview;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeOverview(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    private Map<String, Object> marketSummary(List<RealPoolSnapshot> pools,
                                              int pricePairCount,
                                              BigDecimal totalTvlUsd,
                                              long latestBlockNumber,
                                              long generatedAt) {
        Map<String, Object> market = new LinkedHashMap<>();
        market.put("poolCount", pools.size());
        market.put("pricePairCount", pricePairCount);
        market.put("totalTvlUsdEstimate", totalTvlUsd);
        market.put("latestBlockNumber", latestBlockNumber);
        market.put("updatedAt", generatedAt);
        market.put("syncStatus", latestBlockNumber > 0 ? "LIVE" : "BOOTSTRAPPING");
        return market;
    }

    private List<Map<String, Object>> highlights(List<RealPoolSnapshot> pools,
                                                 int pricePairCount,
                                                 long latestBlockNumber,
                                                 BigDecimal totalTvlUsd) {
        RealPoolSnapshot topPool = pools.stream()
                .max((left, right) -> estimatePoolTvlUsd(left).compareTo(estimatePoolTvlUsd(right)))
                .orElse(null);

        return List.of(
                highlight(
                        "总 TVL",
                        "$" + totalTvlUsd.setScale(2, RoundingMode.HALF_UP),
                        "当前首页总览直接读取缓存快照，不在查询链路做重计算"
                ),
                highlight(
                        "最深流动性池",
                        topPool == null ? "-" : topPool.getPoolName(),
                        topPool == null ? "暂无池数据" : "估算 TVL $" + estimatePoolTvlUsd(topPool).setScale(2, RoundingMode.HALF_UP)
                ),
                highlight(
                        "报价覆盖",
                        pricePairCount + " 个真实交易对",
                        "当前报价覆盖由真实池状态和真实 Quoter 结果支持"
                ),
                highlight(
                        "同步块高",
                        latestBlockNumber <= 0 ? "-" : String.valueOf(latestBlockNumber),
                        latestBlockNumber <= 0 ? "当前还没有链上快照" : "总览卡片保留最新块高，便于展示数据新鲜度"
                )
        );
    }

    private Map<String, Object> highlight(String title, String value, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("value", value);
        item.put("description", description);
        return item;
    }

    private Map<String, Object> toPriceItem(Price price) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("pair", price.getPair());
        item.put("price", price.getPrice().setScale(4, RoundingMode.HALF_UP));
        item.put("timestamp", price.getTimestamp());
        item.put("source", "ethereum-mainnet-uniswap-v3");
        return item;
    }

    private Map<String, Object> metricBoundary(List<String> supportedPairs, long generatedAt) {
        Map<String, Object> boundary = new LinkedHashMap<>();
        boundary.put("stage", "阶段 3");
        boundary.put("message", "阶段 3 只保留当前可以由真实池状态和价格历史可靠推导的指标，不输出伪造 24h 成交量或排行榜。");
        boundary.put("included", List.of(
                "最新价格快照",
                "价格历史快照",
                "简单 K 线",
                "价格瞬时跳变提示",
                "池子列表与储备",
                "TVL 估算",
                "首页总览缓存快照",
                "最新块高与同步状态"
        ));
        boundary.put("excluded", List.of(
                Map.of("metric", "24h Volume / 交易笔数", "reason", "当前未建立基于真实 swap_event 的聚合链路"),
                Map.of("metric", "热门 Token / 涨跌幅排行", "reason", "当前支持交易对过少，且缺少完整时间窗口聚合"),
                Map.of("metric", "流动性深度分数 / 买卖价差近似值", "reason", "当前只有少量池快照，未建立统一盘口近似模型"),
                Map.of("metric", "池子活跃度排行", "reason", "当前没有独立的事件统计物化表")
        ));
        boundary.put("supportedPairs", supportedPairs);
        boundary.put("generatedAt", generatedAt);
        return boundary;
    }

    private String displayPair(RealPoolSnapshot pool) {
        if (isStable(pool.getToken0Symbol()) && !isStable(pool.getToken1Symbol())) {
            return pool.getToken1Symbol() + "/" + pool.getToken0Symbol();
        }
        return pool.getToken0Symbol() + "/" + pool.getToken1Symbol();
    }

    private BigDecimal estimatePoolTvlUsd(RealPoolSnapshot pool) {
        if (isStable(pool.getToken0Symbol())) {
            return pool.getReserve0().multiply(new BigDecimal("2"));
        }
        if (isStable(pool.getToken1Symbol())) {
            return pool.getReserve1().multiply(new BigDecimal("2"));
        }
        return BigDecimal.ZERO;
    }

    private boolean isStable(String symbol) {
        return "USDC".equals(symbol) || "DAI".equals(symbol) || "USDT".equals(symbol);
    }
}
