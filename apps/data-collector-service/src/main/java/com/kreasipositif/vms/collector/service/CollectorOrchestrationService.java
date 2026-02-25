package com.kreasipositif.vms.collector.service;

import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorRegistry;
import com.kreasipositif.vms.collector.core.DataCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestration service for running data collectors on schedule.
 * Uses Virtual Threads for parallel execution of multiple collectors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorOrchestrationService {

    private final CollectorRegistry collectorRegistry;
    private final KafkaProducerService kafkaProducerService;

    @PostConstruct
    public void init() {
        log.info("Collector Orchestration Service initialized");
        log.info("Registered collectors: {}", collectorRegistry.getCollectorCount());
        log.info("Enabled collectors: {}", collectorRegistry.getEnabledCollectorCount());
    }

    /**
     * Run all enabled collectors - scheduled every 10 seconds
     * Uses Virtual Threads for parallel execution
     */
    @Scheduled(fixedDelayString = "${vms.collector.schedule.interval:10000}")
    public void runAllCollectors() {
        log.info("Starting scheduled collection cycle...");
        
        List<DataCollector> enabledCollectors = collectorRegistry.getEnabledCollectors();
        
        if (enabledCollectors.isEmpty()) {
            log.warn("No enabled collectors found");
            return;
        }
        
        // Run all collectors in parallel using Virtual Threads
        List<CompletableFuture<Void>> futures = enabledCollectors.stream()
                .map(this::runCollectorAsync)
                .toList();
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Collection cycle completed successfully");
                    } else {
                        log.error("Collection cycle completed with errors: {}", ex.getMessage());
                    }
                });
    }

    /**
     * Run a single collector asynchronously
     */
    private CompletableFuture<Void> runCollectorAsync(DataCollector collector) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Running collector: {}", collector.getMetadata().getName());
                
                // Collect data
                List<CollectedData> data = collector.collect();
                
                if (data != null && !data.isEmpty()) {
                    // Publish to Kafka
                    String topic = collector.getMetadata().getKafkaTopic();
                    kafkaProducerService.publishBatch(topic, data);
                    
                    log.info("Collector {} collected and published {} records to topic: {}",
                            collector.getMetadata().getName(), data.size(), topic);
                } else {
                    log.debug("Collector {} collected no data", collector.getMetadata().getName());
                }
                
            } catch (Exception e) {
                log.error("Error running collector {}: {}",
                        collector.getMetadata().getName(), e.getMessage(), e);
            }
        }, virtualThreadExecutor());
    }

    /**
     * Run a specific collector by ID
     */
    public void runCollector(String collectorId) {
        collectorRegistry.getCollector(collectorId).ifPresentOrElse(
                collector -> {
                    log.info("Manually running collector: {}", collectorId);
                    runCollectorAsync(collector);
                },
                () -> log.warn("Collector not found: {}", collectorId)
        );
    }

    /**
     * Get Virtual Thread executor
     */
    private java.util.concurrent.Executor virtualThreadExecutor() {
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Health check for all collectors
     */
    public boolean isHealthy() {
        return collectorRegistry.isHealthy();
    }

    /**
     * Get collector statistics
     */
    public CollectorStats getStats() {
        return CollectorStats.builder()
                .totalCollectors(collectorRegistry.getCollectorCount())
                .enabledCollectors(collectorRegistry.getEnabledCollectorCount())
                .healthStatus(collectorRegistry.getHealthStatus())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class CollectorStats {
        private int totalCollectors;
        private int enabledCollectors;
        private java.util.Map<String, Boolean> healthStatus;
    }
}
