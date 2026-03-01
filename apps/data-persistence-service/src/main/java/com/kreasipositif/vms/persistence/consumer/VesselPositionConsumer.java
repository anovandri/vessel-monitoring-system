package com.kreasipositif.vms.persistence.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.kreasipositif.vms.persistence.service.VesselPersistenceService;
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
 * Kafka Consumer for vessel-positions topic
 * 
 * Consumes enriched vessel position data from Flink and persists to:
 * - PostgreSQL: Current vessel positions (upsert by MMSI)
 * - ClickHouse: Historical position snapshots
 * - Redis: Latest position cache with 5-min TTL
 * - Elasticsearch: Searchable position logs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VesselPositionConsumer {

    private final VesselPersistenceService vesselPersistenceService;

    @KafkaListener(
        topics = "${kafka.topics.vessel-positions}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeVesselPositions(
            @Payload List<JsonNode> positions,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received {} vessel positions from partition {} at offset {}", 
                positions.size(), partition, offset);
            
            // Log first message for debugging (only if debug is enabled)
            if (log.isDebugEnabled() && !positions.isEmpty()) {
                log.debug("Sample message from Kafka: {}", positions.get(0).toPrettyString());
            }

            // Process batch
            vesselPersistenceService.persistVesselPositions(positions);

            // Manual commit after successful processing
            acknowledgment.acknowledge();

            log.info("Successfully processed {} vessel positions", positions.size());

        } catch (Exception e) {
            log.error("Error processing vessel positions from partition {} at offset {}: {}", 
                partition, offset, e.getMessage(), e);
            // Don't acknowledge on error - will retry based on Kafka consumer config
        }
    }
}
