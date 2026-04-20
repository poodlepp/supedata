package com.dex.business.service;

import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import com.dex.infrastructure.blockchain.univ3.UniV3RealPoolService;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报价与路由服务：基于池图做逐层扩展搜索，返回多候选路径及评分。
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final MathContext MC = new MathContext(24, RoundingMode.HALF_UP);
    private static final int MAX_HOPS = 3;
    private static final int BEAM_WIDTH = 12;
    private static final int MAX_STATES_PER_TOKEN = 3;
    private static final int MAX_POOLS_PER_TOKEN = 6;
    private static final int MAX_SPLIT_CANDIDATES = 3;
    private static final int MAX_SPLIT_OPTIONS = 4;
    private static final List<BigDecimal> SPLIT_RATIOS = List.of(
            new BigDecimal("0.50"),
            new BigDecimal("0.25"),
            new BigDecimal("0.75")
    );

    /** 每跳固定 gas 成本（USD），再折算成目标币做净收益评分 */
    private static final BigDecimal GAS_PER_HOP_USD = new BigDecimal("1.20");
    /** 双路径拆单的额外 calldata / merge 成本（USD）。 */
    private static final BigDecimal SPLIT_EXTRA_GAS_USD = new BigDecimal("0.60");
    /** 价格冲击超过此阈值则标记为淘汰 */
    private static final BigDecimal PRICE_IMPACT_THRESHOLD = new BigDecimal("5.0");
    private static final long QUOTE_STALE_MS = Duration.ofMinutes(20).toMillis();
    private static final int GAS_BASE_UNITS = 95_000;
    private static final int GAS_PER_HOP_UNITS = 55_000;
    private static final int SPLIT_EXTRA_GAS_UNITS = 35_000;
    private static final Set<String> STABLE_TOKENS = Set.of("USDC", "USDT", "DAI");

    private final UniV3RealPoolService realPoolService;
    private final PrometheusMetrics prometheusMetrics;

    // ------------------------------------------------------------------ quote

    /**
     * 返回 source -> target 的最佳路径及全部候选。
     * 候选由逐层扩展搜索生成，最终按 gas 调整后的净输出排序。
     */
    public Map<String, Object> quote(String from, String to, BigDecimal amountIn) {
        long startedAt = System.nanoTime();
        String source = normalize(from);
        String target = normalize(to);
        BigDecimal safeAmount = amountIn == null || amountIn.signum() <= 0 ? BigDecimal.ONE : amountIn;

        RouteSearchResult searchResult = searchRoutes(source, target, safeAmount);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromToken", source);
        payload.put("toToken", target);
        payload.put("amountIn", safeAmount);
        payload.put("best", searchResult.bestSingle());
        payload.put("bestExecution", searchResult.bestExecution());
        payload.put("candidates", searchResult.candidates());
        payload.put("splitOptions", searchResult.splitOptions());
        payload.put("generatedAt", System.currentTimeMillis());
        payload.put("mode", "layered-beam-search-real-pools");
        payload.put("search", searchMetadata());
        recordRouteRequest("quote", source, target, searchResult, nanosToMillis(startedAt));
        return payload;
    }

    // ---------------------------------------------------------------- compare

    /**
     * 返回所有候选路径，并按是否可执行拆分为 ranked / eliminated 两组。
     */
    public Map<String, Object> compare(String from, String to, BigDecimal amountIn) {
        long startedAt = System.nanoTime();
        String source = normalize(from);
        String target = normalize(to);
        BigDecimal safeAmount = amountIn == null || amountIn.signum() <= 0 ? BigDecimal.ONE : amountIn;

        RouteSearchResult searchResult = searchRoutes(source, target, safeAmount);
        List<Map<String, Object>> viable = searchResult.candidates().stream()
                .filter(c -> Boolean.TRUE.equals(c.get("viable")))
                .sorted(Comparator.comparing((Map<String, Object> c) -> (BigDecimal) c.get("netScore")).reversed())
                .toList();
        List<Map<String, Object>> eliminated = searchResult.candidates().stream()
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
        payload.put("bestExecution", searchResult.bestExecution());
        payload.put("splitOptions", searchResult.splitOptions());
        payload.put("generatedAt", System.currentTimeMillis());
        payload.put("search", searchMetadata());
        recordRouteRequest("compare", source, target, searchResult, nanosToMillis(startedAt));
        return payload;
    }

    // --------------------------------------------------------- path building

    /**
     * 在池图上做逐层扩展搜索：
     * 1. 从 source 状态出发，逐层扩展到相邻 token；
     * 2. 每扩一跳都重新调用 quoter，边权随输入金额动态变化；
     * 3. 每层扩展后执行去重和 beam 剪枝；
     * 4. 命中 target 的状态收集为候选，最后统一计算净得分。
     */
    private RouteSearchResult searchRoutes(String source, String target, BigDecimal amountIn) {
        List<RealPoolSnapshot> pools = realPoolService.getSupportedPools();
        List<Map<String, Object>> candidates = new ArrayList<>();
        if (pools.isEmpty()) {
            candidates.add(unsupportedCandidate(source, target, "PAIR_NOT_SUPPORTED", "当前不支持该交易对"));
            return new RouteSearchResult(candidates, List.of(), null, null);
        }

        Map<String, List<RealPoolSnapshot>> adjacency = buildAdjacency(pools);
        Map<String, RealPoolSnapshot> poolIndex = pools.stream()
                .collect(Collectors.toMap(pool -> pool.getPoolAddress().toLowerCase(Locale.ROOT), pool -> pool, (left, right) -> left));
        Map<String, BigDecimal> priceMemo = new HashMap<>();
        BigDecimal targetUsdPrice = estimateUsdPrice(target, adjacency, priceMemo, new HashSet<>(), 0);

        List<SearchState> completed = new ArrayList<>();
        List<SearchState> frontier = List.of(SearchState.start(source, amountIn));
        Set<String> completedRouteKeys = new HashSet<>();

        for (int depth = 1; depth <= MAX_HOPS && !frontier.isEmpty(); depth++) {
            List<SearchState> nextLayer = new ArrayList<>();

            for (SearchState state : frontier) {
                for (RealPoolSnapshot pool : adjacency.getOrDefault(state.currentToken(), List.of())) {
                    String nextToken = otherToken(pool, state.currentToken());
                    if (nextToken == null || state.visitedTokens().contains(nextToken)) {
                        continue;
                    }

                    Map<String, Object> hopQuote = realPoolService.quoteExactInputSingle(
                            pool, state.currentToken(), nextToken, state.amountOut());
                    if (hopQuote == null || !Boolean.TRUE.equals(hopQuote.get("supported"))) {
                        continue;
                    }

                    SearchState expanded = state.extend(pool, nextToken, hopQuote, GAS_PER_HOP_USD,
                            estimateUsdPrice(nextToken, adjacency, priceMemo, new HashSet<>(), 0));

                    if (target.equals(nextToken)) {
                        if (completedRouteKeys.add(expanded.routeKey())) {
                            completed.add(expanded);
                        }
                    } else if (depth < MAX_HOPS) {
                        nextLayer.add(expanded);
                    }
                }
            }

            frontier = pruneLayer(nextLayer);
        }

        candidates = completed.stream()
                .map(state -> toCandidate(state, targetUsdPrice))
                .sorted(Comparator.comparing((Map<String, Object> c) -> (BigDecimal) c.get("netScore")).reversed())
                .toList();

        if (candidates.isEmpty()) {
            String message = adjacency.containsKey(source)
                    ? "没有在 " + MAX_HOPS + " 跳内找到可执行路径"
                    : "源 token 没有可用池子";
            candidates = List.of(unsupportedCandidate(source, target, "PAIR_NOT_SUPPORTED", message));
        }

        Map<String, Object> bestSingle = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("viable")))
                .max(Comparator.comparing(c -> (BigDecimal) c.get("netScore")))
                .orElse(null);
        List<Map<String, Object>> splitOptions = buildSplitOptions(source, amountIn, candidates, poolIndex, adjacency, priceMemo, targetUsdPrice, bestSingle);
        Map<String, Object> bestExecution = chooseBestExecution(bestSingle, splitOptions);
        return new RouteSearchResult(candidates, splitOptions, bestSingle, bestExecution);
    }

    /**
     * 逐层扩展后，对每一层状态做两级裁剪：
     * 1. 精确路由去重：同一组 poolAddress 只保留最优状态；
     * 2. 支配裁剪：同一 token 的中间状态只保留少量高分候选，避免图爆炸。
     */
    private List<SearchState> pruneLayer(List<SearchState> states) {
        if (states.isEmpty()) {
            return List.of();
        }

        Map<String, SearchState> bestByRoute = new LinkedHashMap<>();
        for (SearchState state : states) {
            bestByRoute.merge(state.routeKey(), state, this::betterState);
        }

        Map<String, List<SearchState>> grouped = bestByRoute.values().stream()
                .collect(Collectors.groupingBy(SearchState::currentToken));

        List<SearchState> limitedPerToken = grouped.values().stream()
                .flatMap(group -> group.stream()
                        .sorted(Comparator.comparing(SearchState::heuristicScoreUsd).reversed())
                        .limit(MAX_STATES_PER_TOKEN))
                .sorted(Comparator.comparing(SearchState::heuristicScoreUsd).reversed())
                .limit(BEAM_WIDTH)
                .toList();
        return limitedPerToken;
    }

    private SearchState betterState(SearchState left, SearchState right) {
        return left.heuristicScoreUsd().compareTo(right.heuristicScoreUsd()) >= 0 ? left : right;
    }

    private void recordRouteRequest(String operation,
                                    String source,
                                    String target,
                                    RouteSearchResult searchResult,
                                    long durationMs) {
        Map<String, Object> bestExecution = searchResult.bestExecution();
        String result = bestExecution != null && Boolean.TRUE.equals(bestExecution.get("viable")) ? "success" : "no_route";
        String pair = displayToken(source) + "-" + displayToken(target);
        String executionType = bestExecution == null ? "NONE" : bestExecution.getOrDefault("executionType", "UNKNOWN").toString();
        int hopCount = bestExecution == null ? 0 : ((Number) bestExecution.getOrDefault("hopCount", 0)).intValue();
        int candidateCount = searchResult.candidates().size();
        long freshnessMs = bestExecution == null ? 0L : ((Number) bestExecution.getOrDefault("quoteFreshnessMs", 0L)).longValue();
        BigDecimal priceImpactPct = bestExecution == null ? BigDecimal.ZERO : (BigDecimal) bestExecution.getOrDefault("priceImpactPct", BigDecimal.ZERO);
        BigDecimal netScore = bestExecution == null ? BigDecimal.ZERO : (BigDecimal) bestExecution.getOrDefault("netScore", BigDecimal.ZERO);
        prometheusMetrics.recordRouteRequest(operation, pair, result, executionType, hopCount, candidateCount,
                freshnessMs, priceImpactPct, netScore, durationMs);
    }

    private long nanosToMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String displayToken(String token) {
        return "WETH".equals(token) ? "ETH" : token;
    }

    /**
     * 构建 token -> pools 的邻接表。
     * 每个 token 只保留流动性更好的少量邻接池，减少后续搜索分支数。
     */
    private Map<String, List<RealPoolSnapshot>> buildAdjacency(List<RealPoolSnapshot> pools) {
        Map<String, List<RealPoolSnapshot>> adjacency = new HashMap<>();
        for (RealPoolSnapshot pool : pools) {
            adjacency.computeIfAbsent(pool.getToken0Symbol(), ignored -> new ArrayList<>()).add(pool);
            adjacency.computeIfAbsent(pool.getToken1Symbol(), ignored -> new ArrayList<>()).add(pool);
        }

        adjacency.replaceAll((token, list) -> list.stream()
                .sorted(Comparator
                        .comparing(this::poolLiquidityScore).reversed()
                        .thenComparing(pool -> pool.getFee() == null ? Integer.MAX_VALUE : pool.getFee()))
                .limit(MAX_POOLS_PER_TOKEN)
                .toList());
        return adjacency;
    }

    /**
     * 把搜索状态转换成对外返回的候选结构。
     * 最终得分使用输出币单位的 netScore，便于直接比较不同跳数路径。
     */
    private Map<String, Object> toCandidate(SearchState state, BigDecimal targetUsdPrice) {
        long now = System.currentTimeMillis();
        BigDecimal gasCostInOutputToken = toOutputTokenGas(state.gasCostUsd(), targetUsdPrice);
        BigDecimal netScore = state.amountOut().subtract(gasCostInOutputToken).setScale(8, RoundingMode.HALF_UP);
        BigDecimal priceImpact = priceImpactPct(state.theoreticalAmountOut(), state.amountOut());
        BigDecimal lpFeeCostInOutputToken = toOutputTokenGas(state.lpFeeCostUsd(), targetUsdPrice);
        BigDecimal priceImpactLossInOutputToken = toOutputTokenGas(state.priceImpactLossUsd(), targetUsdPrice);
        Map<String, Object> lastQuote = state.hops().getLast();
        long quoteAgeMs = quoteAgeMs(lastQuote, now);
        boolean stale = quoteAgeMs > QUOTE_STALE_MS;
        BigDecimal liquidityDepthScore = liquidityDepthScore(state);
        BigDecimal stabilityScore = stabilityScore(state.hopCount(), priceImpact, liquidityDepthScore, quoteAgeMs, state.pathDiversityBonus());
        boolean viable = priceImpact.compareTo(PRICE_IMPACT_THRESHOLD) < 0 && netScore.signum() > 0 && !stale;

        String eliminationReason = null;
        if (!viable) {
            List<String> reasons = new ArrayList<>();
            if (priceImpact.compareTo(PRICE_IMPACT_THRESHOLD) >= 0) {
                reasons.add("价格冲击 " + priceImpact.setScale(2, RoundingMode.HALF_UP) + "% 超过阈值");
            }
            if (netScore.signum() <= 0) {
                reasons.add("gas 调整后净输出小于等于 0");
            }
            if (stale) {
                reasons.add("报价快照已过期");
            }
            eliminationReason = String.join("; ", reasons);
        }

        RealPoolSnapshot lastPool = state.pools().getLast();
        int gasAmount = estimatedGasUnits(state.hopCount());
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("executionType", "SINGLE");
        c.put("path", state.tokenPath());
        c.put("type", routeType(state));
        c.put("label", String.join(" -> ", state.tokenPath()));
        c.put("viable", viable);
        c.put("eliminationReason", eliminationReason);
        c.put("poolAddresses", state.poolAddresses());
        c.put("poolName", state.hopCount() == 1 ? lastPool.getPoolName() : null);
        c.put("fee", state.hopCount() == 1 ? lastPool.getFee() : null);
        c.put("hopCount", state.hopCount());
        c.put("amountOut", state.amountOut().setScale(8, RoundingMode.HALF_UP));
        c.put("grossAmountOut", state.theoreticalAmountOut().setScale(8, RoundingMode.HALF_UP));
        c.put("netAmountOut", netScore);
        c.put("lpFeeCostUsd", state.lpFeeCostUsd().setScale(8, RoundingMode.HALF_UP));
        c.put("lpFeeCostInOutToken", lpFeeCostInOutputToken.setScale(8, RoundingMode.HALF_UP));
        c.put("priceImpactLossUsd", state.priceImpactLossUsd().setScale(8, RoundingMode.HALF_UP));
        c.put("priceImpactLossInOutToken", priceImpactLossInOutputToken.setScale(8, RoundingMode.HALF_UP));
        c.put("gasAmount", gasAmount);
        c.put("gasCostUsd", state.gasCostUsd().setScale(8, RoundingMode.HALF_UP));
        c.put("gasCostInOutToken", gasCostInOutputToken.setScale(8, RoundingMode.HALF_UP));
        c.put("priceImpactPct", priceImpact);
        c.put("netScore", netScore);
        c.put("netScoreUsd", state.heuristicScoreUsd().setScale(8, RoundingMode.HALF_UP));
        c.put("liquidityDepthScore", liquidityDepthScore);
        c.put("stabilityScore", stabilityScore);
        c.put("quoteFreshnessMs", quoteAgeMs);
        c.put("quoteFreshness", freshnessLabel(quoteAgeMs));
        c.put("blockNumber", lastQuote.get("blockNumber"));
        c.put("blockTimestamp", lastQuote.get("blockTimestamp"));
        c.put("source", "layered-search/" + lastQuote.get("source"));
        c.put("dex", state.dexLabel());
        c.put("scoreBreakdown", Map.of(
                "grossAmountOut", state.theoreticalAmountOut().setScale(8, RoundingMode.HALF_UP),
                "lpFeeCostInOutToken", lpFeeCostInOutputToken.setScale(8, RoundingMode.HALF_UP),
                "priceImpactLossInOutToken", priceImpactLossInOutputToken.setScale(8, RoundingMode.HALF_UP),
                "gasCostInOutToken", gasCostInOutputToken.setScale(8, RoundingMode.HALF_UP),
                "netAmountOut", netScore,
                "hopPenalty", new BigDecimal(state.hopCount() - 1).max(BigDecimal.ZERO)
        ));
        c.put("hops", state.hops());
        return c;
    }

    private Map<String, Object> unsupportedCandidate(String source, String target,
                                                      String reasonCode, String reason) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("executionType", "SINGLE");
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
        c.put("netAmountOut", BigDecimal.ZERO);
        c.put("lpFeeCostUsd", BigDecimal.ZERO);
        c.put("lpFeeCostInOutToken", BigDecimal.ZERO);
        c.put("priceImpactLossUsd", BigDecimal.ZERO);
        c.put("priceImpactLossInOutToken", BigDecimal.ZERO);
        c.put("gasAmount", 0);
        c.put("gasCostUsd", BigDecimal.ZERO);
        c.put("gasCostInOutToken", BigDecimal.ZERO);
        c.put("priceImpactPct", BigDecimal.ZERO);
        c.put("netScore", BigDecimal.ZERO);
        c.put("liquidityDepthScore", BigDecimal.ZERO);
        c.put("stabilityScore", BigDecimal.ZERO);
        c.put("quoteFreshnessMs", 0L);
        c.put("quoteFreshness", "unknown");
        c.put("source", "unsupported");
        return c;
    }

    /**
     * 基于最优单路径候选，额外模拟少量双路径拆单方案。
     * 这里不追求复杂求解器，只覆盖最常见的 25/75、50/50、75/25 对比。
     */
    private List<Map<String, Object>> buildSplitOptions(String source,
                                                        BigDecimal amountIn,
                                                        List<Map<String, Object>> candidates,
                                                        Map<String, RealPoolSnapshot> poolIndex,
                                                        Map<String, List<RealPoolSnapshot>> adjacency,
                                                        Map<String, BigDecimal> priceMemo,
                                                        BigDecimal targetUsdPrice,
                                                        Map<String, Object> bestSingle) {
        List<Map<String, Object>> viable = candidates.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("viable")))
                .sorted(Comparator.comparing((Map<String, Object> c) -> (BigDecimal) c.get("netScore")).reversed())
                .limit(MAX_SPLIT_CANDIDATES)
                .toList();
        if (viable.size() < 2) {
            return List.of();
        }

        BigDecimal bestSingleScore = bestSingle == null
                ? BigDecimal.ZERO
                : (BigDecimal) bestSingle.getOrDefault("netScore", BigDecimal.ZERO);
        List<Map<String, Object>> splitOptions = new ArrayList<>();

        for (int i = 0; i < viable.size(); i++) {
            for (int j = i + 1; j < viable.size(); j++) {
                Map<String, Object> left = viable.get(i);
                Map<String, Object> right = viable.get(j);
                if (sameRoute(left, right)) {
                    continue;
                }

                for (BigDecimal leftRatio : SPLIT_RATIOS) {
                    BigDecimal rightRatio = BigDecimal.ONE.subtract(leftRatio);
                    if (rightRatio.signum() <= 0) {
                        continue;
                    }

                    SearchState leftState = replayCandidate(source, left,
                            amountIn.multiply(leftRatio, MC), poolIndex, adjacency, priceMemo);
                    SearchState rightState = replayCandidate(source, right,
                            amountIn.multiply(rightRatio, MC), poolIndex, adjacency, priceMemo);
                    if (leftState == null || rightState == null) {
                        continue;
                    }

                    Map<String, Object> leftLeg = toCandidate(leftState, targetUsdPrice);
                    Map<String, Object> rightLeg = toCandidate(rightState, targetUsdPrice);
                    splitOptions.add(toSplitOption(
                            leftLeg, rightLeg, leftRatio, rightRatio, targetUsdPrice, bestSingleScore
                    ));
                }
            }
        }

        return splitOptions.stream()
                .sorted(Comparator.comparing((Map<String, Object> c) -> (BigDecimal) c.get("netScore")).reversed())
                .limit(MAX_SPLIT_OPTIONS)
                .toList();
    }

    private SearchState replayCandidate(String source,
                                        Map<String, Object> candidate,
                                        BigDecimal amountIn,
                                        Map<String, RealPoolSnapshot> poolIndex,
                                        Map<String, List<RealPoolSnapshot>> adjacency,
                                        Map<String, BigDecimal> priceMemo) {
        @SuppressWarnings("unchecked")
        List<String> tokenPath = ((List<?>) candidate.getOrDefault("path", List.of())).stream()
                .map(Object::toString)
                .toList();
        @SuppressWarnings("unchecked")
        List<String> poolAddresses = ((List<?>) candidate.getOrDefault("poolAddresses", List.of())).stream()
                .map(Object::toString)
                .toList();
        if (tokenPath.size() < 2 || poolAddresses.isEmpty() || tokenPath.size() != poolAddresses.size() + 1) {
            return null;
        }

        SearchState state = SearchState.start(source, amountIn);
        for (int i = 0; i < poolAddresses.size(); i++) {
            RealPoolSnapshot pool = poolIndex.get(poolAddresses.get(i).toLowerCase(Locale.ROOT));
            if (pool == null) {
                return null;
            }
            String tokenIn = tokenPath.get(i);
            String tokenOut = tokenPath.get(i + 1);
            Map<String, Object> hopQuote = realPoolService.quoteExactInputSingle(pool, tokenIn, tokenOut, state.amountOut());
            if (hopQuote == null || !Boolean.TRUE.equals(hopQuote.get("supported"))) {
                return null;
            }
            BigDecimal tokenUsdPrice = estimateUsdPrice(tokenOut, adjacency, priceMemo, new HashSet<>(), 0);
            state = state.extend(pool, tokenOut, hopQuote, GAS_PER_HOP_USD, tokenUsdPrice);
        }
        return state;
    }

    private Map<String, Object> toSplitOption(Map<String, Object> leftLeg,
                                              Map<String, Object> rightLeg,
                                              BigDecimal leftRatio,
                                              BigDecimal rightRatio,
                                              BigDecimal targetUsdPrice,
                                              BigDecimal bestSingleScore) {
        BigDecimal grossAmountOut = ((BigDecimal) leftLeg.get("grossAmountOut"))
                .add((BigDecimal) rightLeg.get("grossAmountOut"));
        BigDecimal amountOut = ((BigDecimal) leftLeg.get("amountOut"))
                .add((BigDecimal) rightLeg.get("amountOut"));
        BigDecimal lpFeeCostUsd = ((BigDecimal) leftLeg.get("lpFeeCostUsd"))
                .add((BigDecimal) rightLeg.get("lpFeeCostUsd"));
        BigDecimal priceImpactLossUsd = ((BigDecimal) leftLeg.get("priceImpactLossUsd"))
                .add((BigDecimal) rightLeg.get("priceImpactLossUsd"));
        BigDecimal gasCostUsd = ((BigDecimal) leftLeg.get("gasCostUsd"))
                .add((BigDecimal) rightLeg.get("gasCostUsd"))
                .add(SPLIT_EXTRA_GAS_USD);
        BigDecimal gasCostInOutputToken = toOutputTokenGas(gasCostUsd, targetUsdPrice);
        BigDecimal netScore = amountOut.subtract(gasCostInOutputToken).setScale(8, RoundingMode.HALF_UP);
        BigDecimal priceImpact = priceImpactPct(grossAmountOut, amountOut);
        BigDecimal liquidityDepthScore = (((BigDecimal) leftLeg.get("liquidityDepthScore"))
                .add((BigDecimal) rightLeg.get("liquidityDepthScore")))
                .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP);
        BigDecimal stabilityScore = ((((BigDecimal) leftLeg.get("stabilityScore"))
                .add((BigDecimal) rightLeg.get("stabilityScore")))
                .divide(new BigDecimal("2"), 8, RoundingMode.HALF_UP))
                .subtract(new BigDecimal("4.0"))
                .max(BigDecimal.ZERO);
        long quoteFreshnessMs = Math.max(
                ((Number) leftLeg.getOrDefault("quoteFreshnessMs", 0L)).longValue(),
                ((Number) rightLeg.getOrDefault("quoteFreshnessMs", 0L)).longValue()
        );
        int gasAmount = ((Number) leftLeg.getOrDefault("gasAmount", 0)).intValue()
                + ((Number) rightLeg.getOrDefault("gasAmount", 0)).intValue()
                + SPLIT_EXTRA_GAS_UNITS;

        List<Map<String, Object>> legs = List.of(
                splitLeg(leftLeg, leftRatio),
                splitLeg(rightLeg, rightRatio)
        );

        Map<String, Object> split = new LinkedHashMap<>();
        split.put("executionType", "SPLIT");
        split.put("type", "SPLIT_2WAY");
        split.put("label", "Split " + formatPct(leftRatio) + " / " + formatPct(rightRatio));
        split.put("path", legs.stream().map(leg -> leg.get("path")).toList());
        boolean stale = quoteFreshnessMs > QUOTE_STALE_MS;
        split.put("viable", priceImpact.compareTo(PRICE_IMPACT_THRESHOLD) < 0 && netScore.signum() > 0 && !stale);
        split.put("eliminationReason", priceImpact.compareTo(PRICE_IMPACT_THRESHOLD) < 0 && netScore.signum() > 0 && !stale
                ? null : (stale ? "拆单后报价快照已过期" : "拆单后价格冲击或净输出不满足阈值"));
        split.put("amountOut", amountOut.setScale(8, RoundingMode.HALF_UP));
        split.put("grossAmountOut", grossAmountOut.setScale(8, RoundingMode.HALF_UP));
        split.put("netAmountOut", netScore);
        split.put("lpFeeCostUsd", lpFeeCostUsd.setScale(8, RoundingMode.HALF_UP));
        split.put("priceImpactLossUsd", priceImpactLossUsd.setScale(8, RoundingMode.HALF_UP));
        split.put("gasCostUsd", gasCostUsd.setScale(8, RoundingMode.HALF_UP));
        split.put("gasAmount", gasAmount);
        split.put("gasCostInOutToken", gasCostInOutputToken.setScale(8, RoundingMode.HALF_UP));
        split.put("priceImpactPct", priceImpact);
        split.put("netScore", netScore);
        split.put("liquidityDepthScore", liquidityDepthScore.setScale(4, RoundingMode.HALF_UP));
        split.put("stabilityScore", stabilityScore.setScale(4, RoundingMode.HALF_UP));
        split.put("quoteFreshnessMs", quoteFreshnessMs);
        split.put("quoteFreshness", freshnessLabel(quoteFreshnessMs));
        split.put("betterThanBestSingle", netScore.compareTo(bestSingleScore) > 0);
        split.put("improvementVsBestSingle", netScore.subtract(bestSingleScore).setScale(8, RoundingMode.HALF_UP));
        split.put("legs", legs);
        split.put("scoreBreakdown", Map.of(
                "grossAmountOut", grossAmountOut.setScale(8, RoundingMode.HALF_UP),
                "lpFeeCostInOutToken", toOutputTokenGas(lpFeeCostUsd, targetUsdPrice).setScale(8, RoundingMode.HALF_UP),
                "priceImpactLossInOutToken", toOutputTokenGas(priceImpactLossUsd, targetUsdPrice).setScale(8, RoundingMode.HALF_UP),
                "gasCostInOutToken", gasCostInOutputToken.setScale(8, RoundingMode.HALF_UP),
                "netAmountOut", netScore
        ));
        return split;
    }

    private Map<String, Object> splitLeg(Map<String, Object> leg, BigDecimal ratio) {
        Map<String, Object> item = new LinkedHashMap<>(leg);
        item.put("allocationPct", ratio.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
        return item;
    }

    private Map<String, Object> chooseBestExecution(Map<String, Object> bestSingle, List<Map<String, Object>> splitOptions) {
        Map<String, Object> bestSplit = splitOptions.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("viable")))
                .max(Comparator.comparing(c -> (BigDecimal) c.get("netScore")))
                .orElse(null);
        if (bestSingle == null) {
            return bestSplit;
        }
        if (bestSplit == null) {
            return bestSingle;
        }
        BigDecimal splitScore = (BigDecimal) bestSplit.get("netScore");
        BigDecimal singleScore = (BigDecimal) bestSingle.get("netScore");
        return splitScore.compareTo(singleScore) > 0 ? bestSplit : bestSingle;
    }

    private boolean sameRoute(Map<String, Object> left, Map<String, Object> right) {
        @SuppressWarnings("unchecked")
        List<String> leftPools = (List<String>) left.getOrDefault("poolAddresses", List.of());
        @SuppressWarnings("unchecked")
        List<String> rightPools = (List<String>) right.getOrDefault("poolAddresses", List.of());
        return leftPools.equals(rightPools);
    }

    private String formatPct(BigDecimal ratio) {
        return ratio.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP) + "%";
    }

    /**
     * 构造统一的搜索参数元信息，便于前端或调试查看当前剪枝配置。
     */
    private Map<String, Object> searchMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("maxHops", MAX_HOPS);
        meta.put("beamWidth", BEAM_WIDTH);
        meta.put("statesPerToken", MAX_STATES_PER_TOKEN);
        meta.put("poolsPerToken", MAX_POOLS_PER_TOKEN);
        meta.put("splitRatios", SPLIT_RATIOS);
        meta.put("splitCandidates", MAX_SPLIT_CANDIDATES);
        return meta;
    }

    /**
     * 将 gas USD 成本折算为输出 token 数量，避免直接拿 token 数量减 USD。
     */
    private BigDecimal toOutputTokenGas(BigDecimal gasUsd, BigDecimal outputTokenUsdPrice) {
        if (gasUsd == null || gasUsd.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (outputTokenUsdPrice == null || outputTokenUsdPrice.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return gasUsd.divide(outputTokenUsdPrice, 18, RoundingMode.HALF_UP);
    }

    /**
     * 递归估算 token 的 USD 价格。
     * 稳定币直接视为 1 USD，其他 token 则通过高流动性邻接池的 spot price 递推估算。
     */
    private BigDecimal estimateUsdPrice(String token,
                                        Map<String, List<RealPoolSnapshot>> adjacency,
                                        Map<String, BigDecimal> memo,
                                        Set<String> visiting,
                                        int depth) {
        String normalized = normalize(token);
        if (normalized.isEmpty()) {
            return null;
        }
        if (STABLE_TOKENS.contains(normalized)) {
            return BigDecimal.ONE;
        }
        if (memo.containsKey(normalized)) {
            return memo.get(normalized);
        }
        if (depth >= MAX_HOPS || !visiting.add(normalized)) {
            return null;
        }

        BigDecimal best = null;
        BigDecimal bestLiquidity = BigDecimal.ZERO;
        for (RealPoolSnapshot pool : adjacency.getOrDefault(normalized, List.of())) {
            String nextToken = otherToken(pool, normalized);
            if (nextToken == null) {
                continue;
            }
            BigDecimal edgePrice = spotPrice(pool, normalized, nextToken);
            BigDecimal nextUsd = estimateUsdPrice(nextToken, adjacency, memo, visiting, depth + 1);
            if (edgePrice == null || nextUsd == null) {
                continue;
            }
            BigDecimal candidate = edgePrice.multiply(nextUsd, MC);
            BigDecimal liquidityScore = poolLiquidityScore(pool);
            if (best == null || liquidityScore.compareTo(bestLiquidity) > 0) {
                best = candidate;
                bestLiquidity = liquidityScore;
            }
        }

        visiting.remove(normalized);
        if (best != null) {
            memo.put(normalized, best);
        }
        return best;
    }

    /**
     * 读取单个 pool 的 spot 价格方向。
     */
    private BigDecimal spotPrice(RealPoolSnapshot pool, String fromToken, String toToken) {
        String from = normalize(fromToken);
        String to = normalize(toToken);
        if (pool.getToken0Symbol().equals(from) && pool.getToken1Symbol().equals(to)) {
            return pool.getPriceToken0InToken1();
        }
        if (pool.getToken0Symbol().equals(to) && pool.getToken1Symbol().equals(from)) {
            return pool.getPriceToken1InToken0();
        }
        return null;
    }

    /**
     * 用于邻接边排序的流动性评分。
     * 优先使用原始 liquidity，缺失时回退到 reserve 和。
     */
    private BigDecimal poolLiquidityScore(RealPoolSnapshot pool) {
        if (pool.getLiquidity() != null && pool.getLiquidity().signum() > 0) {
            return pool.getLiquidity();
        }
        BigDecimal reserve0 = pool.getReserve0() == null ? BigDecimal.ZERO : pool.getReserve0().abs();
        BigDecimal reserve1 = pool.getReserve1() == null ? BigDecimal.ZERO : pool.getReserve1().abs();
        return reserve0.add(reserve1);
    }

    private BigDecimal liquidityDepthScore(SearchState state) {
        BigDecimal minLiquidity = state.pools().stream()
                .map(this::poolLiquidityScore)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        if (minLiquidity.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        int digits = minLiquidity.precision() - minLiquidity.scale();
        return BigDecimal.valueOf(Math.min(100, Math.max(0, digits * 8.0))).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal stabilityScore(int hopCount,
                                      BigDecimal priceImpactPct,
                                      BigDecimal liquidityDepthScore,
                                      long quoteAgeMs,
                                      BigDecimal pathDiversityBonus) {
        BigDecimal freshnessPenalty = BigDecimal.valueOf(Math.min(30.0, quoteAgeMs / 60_000.0));
        BigDecimal score = new BigDecimal("92")
                .subtract(BigDecimal.valueOf(Math.max(0, hopCount - 1) * 12L))
                .subtract(priceImpactPct.multiply(new BigDecimal("2.2"), MC))
                .subtract(freshnessPenalty)
                .add(liquidityDepthScore.multiply(new BigDecimal("0.18"), MC))
                .add(pathDiversityBonus);
        if (score.signum() < 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return score.min(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
    }

    private int estimatedGasUnits(int hopCount) {
        return GAS_BASE_UNITS + (hopCount * GAS_PER_HOP_UNITS);
    }

    private long quoteAgeMs(Map<String, Object> lastQuote, long now) {
        Object ts = lastQuote == null ? null : lastQuote.get("blockTimestamp");
        if (!(ts instanceof Number number)) {
            return 0L;
        }
        return Math.max(0L, now - number.longValue());
    }

    private String freshnessLabel(long quoteAgeMs) {
        if (quoteAgeMs <= 60_000L) {
            return "fresh";
        }
        if (quoteAgeMs <= 5 * 60_000L) {
            return "recent";
        }
        if (quoteAgeMs <= QUOTE_STALE_MS) {
            return "aging";
        }
        return "stale";
    }

    private static BigDecimal feeFraction(RealPoolSnapshot pool) {
        if (pool == null || pool.getFee() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(pool.getFee())
                .divide(new BigDecimal("1000000"), 12, RoundingMode.HALF_UP);
    }

    /**
     * 用整条路径的理论输出和实际输出计算累计价格冲击。
     */
    private BigDecimal priceImpactPct(BigDecimal theoreticalAmountOut, BigDecimal actualAmountOut) {
        if (theoreticalAmountOut == null || theoreticalAmountOut.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return theoreticalAmountOut.subtract(actualAmountOut)
                .divide(theoreticalAmountOut, 8, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 给定当前 token 和 pool，返回该 pool 的另一侧 token。
     */
    private String otherToken(RealPoolSnapshot pool, String token) {
        String normalized = normalize(token);
        if (pool.getToken0Symbol().equals(normalized)) {
            return pool.getToken1Symbol();
        }
        if (pool.getToken1Symbol().equals(normalized)) {
            return pool.getToken0Symbol();
        }
        return null;
    }

    /**
     * 为候选路径生成简单类型标识，便于前端展示和日志排查。
     */
    private String routeType(SearchState state) {
        if (state.hopCount() == 1) {
            Integer fee = state.pools().getFirst().getFee();
            return "DIRECT_" + fee;
        }
        List<String> mids = state.tokenPath().subList(1, state.tokenPath().size() - 1);
        return "MULTI_HOP_VIA_" + String.join("_", mids);
    }

    private String normalize(String token) {
        if (token == null) return "";
        String v = token.trim().toUpperCase(Locale.ROOT);
        return v.equals("ETH") ? "WETH" : v;
    }

    /**
     * 搜索过程中的中间状态。
     * 记录当前位置、当前输出、累计 gas、已走路径和启发式分数。
     */
    private record SearchState(
            List<String> tokenPath,
            List<RealPoolSnapshot> pools,
            List<Map<String, Object>> hops,
            Set<String> visitedTokens,
            String currentToken,
            BigDecimal amountOut,
            BigDecimal theoreticalAmountOut,
            BigDecimal gasCostUsd,
            BigDecimal heuristicScoreUsd,
            BigDecimal lpFeeCostUsd,
            BigDecimal priceImpactLossUsd
    ) {
        /**
         * 初始化搜索起点状态。
         */
        private static SearchState start(String source, BigDecimal amountIn) {
            BigDecimal safeAmount = amountIn == null || amountIn.signum() <= 0 ? BigDecimal.ONE : amountIn;
            Set<String> visited = new HashSet<>();
            visited.add(source);
            return new SearchState(
                    List.of(source),
                    List.of(),
                    List.of(),
                    Set.copyOf(visited),
                    source,
                    safeAmount,
                    safeAmount,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        /**
         * 基于一跳新报价扩展下一层状态。
         * 这里会更新当前输出、理论输出、gas 成本和启发式 USD 分数。
         */
        private SearchState extend(RealPoolSnapshot pool,
                                   String nextToken,
                                   Map<String, Object> hopQuote,
                                   BigDecimal hopGasUsd,
                                   BigDecimal nextTokenUsdPrice) {
            BigDecimal nextAmountOut = ((BigDecimal) hopQuote.get("amountOut")).setScale(8, RoundingMode.HALF_UP);
            BigDecimal nextGasUsd = gasCostUsd.add(hopGasUsd);

            BigDecimal nextTheoreticalAmount = theoreticalAmountOut;
            BigDecimal hopGrossAmountOut = hopQuote.get("grossAmountOut") instanceof BigDecimal bd ? bd : null;
            if (hopGrossAmountOut != null && amountOut.signum() > 0) {
                BigDecimal spotRate = hopGrossAmountOut.divide(amountOut, 18, RoundingMode.HALF_UP);
                nextTheoreticalAmount = theoreticalAmountOut.multiply(spotRate, MC).setScale(8, RoundingMode.HALF_UP);
            } else {
                nextTheoreticalAmount = nextAmountOut;
            }
            BigDecimal hopLpFeeToken = hopGrossAmountOut == null
                    ? BigDecimal.ZERO
                    : hopGrossAmountOut.multiply(feeFraction(pool), MC);
            BigDecimal hopPriceImpactLossToken = hopGrossAmountOut == null
                    ? BigDecimal.ZERO
                    : hopGrossAmountOut.subtract(nextAmountOut).subtract(hopLpFeeToken).max(BigDecimal.ZERO);
            BigDecimal hopLpFeeUsd = nextTokenUsdPrice == null
                    ? BigDecimal.ZERO
                    : hopLpFeeToken.multiply(nextTokenUsdPrice, MC);
            BigDecimal hopPriceImpactLossUsd = nextTokenUsdPrice == null
                    ? BigDecimal.ZERO
                    : hopPriceImpactLossToken.multiply(nextTokenUsdPrice, MC);

            BigDecimal heuristicUsd = nextTokenUsdPrice == null
                    ? BigDecimal.ZERO
                    : nextAmountOut.multiply(nextTokenUsdPrice, MC).subtract(nextGasUsd);

            List<String> nextPath = new ArrayList<>(tokenPath);
            nextPath.add(nextToken);
            List<RealPoolSnapshot> nextPools = new ArrayList<>(pools);
            nextPools.add(pool);
            List<Map<String, Object>> nextHops = new ArrayList<>(hops);
            nextHops.add(Map.copyOf(hopQuote));
            Set<String> nextVisited = new HashSet<>(visitedTokens);
            nextVisited.add(nextToken);

            return new SearchState(
                    List.copyOf(nextPath),
                    List.copyOf(nextPools),
                    List.copyOf(nextHops),
                    Set.copyOf(nextVisited),
                    nextToken,
                    nextAmountOut,
                    nextTheoreticalAmount,
                    nextGasUsd,
                    heuristicUsd.setScale(8, RoundingMode.HALF_UP),
                    lpFeeCostUsd.add(hopLpFeeUsd).setScale(8, RoundingMode.HALF_UP),
                    priceImpactLossUsd.add(hopPriceImpactLossUsd).setScale(8, RoundingMode.HALF_UP)
            );
        }

        private int hopCount() {
            return pools.size();
        }

        private List<String> poolAddresses() {
            return pools.stream().map(RealPoolSnapshot::getPoolAddress).toList();
        }

        private String routeKey() {
            return String.join(">", poolAddresses());
        }

        private String dexLabel() {
            return pools.stream()
                    .map(RealPoolSnapshot::getDex)
                    .distinct()
                    .collect(Collectors.joining(" + "));
        }

        private BigDecimal pathDiversityBonus() {
            long distinctFees = pools.stream()
                    .map(RealPoolSnapshot::getFee)
                    .filter(fee -> fee != null)
                    .distinct()
                    .count();
            return BigDecimal.valueOf(Math.min(6L, distinctFees * 2L)).setScale(4, RoundingMode.HALF_UP);
        }
    }

    private record RouteSearchResult(
            List<Map<String, Object>> candidates,
            List<Map<String, Object>> splitOptions,
            Map<String, Object> bestSingle,
            Map<String, Object> bestExecution
    ) {}
}
