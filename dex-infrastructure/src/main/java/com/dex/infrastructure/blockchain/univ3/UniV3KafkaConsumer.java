package com.dex.infrastructure.blockchain.univ3;

import com.dex.data.entity.UniV3PoolEvent;
import com.dex.data.repository.UniV3IndexerCheckpointRepository;
import com.dex.data.repository.UniV3PoolEventRepository;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spring.kafka.listener", name = "auto-startup", havingValue = "true")
public class UniV3KafkaConsumer {

    private final ObjectMapper objectMapper;
    private final UniV3PoolEventRepository eventRepository;
    private final UniV3IndexerCheckpointRepository checkpointRepository;
    private final PrometheusMetrics prometheusMetrics;

    @KafkaListener(topics = "${blockchain.univ3.kafka-topic:dex.uniswap.v3.pool.blocks}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onMessage(String payload) {
        try {
            UniV3BlockBatchMessage message = objectMapper.readValue(payload, UniV3BlockBatchMessage.class);
            prometheusMetrics.recordKafkaConsumed();
            message.getEvents().stream()
                    .sorted(Comparator.comparingInt(UniV3PoolEvent::getTransactionIndex)
                            .thenComparingInt(UniV3PoolEvent::getLogIndex))
                    .forEach(eventRepository::insertIgnore);
            checkpointRepository.updateCommittedBlock(
                    message.getChainId(),
                    message.getPoolAddress(),
                    message.getBlockNumber(),
                    message.getBlockHash()
            );
            prometheusMetrics.recordCommittedBlock(message.getBlockNumber());
            prometheusMetrics.recordEventsProcessed(message.getEvents() == null ? 0 : message.getEvents().size());
            Long latestEventTime = message.getEvents() == null || message.getEvents().isEmpty()
                    ? null
                    : message.getEvents().stream()
                    .map(UniV3PoolEvent::getBlockTime)
                    .filter(java.util.Objects::nonNull)
                    .max(Long::compareTo)
                    .orElse(null);
            if (latestEventTime != null) {
                prometheusMetrics.recordLatestEventTimestamp(latestEventTime);
                prometheusMetrics.recordLatestEventLagMs(System.currentTimeMillis() - latestEventTime);
            }
        } catch (Exception e) {
            prometheusMetrics.recordKafkaFailure();
            log.error("Failed to consume UniV3 kafka payload", e);
            throw new IllegalStateException("Failed to consume UniV3 kafka payload", e);
        }
    }
}
