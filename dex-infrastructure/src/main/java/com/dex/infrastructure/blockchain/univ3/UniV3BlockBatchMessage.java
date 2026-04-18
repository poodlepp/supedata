package com.dex.infrastructure.blockchain.univ3;

import com.dex.data.entity.UniV3PoolEvent;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UniV3BlockBatchMessage {
    private Long chainId;
    private String poolAddress;
    private String poolName;
    private Long blockNumber;
    private String blockHash;
    private List<UniV3PoolEvent> events = new ArrayList<>();
}
