package com.kreasipositif.vms.persistence.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.kreasipositif.vms.persistence.service.WeatherPersistenceService;
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
 * Kafka Consumer for weather-data topic
 * 
 * Consumes weather data and persists to:
 * - PostgreSQL: Current weather per grid cell (upsert)
 * - ClickHouse: Historical weather data
 * - Redis: Weather cache with 30-min TTL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherDataConsumer {

    private final WeatherPersistenceService weatherPersistenceService;

    @KafkaListener(
        topics = "${kafka.topics.weather-data}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeWeatherData(
            @Payload List<JsonNode> weatherData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.debug("Received {} weather records from partition {} at offset {}", 
                weatherData.size(), partition, offset);

            // Process batch
            weatherPersistenceService.persistWeatherData(weatherData);

            // Manual commit after successful processing
            acknowledgment.acknowledge();

            log.debug("Successfully processed {} weather records", weatherData.size());

        } catch (Exception e) {
            log.error("Error processing weather data from partition {} at offset {}: {}", 
                partition, offset, e.getMessage(), e);
        }
    }
}
