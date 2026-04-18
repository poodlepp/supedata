package com.dex.data.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UniV3PoolEvent {
    private Long id;
    private String eventUid;
    private Long chainId;
    private String poolAddress;
    private String poolName;
    private String eventType;
    private Long blockNumber;
    private String blockHash;
    private String transactionHash;
    private Integer transactionIndex;
    private Integer logIndex;
    private Long blockTime;
    private String sender;
    private String recipient;
    private String ownerAddress;
    private Integer tickLower;
    private Integer tickUpper;
    private String amount;
    private String amount0;
    private String amount1;
    private String sqrtPriceX96;
    private String liquidity;
    private Integer tick;
    private String paid0;
    private String paid1;
    private String summary;
    private LocalDateTime createdAt;
}
