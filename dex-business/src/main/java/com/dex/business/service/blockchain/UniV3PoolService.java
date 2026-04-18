package com.dex.business.service.blockchain;

import com.dex.data.entity.UniV3PoolEvent;
import com.dex.infrastructure.blockchain.univ3.UniV3PoolIndexerService;
import com.dex.infrastructure.blockchain.univ3.UniV3PoolSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UniV3PoolService {

    private final UniV3PoolIndexerService indexerService;

    public UniV3PoolSummary getSummary() {
        return indexerService.getSummary();
    }

    public List<UniV3PoolEvent> getRecentEvents(String eventType, int limit) {
        return indexerService.getRecentEvents(eventType, limit);
    }
}
