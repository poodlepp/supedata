package com.dex.infrastructure.blockchain.univ3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint160;
import org.web3j.abi.datatypes.generated.Uint24;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UniV3RealPoolService {

    private static final MathContext MC = new MathContext(40, RoundingMode.HALF_UP);
    private static final BigDecimal Q96 = new BigDecimal(BigInteger.ONE.shiftLeft(96));
    private static final BigDecimal Q192 = Q96.multiply(Q96, MC);
    private static final long POOL_CACHE_TTL_MS = 15_000L;
    private static final long QUOTE_CACHE_TTL_MS = 15_000L;

    private static final String WETH = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2";
    private static final String USDC = "0xA0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";
    private static final String DAI = "0x6B175474E89094C44Da98b954EedeAC495271d0F";

    private static final String POOL_WETH_USDC_500  = "0x88e6A0c2dDD26FEEb64F039a2c41296FcB3f5640";
    private static final String POOL_WETH_USDC_3000 = "0x8ad599c3A0ff1De082011EFDDc58f1908eb6e6D8";
    private static final String POOL_WETH_DAI_3000  = "0xC2e9F25be6257c210d7ADf0d4cd6e3e881ba25f8";
    private static final String POOL_DAI_USDC_100   = "0x5777d92f208679DB4b9778590Fa3CAB3aC9e2168";
    private static final String QUOTER_V1 = "0xb27308f9F90D607463bb33eA1BeBb41C27CE5AB6";

    private final Web3j web3j;

    private volatile List<RealPoolSnapshot> cachedPools = List.of();
    private volatile long poolCacheAt = 0L;
    private final Map<String, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    /** 价格历史环形缓冲区：key = "TOKEN0-TOKEN1"，最多保留 120 条（约 30 分钟 @ 15s 刷新） */
    private static final int PRICE_HISTORY_MAX = 120;
    private final Map<String, java.util.Deque<PricePoint>> priceHistory = new ConcurrentHashMap<>();

    public synchronized List<RealPoolSnapshot> getSupportedPools() {
        long now = System.currentTimeMillis();
        if (!cachedPools.isEmpty() && now - poolCacheAt < POOL_CACHE_TTL_MS) {
            return cachedPools;
        }

        try {
            EthBlock.Block latestBlock = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
            if (latestBlock == null) {
                return cachedPools;
            }
            List<RealPoolSnapshot> pools = new ArrayList<>();
            loadPool(latestBlock, POOL_WETH_USDC_500,  "Uniswap V3 USDC/WETH 0.05%", USDC, WETH, "USDC", "WETH", 6,  18, 500).ifPresent(pools::add);
            loadPool(latestBlock, POOL_WETH_USDC_3000, "Uniswap V3 USDC/WETH 0.30%", USDC, WETH, "USDC", "WETH", 6,  18, 3000).ifPresent(pools::add);
            loadPool(latestBlock, POOL_WETH_DAI_3000,  "Uniswap V3 DAI/WETH 0.30%",  DAI,  WETH, "DAI",  "WETH", 18, 18, 3000).ifPresent(pools::add);
            loadPool(latestBlock, POOL_DAI_USDC_100,   "Uniswap V3 DAI/USDC 0.01%",  DAI,  USDC, "DAI",  "USDC", 18, 6,  100).ifPresent(pools::add);
            if (!pools.isEmpty()) {
                cachedPools = List.copyOf(pools);
                poolCacheAt = now;
                recordPriceHistory(pools);
            }
        } catch (Exception e) {
            log.warn("Failed to refresh supported pools, fallback to cache", e);
        }
        return cachedPools;
    }

    public Optional<RealPoolSnapshot> getPool(String poolAddress) {
        return getSupportedPools().stream()
                .filter(pool -> pool.getPoolAddress().equalsIgnoreCase(poolAddress))
                .findFirst();
    }

    public Optional<RealPoolSnapshot> findPoolByPair(String tokenA, String tokenB) {
        String left = normalize(tokenA);
        String right = normalize(tokenB);
        return getSupportedPools().stream()
                .filter(pool -> (pool.getToken0Symbol().equals(left) && pool.getToken1Symbol().equals(right))
                             || (pool.getToken0Symbol().equals(right) && pool.getToken1Symbol().equals(left)))
                .findFirst();
    }

    /** 返回同一交易对的所有费率层池子（用于多候选路径对比）。 */
    public List<RealPoolSnapshot> findAllPoolsByPair(String tokenA, String tokenB) {
        String left = normalize(tokenA);
        String right = normalize(tokenB);
        return getSupportedPools().stream()
                .filter(pool -> (pool.getToken0Symbol().equals(left) && pool.getToken1Symbol().equals(right))
                             || (pool.getToken0Symbol().equals(right) && pool.getToken1Symbol().equals(left)))
                .toList();
    }

    /**
     * 多跳报价：链式调用单跳 quoteExactInputSingle，返回合并结果。
     * tokenPath 至少包含 2 个 token symbol，例如 ["WETH","DAI","USDC"]。
     */
    public Map<String, Object> quoteMultiHopExactInput(List<String> tokenPath, BigDecimal amountIn) {
        if (tokenPath == null || tokenPath.size() < 2) {
            return Map.of("supported", false, "reason", "INVALID_PATH", "message", "路径至少需要 2 个 token");
        }
        BigDecimal currentAmount = amountIn == null || amountIn.signum() <= 0 ? BigDecimal.ONE : amountIn;
        List<Map<String, Object>> hops = new ArrayList<>();
        BigDecimal totalPriceImpact = BigDecimal.ZERO;

        for (int i = 0; i < tokenPath.size() - 1; i++) {
            Map<String, Object> hop = quoteExactInputSingle(tokenPath.get(i), tokenPath.get(i + 1), currentAmount);
            if (!Boolean.TRUE.equals(hop.get("supported"))) {
                return Map.of(
                        "supported", false,
                        "reason", "HOP_FAILED",
                        "message", "第 " + (i + 1) + " 跳失败: " + hop.get("message"),
                        "failedHop", i
                );
            }
            hops.add(hop);
            currentAmount = (BigDecimal) hop.get("amountOut");
            BigDecimal hopImpact = hop.get("priceImpactPct") instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            totalPriceImpact = totalPriceImpact.add(hopImpact);
        }

        Map<String, Object> first = hops.getFirst();
        Map<String, Object> last = hops.getLast();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", true);
        result.put("tokenIn", normalize(tokenPath.getFirst()));
        result.put("tokenOut", normalize(tokenPath.getLast()));
        result.put("amountIn", amountIn);
        result.put("amountOut", currentAmount.setScale(8, RoundingMode.HALF_UP));
        result.put("priceImpactPct", totalPriceImpact.setScale(6, RoundingMode.HALF_UP));
        result.put("hops", hops);
        result.put("hopCount", hops.size());
        result.put("blockNumber", last.get("blockNumber"));
        result.put("blockTimestamp", last.get("blockTimestamp"));
        result.put("source", "uniswap-v3-multi-hop-chained");
        result.put("dex", "Uniswap V3");
        return result;
    }

    public Map<String, Object> quoteExactInputSingle(String tokenInSymbol, String tokenOutSymbol, BigDecimal amountIn) {
        Optional<RealPoolSnapshot> maybePool = findPoolByPair(tokenInSymbol, tokenOutSymbol);
        if (maybePool.isEmpty()) {
            return Map.of(
                    "supported", false,
                    "reason", "PAIR_NOT_SUPPORTED",
                    "message", "当前仅支持少量真实池报价"
            );
        }

        return quoteExactInputSingle(maybePool.get(), tokenInSymbol, tokenOutSymbol, amountIn);
    }

    public Map<String, Object> quoteExactInputSingle(RealPoolSnapshot pool,
                                                     String tokenInSymbol,
                                                     String tokenOutSymbol,
                                                     BigDecimal amountIn) {
        String safePoolAddress = pool == null || pool.getPoolAddress() == null ? "unknown" : pool.getPoolAddress().toLowerCase();
        String cacheKey = safePoolAddress + ":" + normalize(tokenInSymbol) + "->" + normalize(tokenOutSymbol) + ":"
                + (amountIn == null ? "1" : amountIn.stripTrailingZeros().toPlainString());
        CachedQuote cached = quoteCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.cachedAt < QUOTE_CACHE_TTL_MS) {
            return cached.data;
        }

        boolean forward = pool.getToken0Symbol().equalsIgnoreCase(normalize(tokenInSymbol));
        String tokenIn = forward ? pool.getToken0Address() : pool.getToken1Address();
        String tokenOut = forward ? pool.getToken1Address() : pool.getToken0Address();
        int tokenInDecimals = forward ? pool.getToken0Decimals() : pool.getToken1Decimals();
        int tokenOutDecimals = forward ? pool.getToken1Decimals() : pool.getToken0Decimals();
        BigDecimal safeAmountIn = amountIn == null || amountIn.signum() <= 0 ? BigDecimal.ONE : amountIn;
        BigInteger rawAmountIn = safeAmountIn.multiply(BigDecimal.TEN.pow(tokenInDecimals), MC).toBigInteger();

        Function function = new Function(
                "quoteExactInputSingle",
                List.of(
                        new org.web3j.abi.datatypes.Address(tokenIn),
                        new org.web3j.abi.datatypes.Address(tokenOut),
                        new Uint24(BigInteger.valueOf(pool.getFee())),
                        new Uint256(rawAmountIn),
                        new Uint160(BigInteger.ZERO)
                ),
                outputsForQuoterV1()
        );

        String encoded = FunctionEncoder.encode(function);
        Transaction tx = Transaction.createEthCallTransaction(null, QUOTER_V1, encoded);

        try {
            String value = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send().getValue();
            List<Type> decoded = FunctionReturnDecoder.decode(value, function.getOutputParameters());
            if (decoded.isEmpty()) {
                return cached != null ? cached.data : Map.of(
                        "supported", false,
                        "reason", "EMPTY_QUOTE",
                        "message", "主网 Quoter 返回为空"
                );
            }
            BigInteger rawAmountOut = (BigInteger) decoded.getFirst().getValue();
            BigDecimal amountOut = new BigDecimal(rawAmountOut).divide(BigDecimal.TEN.pow(tokenOutDecimals), 18, RoundingMode.HALF_UP);
            BigDecimal spotOut = forward ? pool.getPriceToken0InToken1() : pool.getPriceToken1InToken0();
            BigDecimal grossAmountOut = safeAmountIn.multiply(spotOut, MC).setScale(8, RoundingMode.HALF_UP);
            BigDecimal priceImpactPct = grossAmountOut.signum() == 0
                    ? BigDecimal.ZERO
                    : grossAmountOut.subtract(amountOut)
                        .divide(grossAmountOut, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supported", true);
            data.put("tokenIn", normalize(tokenInSymbol));
            data.put("tokenOut", normalize(tokenOutSymbol));
            data.put("amountIn", safeAmountIn);
            data.put("amountOut", amountOut.setScale(8, RoundingMode.HALF_UP));
            data.put("grossAmountOut", grossAmountOut);
            data.put("priceImpactPct", priceImpactPct.setScale(6, RoundingMode.HALF_UP));
            data.put("poolAddress", pool.getPoolAddress());
            data.put("poolName", pool.getPoolName());
            data.put("fee", pool.getFee());
            data.put("blockNumber", pool.getBlockNumber());
            data.put("blockTimestamp", pool.getBlockTimestamp());
            data.put("source", "uniswap-v3-quoter-v1");
            data.put("dex", pool.getDex());
            quoteCache.put(cacheKey, new CachedQuote(now, Map.copyOf(data)));
            return data;
        } catch (Exception e) {
            log.warn("Failed to quote via Uniswap V3 quoter, fallback to cache if present", e);
            if (cached != null) {
                return cached.data;
            }
            return Map.of(
                    "supported", false,
                    "reason", "QUOTE_FAILED",
                    "message", e.getMessage()
            );
        }
    }

    private Optional<RealPoolSnapshot> loadPool(EthBlock.Block latestBlock,
                                                String poolAddress,
                                                String poolName,
                                                String token0Address,
                                                String token1Address,
                                                String token0Symbol,
                                                String token1Symbol,
                                                int token0Decimals,
                                                int token1Decimals,
                                                int expectedFee) {
        try {
            String slot0Result = call(poolAddress, new Function(
                    "slot0",
                    List.of(),
                    outputsForSlot0()
            ));
            List<Type> slot0Decoded = FunctionReturnDecoder.decode(slot0Result, castOutputs(outputsForSlot0()));
            BigInteger sqrtPriceX96Raw = (BigInteger) slot0Decoded.get(0).getValue();
            Integer tick = ((BigInteger) slot0Decoded.get(1).getValue()).intValue();

            BigInteger liquidityRaw = uint(poolAddress, "liquidity");
            int fee = uint(poolAddress, "fee").intValue();

            BigDecimal sqrtPrice = new BigDecimal(sqrtPriceX96Raw);
            BigDecimal ratio = sqrtPrice.multiply(sqrtPrice, MC).divide(Q192, 30, RoundingMode.HALF_UP);
            int decimalDiff = token0Decimals - token1Decimals;
            BigDecimal scaleFactor = BigDecimal.TEN.pow(Math.abs(decimalDiff));
            BigDecimal priceToken0InToken1 = decimalDiff >= 0
                    ? ratio.multiply(scaleFactor, MC)
                    : ratio.divide(scaleFactor, 30, RoundingMode.HALF_UP);
            BigDecimal priceToken1InToken0 = BigDecimal.ONE.divide(priceToken0InToken1, 30, RoundingMode.HALF_UP);

            BigDecimal liquidity = new BigDecimal(liquidityRaw);
            BigDecimal reserve0 = liquidity.multiply(Q96, MC)
                    .divide(sqrtPrice, 18, RoundingMode.HALF_UP)
                    .divide(BigDecimal.TEN.pow(token0Decimals), 18, RoundingMode.HALF_UP);
            BigDecimal reserve1 = liquidity.multiply(sqrtPrice, MC)
                    .divide(Q96, 18, RoundingMode.HALF_UP)
                    .divide(BigDecimal.TEN.pow(token1Decimals), 18, RoundingMode.HALF_UP);

            return Optional.of(RealPoolSnapshot.builder()
                    .poolAddress(poolAddress)
                    .poolName(poolName)
                    .dex("Uniswap V3")
                    .token0Symbol(token0Symbol)
                    .token1Symbol(token1Symbol)
                    .token0Address(token0Address)
                    .token1Address(token1Address)
                    .token0Decimals(token0Decimals)
                    .token1Decimals(token1Decimals)
                    .fee(fee == 0 ? expectedFee : fee)
                    .blockNumber(latestBlock.getNumber().longValue())
                    .blockTimestamp(latestBlock.getTimestamp().longValue() * 1000L)
                    .blockHash(latestBlock.getHash())
                    .sqrtPriceX96(sqrtPrice)
                    .tick(tick)
                    .priceToken0InToken1(priceToken0InToken1.setScale(8, RoundingMode.HALF_UP))
                    .priceToken1InToken0(priceToken1InToken0.setScale(12, RoundingMode.HALF_UP))
                    .reserve0(reserve0.setScale(8, RoundingMode.HALF_UP))
                    .reserve1(reserve1.setScale(8, RoundingMode.HALF_UP))
                    .liquidity(liquidity.setScale(0, RoundingMode.HALF_UP))
                    .fromChain(true)
                    .source("ethereum-mainnet-uniswap-v3-pool")
                    .build());
        } catch (Exception e) {
            log.warn("Failed to load pool {}", poolAddress, e);
            return cachedPools.stream().filter(pool -> pool.getPoolAddress().equalsIgnoreCase(poolAddress)).findFirst();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<TypeReference<Type>> castOutputs(List<TypeReference<?>> outputs) {
        return outputs.stream().map(o -> (TypeReference<Type>) (TypeReference) o).collect(Collectors.toList());
    }

    private List<TypeReference<?>> outputsForQuoterV1() {
        return List.of(new TypeReference<Uint256>() {});
    }

    private List<TypeReference<?>> outputsForSlot0() {
        return List.of(
                new TypeReference<Uint160>() {},
                new TypeReference<org.web3j.abi.datatypes.generated.Int24>() {},
                new TypeReference<Uint16>() {},
                new TypeReference<Uint16>() {},
                new TypeReference<Uint16>() {},
                new TypeReference<Uint8>() {},
                new TypeReference<org.web3j.abi.datatypes.Bool>() {}
        );
    }

    private List<TypeReference<?>> outputsForUint256() {
        return List.of(new TypeReference<Uint256>() {});
    }

    private BigInteger uint(String contract, String method) throws Exception {
        List<TypeReference<?>> outputs = outputsForUint256();
        String value = call(contract, new Function(method, List.of(), outputs));
        List<Type> decoded = FunctionReturnDecoder.decode(value, castOutputs(outputs));
        return (BigInteger) decoded.getFirst().getValue();
    }

    private String call(String contract, Function function) throws Exception {
        String encoded = FunctionEncoder.encode(function);
        Transaction tx = Transaction.createEthCallTransaction(null, contract, encoded);
        return web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send().getValue();
    }

    private String normalize(String token) {
        if (token == null) return "";
        String value = token.trim().toUpperCase();
        return value.equals("ETH") ? "WETH" : value;
    }

    // --------------------------------------------------------- price history

    private void recordPriceHistory(List<RealPoolSnapshot> pools) {
        for (RealPoolSnapshot pool : pools) {
            String key = pool.getToken0Symbol() + "-" + pool.getToken1Symbol();
            priceHistory.computeIfAbsent(key, k -> new java.util.ArrayDeque<>());
            java.util.Deque<PricePoint> deque = priceHistory.get(key);
            deque.addLast(new PricePoint(
                    pool.getBlockTimestamp() != null ? pool.getBlockTimestamp() : System.currentTimeMillis(),
                    pool.getPriceToken0InToken1(),
                    pool.getBlockNumber()
            ));
            while (deque.size() > PRICE_HISTORY_MAX) {
                deque.pollFirst();
            }
        }
    }

    /** 返回指定交易对的价格历史（最新在后）。 */
    public List<PricePoint> getPriceHistory(String tokenA, String tokenB) {
        String left = normalize(tokenA);
        String right = normalize(tokenB);
        String key = left + "-" + right;
        String keyReverse = right + "-" + left;
        java.util.Deque<PricePoint> deque = priceHistory.getOrDefault(key,
                priceHistory.getOrDefault(keyReverse, new java.util.ArrayDeque<>()));
        return new ArrayList<>(deque);
    }

    /** 获取 getSummary 状态摘要（供 StageStatusService 使用）。 */
    public Map<String, Object> getSummary() {
        List<RealPoolSnapshot> pools = getSupportedPools();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", pools.isEmpty() ? "BOOTSTRAPPING" : "LIVE");
        summary.put("poolCount", pools.size());
        summary.put("pools", pools.stream().map(p -> Map.of(
                "poolAddress", p.getPoolAddress(),
                "poolName", p.getPoolName(),
                "fee", p.getFee()
        )).toList());
        return summary;
    }

    public record PricePoint(long timestamp, BigDecimal price, Long blockNumber) {}

    private record CachedQuote(long cachedAt, Map<String, Object> data) {}
}
