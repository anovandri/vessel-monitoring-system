package com.kreasipositif.vms.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kreasipositif.vms.collector.core.CollectedData;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for publishing collected data to Kafka topics.
 * Uses Virtual Threads for efficient parallel publishing.
 */
@Slf4j
@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter successCounter;
    private final Counter failureCounter;

    public KafkaProducerService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.successCounter = Counter.builder("kafka.publish.success")
                .description("Number of successful Kafka publishes")
                .register(meterRegistry);
                
        this.failureCounter = Counter.builder("kafka.publish.failure")
                .description("Number of failed Kafka publishes")
                .register(meterRegistry);
    }

    /**
     * Publish a single data record to Kafka
     */
    public CompletableFuture<SendResult<String, String>> publishAsync(
            String topic, 
            CollectedData data) {
        
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            String key = data.getId(); // Use data ID as message key for partitioning
            
            log.debug("Publishing data to topic: {} with key: {}", topic, key);
            
            return kafkaTemplate.send(topic, key, jsonData)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            successCounter.increment();
                            log.debug("Published to {}: partition={}, offset={}",
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        } else {
                            failureCounter.increment();
                            log.error("Failed to publish to {}: {}", topic, ex.getMessage());
                        }
                    });
                    
        } catch (Exception e) {
            failureCounter.increment();
            log.error("Error preparing data for Kafka: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Publish multiple data records in parallel using Virtual Threads
     */
    public CompletableFuture<Void> publishBatchAsync(
            String topic, 
            List<CollectedData> dataList) {
        
        log.info("Publishing batch of {} records to topic: {}", dataList.size(), topic);
        
        // Create array of futures for parallel publishing
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>>[] futures = dataList.stream()
                .map(data -> publishAsync(topic, data))
                .toArray(CompletableFuture[]::new);
        
        // Wait for all to complete
        return CompletableFuture.allOf(futures)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Successfully published {} records to {}", dataList.size(), topic);
                    } else {
                        log.error("Some records failed to publish to {}: {}", topic, ex.getMessage());
                    }
                });
    }

    /**
     * Synchronous publish (blocks)
     */
    public void publish(String topic, CollectedData data) {
        try {
            publishAsync(topic, data).get();
        } catch (Exception e) {
            log.error("Failed to publish synchronously: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka publish failed", e);
        }
    }

    /**
     * Synchronous batch publish
     */
    public void publishBatch(String topic, List<CollectedData> dataList) {
        try {
            publishBatchAsync(topic, dataList).get();
        } catch (Exception e) {
            log.error("Failed to publish batch synchronously: {}", e.getMessage(), e);
            throw new RuntimeException("Kafka batch publish failed", e);
        }
    }
}
