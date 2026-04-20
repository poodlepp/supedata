package com.dex.infrastructure.blockchain.univ3;

import com.dex.data.entity.UniV3PoolEvent;
import com.dex.infrastructure.monitor.metrics.PrometheusMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class UniV3KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PrometheusMetrics prometheusMetrics;

    @Value("${blockchain.univ3.kafka-topic:dex.uniswap.v3.pool.blocks}")
    private String topic;

    public void publishBlock(Long chainId,
                             String poolAddress,
                             String poolName,
                             long blockNumber,
                             String blockHash,
                             List<UniV3PoolEvent> events) {
        try {
            UniV3BlockBatchMessage message = new UniV3BlockBatchMessage();
            message.setChainId(chainId);
            message.setPoolAddress(poolAddress);
            message.setPoolName(poolName);
            message.setBlockNumber(blockNumber);
            message.setBlockHash(blockHash);
            message.setEvents(events);
            String key = chainId + ":" + poolAddress.toLowerCase();
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, key, payload).get();
            prometheusMetrics.recordKafkaPublished();
        } catch (Exception e) {
            prometheusMetrics.recordKafkaFailure();
            throw new IllegalStateException("Failed to publish kafka block batch for block " + blockNumber, e);
        }
    }
}
