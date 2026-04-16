package com.dex.business.service.blockchain;

import com.dex.infrastructure.blockchain.listener.EthUsdtPriceListener;
import com.dex.infrastructure.blockchain.listener.SwapEventData;
import com.dex.infrastructure.blockchain.listener.UniswapV2SwapListener;
import com.dex.infrastructure.blockchain.provider.BlockchainProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 以太坊主网区块链服务实现
 *
 * 职责：
 * - 封装主网特定的业务逻辑
 * - 管理价格监听生命周期
 * - 提供缓存和优化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MainnetService implements BlockchainService {

    private final BlockchainProvider blockchainProvider;
    private final EthUsdtPriceListener priceListener;
    private final UniswapV2SwapListener swapListener;

    @Override
    public CompletableFuture<Double> getLatestEthUsdtPrice() {
        return blockchainProvider.getEthUsdtPrice()
                .exceptionally(e -> {
                    log.error("Failed to get ETH/USDT price from mainnet", e);
                    return 0.0;
                });
    }

    @Override
    public CompletableFuture<Boolean> getConnectionStatus() {
        return blockchainProvider.isConnected();
    }

    @Override
    public CompletableFuture<Long> getLatestBlockNumber() {
        return blockchainProvider.getLatestBlockNumber()
                .thenApply(blockNumber -> blockNumber.longValue())
                .exceptionally(e -> {
                    log.error("Failed to get latest block number", e);
                    return 0L;
                });
    }

    @Override
    public List<SwapEventData> getRecentSwapEvents() {
        return swapListener.getRecentEvents();
    }

    @Override
    public void startPriceListener() {
        log.info("Starting blockchain listeners for Ethereum Mainnet");
        priceListener.start();
        swapListener.start();
    }

    @Override
    public void stopPriceListener() {
        log.info("Stopping blockchain listeners for Ethereum Mainnet");
        priceListener.stop();
        swapListener.stop();
    }
}
