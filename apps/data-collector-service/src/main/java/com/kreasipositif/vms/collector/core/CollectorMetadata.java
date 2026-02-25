package com.kreasipositif.vms.collector.core;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Metadata about a collector including its type, capabilities, and configuration.
 * Used by the Registry pattern to track available collectors.
 */
@Data
@Builder
public class CollectorMetadata {

    /**
     * Unique identifier for the collector (e.g., "ais", "weather", "port-data")
     */
    private String id;

    /**
     * Human-readable name
     */
    private String name;

    /**
     * Description of what this collector does
     */
    private String description;

    /**
     * Data type this collector produces (e.g., "AIS_RAW", "WEATHER", "PORT_INFO")
     */
    private CollectorDataType dataType;

    /**
     * Expected poll interval
     */
    private Duration pollInterval;

    /**
     * Is this collector currently enabled?
     */
    private boolean enabled;

    /**
     * Priority (higher = more important)
     */
    private int priority;

    /**
     * Collector version
     */
    private String version;

    /**
     * Kafka topic to publish data to
     */
    private String kafkaTopic;

    public enum CollectorDataType {
        AIS_RAW,
        WEATHER,
        PORT_DATA,
        SATELLITE_AIS,
        TERRESTRIAL_AIS,
        CUSTOM
    }
}
