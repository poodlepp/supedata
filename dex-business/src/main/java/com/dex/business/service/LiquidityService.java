package com.dex.business.service;

import com.dex.data.entity.LiquidityPool;
import com.dex.data.repository.LiquidityPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 流动性分析服务
 */
@Service
@RequiredArgsConstructor
public class LiquidityService {
    private final LiquidityPoolRepository liquidityPoolRepository;

    public Optional<LiquidityPool> getPoolByAddress(String poolAddress) {
        return Optional.ofNullable(liquidityPoolRepository.findByPoolAddress(poolAddress));
    }

    public List<LiquidityPool> getPoolsByTokenPair(String token0, String token1) {
        return liquidityPoolRepository.findByToken0AndToken1(token0, token1);
    }

    public List<LiquidityPool> getAllPools() {
        return liquidityPoolRepository.findAll();
    }
}
