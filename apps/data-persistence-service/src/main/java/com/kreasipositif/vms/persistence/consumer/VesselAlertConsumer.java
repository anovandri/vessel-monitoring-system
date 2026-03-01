package com.kreasipositif.vms.persistence.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.kreasipositif.vms.persistence.service.AlertPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka Consumer for vessel-alerts topic
 * 
 * Consumes anomaly alerts detected by Flink and persists to:
 * - PostgreSQL: Active alerts table
 * - ClickHouse: Historical alerts for analytics
 * - Redis: Active alerts cache with 1-hour TTL
 * - Elasticsearch: Searchable alert logs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VesselAlertConsumer {

    private final AlertPersistenceService alertPersistenceService;

    @KafkaListener(
        topics = "${kafka.topics.vessel-alerts}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeVesselAlerts(
            @Payload List<JsonNode> alerts,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received {} vessel alerts from partition {} at offset {}", 
                alerts.size(), partition, offset);

            // Process batch
            alertPersistenceService.persistVesselAlerts(alerts);

            // Manual commit after successful processing
            acknowledgment.acknowledge();

            log.info("Successfully processed {} vessel alerts", alerts.size());

        } catch (Exception e) {
            log.error("Error processing vessel alerts from partition {} at offset {}: {}", 
                partition, offset, e.getMessage(), e);
            // Don't acknowledge on error - will retry
        }
    }
}
