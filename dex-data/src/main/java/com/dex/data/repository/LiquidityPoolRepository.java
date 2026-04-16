package com.dex.data.repository;

import com.dex.data.entity.LiquidityPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LiquidityPoolRepository {
    @Select("""
            SELECT id, pool_address, token0, token1, reserve0, reserve1, updated_at
            FROM liquidity_pools
            WHERE pool_address = #{poolAddress}
            LIMIT 1
            """)
    LiquidityPool findByPoolAddress(String poolAddress);

    @Select("""
            SELECT id, pool_address, token0, token1, reserve0, reserve1, updated_at
            FROM liquidity_pools
            WHERE token0 = #{token0} AND token1 = #{token1}
            """)
    List<LiquidityPool> findByToken0AndToken1(String token0, String token1);

    @Select("""
            SELECT id, pool_address, token0, token1, reserve0, reserve1, updated_at
            FROM liquidity_pools
            ORDER BY id
            """)
    List<LiquidityPool> findAll();
}
