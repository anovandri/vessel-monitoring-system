package com.kreasipositif.vms.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Raw AIS message received from Kafka (ais-raw-data topic).
 * This represents unvalidated, unenriched vessel position data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AISMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Vessel Identification
    private Long mmsi;              // Maritime Mobile Service Identity
    private String vesselName;
    private String imo;             // International Maritime Organization number
    private String callSign;
    
    // Position Data
    private Double latitude;
    private Double longitude;
    private Double speed;           // Speed Over Ground (knots)
    private Double course;          // Course Over Ground (degrees)
    private Integer heading;        // True heading (degrees)
    
    // Status
    private String status;          // Navigational status (e.g., "Under way using engine")
    
    // Timestamps
    private Instant timestamp;      // Position timestamp
    private Instant collectionTime; // When data was collected
    
    // Metadata
    private String source;          // Data source (mock-ais-collector, ais-stream-collector)
    private String collectorId;
    
    /**
     * Check if this message has valid coordinates.
     */
    public boolean hasValidCoordinates() {
        return latitude != null && longitude != null &&
               latitude >= -90 && latitude <= 90 &&
               longitude >= -180 && longitude <= 180;
    }
    
    /**
     * Check if this message has valid speed.
     */
    public boolean hasValidSpeed() {
        return speed != null && speed >= 0 && speed <= 102.3; // Max vessel speed in knots
    }
    
    /**
     * Check if MMSI is valid (9 digits).
     */
    public boolean hasValidMMSI() {
        return mmsi != null && mmsi > 0;
    }
}
