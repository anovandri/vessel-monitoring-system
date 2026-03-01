package com.kreasipositif.vms.processor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Alert generated during stream processing.
 * Published to vessel-alerts topic for real-time notification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VesselAlert implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // Alert Identification
    private String alertId;
    private AlertType alertType;
    private AlertSeverity severity;
    
    // Vessel Information
    private Long mmsi;
    private String vesselName;
    private List<Long> involvedMMSIs;  // For multi-vessel alerts (collision)
    
    // Location
    private Double latitude;
    private Double longitude;
    
    // Alert Details
    private String title;
    private String description;
    private Map<String, Object> details;  // Flexible alert-specific data
    
    // Timestamps
    private Instant detectedAt;
    private Instant alertTime;
    
    // Status
    private Boolean actionRequired;
    private Boolean acknowledged;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    
    /**
     * Alert types supported by the system.
     */
    public enum AlertType {
        GEOFENCE_ENTRY,
        GEOFENCE_EXIT,
        GEOFENCE_VIOLATION,
        EXCESSIVE_SPEED,
        ERRATIC_SPEED,
        SUDDEN_STOP,
        COLLISION_RISK,
        DRIFT_DETECTED,
        IMPOSSIBLE_ACCELERATION,
        COURSE_DEVIATION,
        SIGNAL_LOST,
        RESTRICTED_AREA_ENTRY
    }
    
    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        INFO,      // Informational
        LOW,       // Low priority
        MEDIUM,    // Moderate attention needed
        HIGH,      // Urgent attention required
        CRITICAL   // Immediate action required
    }
}
