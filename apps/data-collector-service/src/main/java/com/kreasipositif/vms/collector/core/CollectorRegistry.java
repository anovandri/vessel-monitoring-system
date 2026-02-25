package com.kreasipositif.vms.collector.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry pattern implementation for managing all available collectors.
 * Provides centralized access to collectors and their metadata.
 */
@Slf4j
@Component("dataCollectorRegistry")
@RequiredArgsConstructor
public class CollectorRegistry {

    private final Map<String, DataCollector> collectors = new ConcurrentHashMap<>();
    private final Map<String, CollectorMetadata> metadata = new ConcurrentHashMap<>();

    /**
     * Register a collector with the registry
     */
    public void register(DataCollector collector) {
        CollectorMetadata meta = collector.getMetadata();
        String id = meta.getId();
        
        if (collectors.containsKey(id)) {
            throw new IllegalStateException("Collector with ID '" + id + "' is already registered");
        }
        
        collectors.put(id, collector);
        metadata.put(id, meta);
        
        log.info("Registered collector: {} (type: {}, enabled: {})", 
                meta.getName(), meta.getDataType(), meta.isEnabled());
    }

    /**
     * Unregister a collector
     */
    public void unregister(String collectorId) {
        DataCollector removed = collectors.remove(collectorId);
        metadata.remove(collectorId);
        
        if (removed != null) {
            log.info("Unregistered collector: {}", collectorId);
        }
    }

    /**
     * Get a specific collector by ID
     */
    public Optional<DataCollector> getCollector(String collectorId) {
        return Optional.ofNullable(collectors.get(collectorId));
    }

    /**
     * Get all registered collectors
     */
    public Collection<DataCollector> getAllCollectors() {
        return Collections.unmodifiableCollection(collectors.values());
    }

    /**
     * Get only enabled collectors
     */
    public List<DataCollector> getEnabledCollectors() {
        return collectors.values().stream()
                .filter(collector -> collector.getMetadata().isEnabled())
                .collect(Collectors.toList());
    }

    /**
     * Get collectors by data type
     */
    public List<DataCollector> getCollectorsByType(CollectorMetadata.CollectorDataType dataType) {
        return collectors.values().stream()
                .filter(collector -> collector.getMetadata().getDataType() == dataType)
                .collect(Collectors.toList());
    }

    /**
     * Get collector metadata by ID
     */
    public Optional<CollectorMetadata> getMetadata(String collectorId) {
        return Optional.ofNullable(metadata.get(collectorId));
    }

    /**
     * Get all collector metadata
     */
    public Collection<CollectorMetadata> getAllMetadata() {
        return Collections.unmodifiableCollection(metadata.values());
    }

    /**
     * Check if a collector is registered
     */
    public boolean isRegistered(String collectorId) {
        return collectors.containsKey(collectorId);
    }

    /**
     * Get count of registered collectors
     */
    public int getCollectorCount() {
        return collectors.size();
    }

    /**
     * Get count of enabled collectors
     */
    public int getEnabledCollectorCount() {
        return (int) collectors.values().stream()
                .filter(collector -> collector.getMetadata().isEnabled())
                .count();
    }

    /**
     * Get collectors sorted by priority (highest first)
     */
    public List<DataCollector> getCollectorsByPriority() {
        return collectors.values().stream()
                .sorted((c1, c2) -> Integer.compare(
                        c2.getMetadata().getPriority(), 
                        c1.getMetadata().getPriority()))
                .collect(Collectors.toList());
    }

    /**
     * Health check - are all enabled collectors healthy?
     */
    public boolean isHealthy() {
        return getEnabledCollectors().stream()
                .allMatch(DataCollector::isHealthy);
    }

    /**
     * Get health status summary
     */
    public Map<String, Boolean> getHealthStatus() {
        return collectors.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().isHealthy()
                ));
    }

    @PostConstruct
    public void init() {
        log.info("CollectorRegistry initialized");
    }
}
