package com.dex.infrastructure.blockchain.univ3;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class UniV3PoolSummary {
    private Long chainId;
    private String poolAddress;
    private String poolName;
    private Long latestBlock;
    private Long safeLatestBlock;
    private Long latestCommittedBlock;
    private Long startBlock;
    private Long syncLag;
    private Long totalEvents;
    private Long latestEventTime;
    private Map<String, Long> eventCounts;
    private String status;
    private String errorMessage;
}
