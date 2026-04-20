package com.dex.business.service;

import com.dex.data.entity.LiquidityPool;
import com.dex.infrastructure.blockchain.univ3.RealPoolSnapshot;
import com.dex.infrastructure.blockchain.univ3.UniV3RealPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 真实流动性池服务：只暴露当前支持的主网真实池。
 */
@Service
@RequiredArgsConstructor
public class LiquidityService {

    private final UniV3RealPoolService realPoolService;

    public Optional<LiquidityPool> getPoolByAddress(String poolAddress) {
        return realPoolService.getPool(poolAddress).map(this::toEntity);
    }

    public List<LiquidityPool> getPoolsByTokenPair(String token0, String token1) {
        String left = normalize(token0);
        String right = normalize(token1);
        return realPoolService.getSupportedPools().stream()
                .filter(pool -> (pool.getToken0Symbol().equals(left) && pool.getToken1Symbol().equals(right))
                             || (pool.getToken0Symbol().equals(right) && pool.getToken1Symbol().equals(left)))
                .map(this::toEntity)
                .toList();
    }

    public List<LiquidityPool> getAllPools() {
        return realPoolService.getSupportedPools().stream().map(this::toEntity).toList();
    }

    public List<RealPoolSnapshot> getAllRealPools() {
        return realPoolService.getSupportedPools();
    }

    private LiquidityPool toEntity(RealPoolSnapshot pool) {
        return new LiquidityPool(
                null,
                pool.getPoolAddress(),
                pool.getToken0Symbol(),
                pool.getToken1Symbol(),
                pool.getReserve0(),
                pool.getReserve1(),
                LocalDateTime.ofInstant(Instant.ofEpochMilli(pool.getBlockTimestamp()), ZoneId.systemDefault())
        );
    }

    private String normalize(String token) {
        return token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
    }
}
