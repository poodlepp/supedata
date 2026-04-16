package com.dex.api.controller;

import com.dex.api.dto.PriceSnapshot;
import com.dex.api.dto.SwapEventSnapshot;
import com.dex.business.service.blockchain.BlockchainService;
import com.dex.common.model.ApiResponse;
import com.dex.infrastructure.blockchain.listener.SwapEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 区块链 API 控制器
 * 
 * 端点：
 * - GET /api/blockchain/price/eth-usdt - 获取 ETH/USDT 价格
 * - GET /api/blockchain/status - 获取区块链状态
 * - GET /api/blockchain/block - 获取最新区块号
 * - GET /api/blockchain/swaps - 获取最近的 Uniswap V2 Swap 事件
 * - POST /api/blockchain/listener/start - 启动价格监听
 * - POST /api/blockchain/listener/stop - 停止价格监听
 */
@Slf4j
@RestController
@RequestMapping("/api/blockchain")
@RequiredArgsConstructor
public class BlockchainController {

    private final BlockchainService blockchainService;

    /**
     * 获取 ETH/USDT 最新价格
     */
    @GetMapping("/price/eth-usdt")
    public CompletableFuture<ApiResponse<PriceSnapshot>> getEthUsdtPrice() {
        return blockchainService.getLatestEthUsdtPrice()
                .thenApply(price -> {
                    PriceSnapshot snapshot = PriceSnapshot.builder()
                            .pair("ETH/USDT")
                            .price(price)
                            .timestamp(System.currentTimeMillis())
                            .network("Ethereum Mainnet")
                            .source("Blockchain")
                            .isLatest(true)
                            .build();
                    return ApiResponse.success(snapshot);
                })
                .exceptionally(e -> {
                    log.error("Failed to get ETH/USDT price", e);
                    return ApiResponse.error("Failed to get price: " + e.getMessage());
                });
    }

    /**
     * 获取区块链连接状态
     */
    @GetMapping("/status")
    public CompletableFuture<ApiResponse<Boolean>> getStatus() {
        return blockchainService.getConnectionStatus()
                .thenApply(ApiResponse::success)
                .exceptionally(e -> {
                    log.error("Failed to get blockchain status", e);
                    return ApiResponse.error("Failed to get status: " + e.getMessage());
                });
    }

    /**
     * 获取最新区块号
     */
    @GetMapping("/block")
    public CompletableFuture<ApiResponse<Long>> getLatestBlock() {
        return blockchainService.getLatestBlockNumber()
                .thenApply(ApiResponse::success)
                .exceptionally(e -> {
                    log.error("Failed to get latest block", e);
                    return ApiResponse.error("Failed to get block: " + e.getMessage());
                });
    }

    /**
     * 获取最近的 Uniswap V2 Swap 事件（监听器当前缓存）
     */
    @GetMapping("/swaps")
    public ApiResponse<List<SwapEventSnapshot>> getRecentSwaps() {
        try {
            List<SwapEventSnapshot> snapshots = blockchainService.getRecentSwapEvents().stream()
                    .map(this::toSnapshot)
                    .toList();
            return ApiResponse.success(snapshots);
        } catch (Exception e) {
            log.error("Failed to get recent swaps", e);
            return ApiResponse.error("Failed to get swaps: " + e.getMessage());
        }
    }

    /**
     * 启动价格监听
     */
    @PostMapping("/listener/start")
    public ApiResponse<String> startListener() {
        try {
            blockchainService.startPriceListener();
            return ApiResponse.success("Price listener started");
        } catch (Exception e) {
            log.error("Failed to start price listener", e);
            return ApiResponse.error("Failed to start listener: " + e.getMessage());
        }
    }

    /**
     * 停止价格监听
     */
    @PostMapping("/listener/stop")
    public ApiResponse<String> stopListener() {
        try {
            blockchainService.stopPriceListener();
            return ApiResponse.success("Price listener stopped");
        } catch (Exception e) {
            log.error("Failed to stop price listener", e);
            return ApiResponse.error("Failed to stop listener: " + e.getMessage());
        }
    }

    private SwapEventSnapshot toSnapshot(SwapEventData eventData) {
        return SwapEventSnapshot.builder()
                .pair(eventData.getPair())
                .pairAddress(eventData.getPairAddress())
                .txHash(eventData.getTxHash())
                .blockNumber(eventData.getBlockNumber())
                .timestamp(eventData.getTimestamp())
                .sender(eventData.getSender())
                .recipient(eventData.getRecipient())
                .tokenInSymbol(eventData.getTokenInSymbol())
                .tokenOutSymbol(eventData.getTokenOutSymbol())
                .amountIn(eventData.getAmountIn())
                .amountOut(eventData.getAmountOut())
                .price(eventData.getPrice())
                .side(eventData.getSide())
                .build();
    }
}
