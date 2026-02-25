package com.kreasipositif.vms.collector.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Generic data container for collected data.
 * All collectors produce CollectedData instances which are then published to Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectedData {

    /**
     * Unique identifier for this data record
     */
    private String id;

    /**
     * Type of data (matches CollectorMetadata.dataType)
     */
    private CollectorMetadata.CollectorDataType dataType;

    /**
     * Source collector ID
     */
    private String collectorId;

    /**
     * Timestamp when data was collected
     */
    private Instant timestamp;

    /**
     * Raw data payload (JSON or other format)
     */
    private String rawData;

    /**
     * Parsed/structured data (optional, can be used before Kafka)
     */
    private Object parsedData;

    /**
     * Additional metadata about the collection
     */
    private Map<String, String> metadata;

    /**
     * Quality score (0-100)
     */
    private int qualityScore;

    /**
     * Data source (e.g., API endpoint, file path)
     */
    private String source;
}
