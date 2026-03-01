package com.kreasipositif.vms.processor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Wrapper for messages from data-collector-service.
 * This matches the structure sent by the data collector to Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectorMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String dataType;
    private String collectorId;
    private Instant timestamp;
    private String rawData;
    
    @JsonProperty("parsedData")
    private ParsedAISData parsedData;
    
    private Map<String, Object> metadata;
    private Integer qualityScore;
    private String source;
    
    /**
     * Nested parsed AIS data.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParsedAISData implements Serializable {
        
        private static final long serialVersionUID = 1L;
        
        private Boolean valid;
        private String navigationStatusDescription;
        
        // Vessel Identification
        private Long mmsi;
        private String imo;
        @JsonProperty("vessel_name")
        private String vesselName;
        @JsonProperty("call_sign")
        private String callSign;
        
        // Position Data
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double course;
        private Integer heading;
        
        // Status
        @JsonProperty("navigation_status")
        private Integer navigationStatus;
        
        @JsonProperty("vessel_type")
        private Integer vesselType;
        
        // Physical dimensions
        private Integer length;
        private Integer width;
        private Double draught;
        
        // Voyage data
        private String destination;
        private String eta;
        @JsonProperty("flag_country")
        private String flagCountry;
        
        // Timestamps
        private Instant timestamp;
        private String source;
        
        // Rate of turn
        private Double rot;
    }
    
    /**
     * Convert this collector message to an AISMessage for processing.
     */
    public AISMessage toAISMessage() {
        if (parsedData == null) {
            return null;
        }
        
        return AISMessage.builder()
                .mmsi(parsedData.getMmsi())
                .vesselName(parsedData.getVesselName())
                .imo(parsedData.getImo())
                .callSign(parsedData.getCallSign())
                .latitude(parsedData.getLatitude())
                .longitude(parsedData.getLongitude())
                .speed(parsedData.getSpeed())
                .course(parsedData.getCourse())
                .heading(parsedData.getHeading())
                .status(parsedData.getNavigationStatusDescription())
                .timestamp(parsedData.getTimestamp())
                .collectionTime(this.timestamp)
                .source(this.collectorId)
                .collectorId(this.collectorId)
                .build();
    }
}
