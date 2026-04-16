package com.dex.data.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiquidityPool {
    private Long id;
    private String poolAddress;
    private String token0;
    private String token1;
    private BigDecimal reserve0;
    private BigDecimal reserve1;
    private LocalDateTime updatedAt;
}
