package com.kreasipositif.vms.collector.core;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for all data collectors.
 * Implements the Template Method pattern - subclasses override specific methods.
 * Uses Virtual Threads for efficient concurrent execution.
 */
@Slf4j
public abstract class DataCollector {

    private final MeterRegistry meterRegistry;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Timer collectionTimer;
    private final CollectorMetadata metadata;

    protected DataCollector(MeterRegistry meterRegistry, CollectorMetadata metadata) {
        this.meterRegistry = meterRegistry;
        this.metadata = metadata;
        String collectorId = metadata.getId();
        
        this.successCounter = Counter.builder("collector.success")
                .tag("collector", collectorId)
                .description("Number of successful collections")
                .register(meterRegistry);
                
        this.failureCounter = Counter.builder("collector.failure")
                .tag("collector", collectorId)
                .description("Number of failed collections")
                .register(meterRegistry);
                
        this.collectionTimer = Timer.builder("collector.duration")
                .tag("collector", collectorId)
                .description("Time taken to collect data")
                .register(meterRegistry);
    }

    /**
     * Get metadata about this collector.
     */
    public CollectorMetadata getMetadata() {
        return metadata;
    }

    /**
     * Template method for collecting data.
     * This orchestrates the collection process and handles common concerns.
     * Uses Virtual Threads for async execution.
     */
    public CompletableFuture<List<CollectedData>> collectAsync() {
        log.debug("Starting async collection for collector: {}", getMetadata().getId());
        
        return CompletableFuture.supplyAsync(() -> {
            return collectionTimer.record(() -> {
                try {
                    // Pre-collection hook
                    preCollect();
                    
                    // Actual data collection (implemented by subclass)
                    List<CollectedData> data = doCollect();
                    
                    // Post-collection processing
                    List<CollectedData> processedData = postCollect(data);
                    
                    // Enrich with common metadata
                    processedData.forEach(this::enrichData);
                    
                    successCounter.increment();
                    log.info("Successfully collected {} records from {}", 
                            processedData.size(), getMetadata().getId());
                    
                    return processedData;
                    
                } catch (Exception e) {
                    failureCounter.increment();
                    log.error("Failed to collect data from {}: {}", 
                            getMetadata().getId(), e.getMessage(), e);
                    handleCollectionError(e);
                    throw new CollectionException("Collection failed for " + getMetadata().getId(), e);
                }
            });
        }, virtualThreadExecutor());
    }

    /**
     * Synchronous collection method (calls async and waits)
     */
    @Retryable(
        retryFor = {CollectionException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000)
    )
    public List<CollectedData> collect() {
        try {
            return collectAsync().join();
        } catch (Exception e) {
            log.error("Synchronous collection failed for {}", getMetadata().getId(), e);
            throw new CollectionException("Collection failed", e);
        }
    }

    /**
     * Core collection logic - subclasses implement this
     */
    protected abstract List<CollectedData> doCollect() throws Exception;

    /**
     * Hook: Called before collection starts
     */
    protected void preCollect() {
        log.debug("Pre-collect hook for {}", getMetadata().getId());
    }

    /**
     * Hook: Called after collection, before returning data
     */
    protected List<CollectedData> postCollect(List<CollectedData> data) {
        log.debug("Post-collect hook for {} with {} records", getMetadata().getId(), data.size());
        return data;
    }

    /**
     * Enrich collected data with standard fields
     */
    private void enrichData(CollectedData data) {
        if (data.getId() == null) {
            data.setId(UUID.randomUUID().toString());
        }
        if (data.getTimestamp() == null) {
            data.setTimestamp(Instant.now());
        }
        if (data.getCollectorId() == null) {
            data.setCollectorId(getMetadata().getId());
        }
        if (data.getDataType() == null) {
            data.setDataType(getMetadata().getDataType());
        }
    }

    /**
     * Handle collection errors - can be overridden for custom error handling
     */
    protected void handleCollectionError(Exception e) {
        log.error("Collection error in {}: {}", getMetadata().getId(), e.getMessage());
    }

    /**
     * Validate collected data - can be overridden
     */
    protected boolean validateData(CollectedData data) {
        return data != null 
                && data.getRawData() != null 
                && !data.getRawData().isBlank();
    }

    /**
     * Check if this collector is healthy and ready to collect
     */
    public boolean isHealthy() {
        return getMetadata().isEnabled();
    }

    /**
     * Get Virtual Thread executor for this collector
     * Java 21 feature - lightweight threads
     */
    private java.util.concurrent.Executor virtualThreadExecutor() {
        return java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Custom exception for collection failures
     */
    public static class CollectionException extends RuntimeException {
        public CollectionException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public CollectionException(String message) {
            super(message);
        }
    }
}
