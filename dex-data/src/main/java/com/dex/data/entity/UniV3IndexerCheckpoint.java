package com.dex.data.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UniV3IndexerCheckpoint {
    private Long id;
    private Long chainId;
    private String poolAddress;
    private String poolName;
    private Long startBlock;
    private Long lastScannedBlock;
    private Long lastCommittedBlock;
    private String lastCommittedBlockHash;
    private Integer confirmations;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
