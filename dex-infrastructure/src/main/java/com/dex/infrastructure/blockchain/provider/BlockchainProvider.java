package com.dex.infrastructure.blockchain.provider;

import java.math.BigInteger;
import java.util.concurrent.CompletableFuture;

/**
 * 区块链提供者接口
 * 
 * 设计模式：策略模式 + 工厂模式
 * - 定义统一的区块链操作接口
 * - 支持多链实现（Sepolia、Mainnet 等）
 * - 异步操作，返回 CompletableFuture
 */
public interface BlockchainProvider {

    /**
     * 获取 ETH 价格（单位：Wei）
     */
    CompletableFuture<BigInteger> getEthPrice();

    /**
     * 获取 USDT 价格（单位：Wei）
     */
    CompletableFuture<BigInteger> getUsdtPrice();

    /**
     * 获取 ETH/USDT 交易对价格
     */
    CompletableFuture<Double> getEthUsdtPrice();

    /**
     * 获取最新区块号
     */
    CompletableFuture<BigInteger> getLatestBlockNumber();

    /**
     * 获取网络名称
     */
    String getNetworkName();

    /**
     * 检查连接状态
     */
    CompletableFuture<Boolean> isConnected();
}
