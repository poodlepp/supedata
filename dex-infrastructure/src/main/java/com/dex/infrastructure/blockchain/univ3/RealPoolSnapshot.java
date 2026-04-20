package com.dex.infrastructure.blockchain.univ3;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class RealPoolSnapshot {
    private String poolAddress;
    private String poolName;
    private String dex;
    private String token0Symbol;
    private String token1Symbol;
    private String token0Address;
    private String token1Address;
    private Integer token0Decimals;
    private Integer token1Decimals;
    private Integer fee;
    private Long blockNumber;
    private Long blockTimestamp;
    private String blockHash;
    private BigDecimal sqrtPriceX96;
    private Integer tick;
    private BigDecimal priceToken0InToken1;
    private BigDecimal priceToken1InToken0;
    private BigDecimal reserve0;
    private BigDecimal reserve1;
    private BigDecimal liquidity;
    private boolean fromChain;
    private String source;
}
