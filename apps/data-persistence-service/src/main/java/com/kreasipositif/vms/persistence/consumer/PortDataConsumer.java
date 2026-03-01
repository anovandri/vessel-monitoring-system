package com.kreasipositif.vms.persistence.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.kreasipositif.vms.persistence.service.PortPersistenceService;
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
 * Kafka Consumer for port-data topic
 * 
 * Consumes port operational data and persists to:
 * - PostgreSQL: Port master data, vessel-berth assignments
 * - ClickHouse: Port events history, traffic statistics
 * - Redis: Live port operations with 10-min TTL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortDataConsumer {

    private final PortPersistenceService portPersistenceService;

    @KafkaListener(
        topics = "${kafka.topics.port-data}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePortData(
            @Payload List<JsonNode> portData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Received {} port records from partition {} at offset {}", 
                portData.size(), partition, offset);

            // Process batch
            portPersistenceService.persistPortData(portData);

            // Manual commit after successful processing
            acknowledgment.acknowledge();

            log.debug("Successfully processed {} port records", portData.size());

        } catch (Exception e) {
            log.error("Error processing port data from partition {} at offset {}: {}", 
                partition, offset, e.getMessage(), e);
        }
    }
}
