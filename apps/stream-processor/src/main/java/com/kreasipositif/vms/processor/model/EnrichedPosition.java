package com.kreasipositif.vms.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Enriched vessel position after validation and data enrichment.
 * This is published to vessel-positions topic and stored in databases.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedPosition implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Vessel Identification
    private Long mmsi;
    private String vesselName;
    private String imo;
    private String callSign;
    
    // Position Data
    private Double latitude;
    private Double longitude;
    private Double speed;
    private Double course;
    private Integer heading;
    private String status;
    
    // Enriched Vessel Data (from master data)
    private String vesselType;      // CARGO, TANKER, PASSENGER, FISHING, etc.
    private String flag;            // Country code (ISO 3166-1 alpha-2)
    private String destination;     // Destination port
    private Instant eta;            // Estimated Time of Arrival
    private Double draught;         // Current draught in meters
    private Integer length;         // Length in meters
    private Integer width;          // Width in meters
    
    // Calculated Fields
    private Double distanceFromPrevious;  // Distance traveled from last position (nautical miles)
    private Long timeSinceLastUpdate;     // Milliseconds since last position
    
    // Timestamps
    private Instant timestamp;
    private Instant processedTime;
    
    // Metadata
    private String source;
    private Boolean validated;
    private String validationStatus;
    
    /**
     * Calculate approximate cell tower distance for spatial partitioning.
     */
    public String getGridCell() {
        if (latitude == null || longitude == null) {
            return "UNKNOWN";
        }
        // Create 0.1 degree grid cells (~11km at equator)
        int latCell = (int) (latitude * 10);
        int lonCell = (int) (longitude * 10);
        return latCell + "_" + lonCell;
    }
}
