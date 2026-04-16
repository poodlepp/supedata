package com.dex.infrastructure.blockchain.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Uint;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 以太坊主网提供者实现
 *
 * 职责：
 * - 连接以太坊主网 RPC
 * - 从 Uniswap V2 获取 ETH/USDT 价格
 * - 监听区块链事件
 *
 * 设计模式：策略模式
 * - 实现 BlockchainProvider 接口
 * - 支持多链扩展
 */
@Slf4j
@Component
public class MainnetProvider implements BlockchainProvider {

    private final Web3j web3j;

    public MainnetProvider(Web3j web3j) {
        this.web3j = web3j;
    }

    // 以太坊主网 Uniswap V2 Router 地址
    private static final String UNISWAP_V2_ROUTER = "0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D";

    // Token 地址（主网）
    private static final String WETH = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2";  // WETH on Mainnet
    private static final String USDT = "0xdAC17F958D2ee523a2206206994597C13D831ec7";  // USDT on Mainnet

    @Override
    public CompletableFuture<BigInteger> getEthPrice() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BigDecimal price = getEthUsdtPriceFromUniswap();
                return price.toBigInteger();
            } catch (Exception e) {
                log.error("Failed to get ETH price from Uniswap", e);
                return BigInteger.ZERO;
            }
        });
    }

    @Override
    public CompletableFuture<BigInteger> getUsdtPrice() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // USDT 价格为 1
                return BigInteger.ONE;
            } catch (Exception e) {
                log.error("Failed to get USDT price", e);
                return BigInteger.ZERO;
            }
        });
    }

    @Override
    public CompletableFuture<Double> getEthUsdtPrice() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BigDecimal price = getEthUsdtPriceFromUniswap();

                if (price.equals(BigDecimal.ZERO)) {
                    log.warn("Failed to get price from Uniswap, returning zero");
                    return 0.0;
                }

                log.info("ETH/USDT Price from Uniswap Mainnet: {}", price);
                return price.doubleValue();
            } catch (Exception e) {
                log.error("Failed to get ETH/USDT price from mainnet", e);
                return 0.0;
            }
        });
    }

    @Override
    public CompletableFuture<BigInteger> getLatestBlockNumber() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return web3j.ethBlockNumber().send().getBlockNumber();
            } catch (Exception e) {
                log.error("Failed to get latest block number from mainnet", e);
                return BigInteger.ZERO;
            }
        });
    }

    @Override
    public String getNetworkName() {
        return "Ethereum Mainnet";
    }

    @Override
    public CompletableFuture<Boolean> isConnected() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return web3j.web3ClientVersion().send().getWeb3ClientVersion() != null;
            } catch (Exception e) {
                log.warn("Mainnet connection check failed", e);
                return false;
            }
        });
    }

    /**
     * 从 Uniswap V2 Router 获取 ETH/USDT 价格
     * 调用 getAmountsOut(1 ETH, [WETH, USDT]) 获取 1 ETH 对应的 USDT 数量
     *
     * @return 价格（BigDecimal）
     */
    private BigDecimal getEthUsdtPriceFromUniswap() {
        try {
            log.debug("Attempting to get price from Uniswap V2 Router: {}", UNISWAP_V2_ROUTER);

            // 1 ETH = 10^18 wei
            BigInteger amountIn = BigInteger.TEN.pow(18);

            // 构建 getAmountsOut 函数调用
            // getAmountsOut(uint amountIn, address[] calldata path)
            List<TypeReference<?>> outputParameters = Arrays.asList(
                    new TypeReference<DynamicArray<Uint>>() {}
            );

            Function function = new Function(
                    "getAmountsOut",
                    Arrays.asList(
                            new Uint(amountIn),
                            new org.web3j.abi.datatypes.DynamicArray<>(
                                    Address.class,
                                    Arrays.asList(
                                            new Address(WETH),
                                            new Address(USDT)
                                    )
                            )
                    ),
                    outputParameters
            );

            String encodedFunction = FunctionEncoder.encode(function);

            // 调用合约
            Transaction transaction = Transaction.createEthCallTransaction(
                    null,
                    UNISWAP_V2_ROUTER,
                    encodedFunction
            );

            String result = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST)
                    .send()
                    .getValue();

            if (result == null || result.equals("0x")) {
                log.warn("Empty result from Uniswap V2 Router");
                return BigDecimal.ZERO;
            }

            // 解析返回值
            @SuppressWarnings("unchecked")
            List<TypeReference<org.web3j.abi.datatypes.Type>> outputs =
                    (List<TypeReference<org.web3j.abi.datatypes.Type>>) (List<?>) Arrays.asList(
                    new TypeReference<DynamicArray<Uint>>() {}
            );

            List<org.web3j.abi.datatypes.Type> decodedOutput = FunctionReturnDecoder.decode(result, outputs);

            if (decodedOutput.isEmpty()) {
                log.warn("Failed to decode Uniswap response");
                return BigDecimal.ZERO;
            }

            @SuppressWarnings("unchecked")
            DynamicArray<Uint> amounts = (DynamicArray<Uint>) decodedOutput.get(0);
            List<Uint> amountsList = amounts.getValue();

            if (amountsList.size() < 2) {
                log.warn("Invalid amounts array size: {}", amountsList.size());
                return BigDecimal.ZERO;
            }

            // amountsList[1] 是输出的 USDT 数量（6 位小数）
            BigInteger usdtAmount = amountsList.get(1).getValue();

            // USDT 有 6 位小数，转换为标准价格格式
            BigDecimal price = new BigDecimal(usdtAmount).divide(
                    BigDecimal.TEN.pow(6),
                    8,
                    java.math.RoundingMode.HALF_UP
            );

            log.info("ETH/USDT Price from Uniswap: {} (raw: {})", price, usdtAmount);
            return price;

        } catch (Exception e) {
            log.error("Failed to get price from Uniswap", e);
            return BigDecimal.ZERO;
        }
    }
}
