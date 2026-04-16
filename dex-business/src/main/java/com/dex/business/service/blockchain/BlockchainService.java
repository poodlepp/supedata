package com.dex.business.service.blockchain;

import com.dex.infrastructure.blockchain.listener.SwapEventData;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 区块链业务服务接口
 * 
 * 职责：
 * - 提供高层业务操作
 * - 聚合多个数据源
 * - 缓存和优化
 */
public interface BlockchainService {

    /**
     * 获取 ETH/USDT 最新价格
     */
    CompletableFuture<Double> getLatestEthUsdtPrice();

    /**
     * 获取区块链连接状态
     */
    CompletableFuture<Boolean> getConnectionStatus();

    /**
     * 获取最新区块号
     */
    CompletableFuture<Long> getLatestBlockNumber();

    /**
     * 获取最近的 Uniswap V2 Swap 事件（来自监听器缓存）
     */
    List<SwapEventData> getRecentSwapEvents();

    /**
     * 启动价格监听
     */
    void startPriceListener();

    /**
     * 停止价格监听
     */
    void stopPriceListener();
}
