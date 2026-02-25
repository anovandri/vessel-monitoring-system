package com.kreasipositif.vms.collector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * AIS (Automatic Identification System) message model.
 * Represents vessel position and identification data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AISMessage {

    /**
     * Maritime Mobile Service Identity - unique vessel identifier
     */
    @JsonProperty("mmsi")
    private Integer mmsi;

    /**
     * IMO ship identification number
     */
    @JsonProperty("imo")
    private String imo;

    /**
     * Vessel name
     */
    @JsonProperty("vessel_name")
    private String vesselName;

    /**
     * Call sign
     */
    @JsonProperty("call_sign")
    private String callSign;

    /**
     * Latitude in decimal degrees
     */
    @JsonProperty("latitude")
    private Double latitude;

    /**
     * Longitude in decimal degrees
     */
    @JsonProperty("longitude")
    private Double longitude;

    /**
     * Speed over ground in knots
     */
    @JsonProperty("speed")
    private Double speed;

    /**
     * Course over ground in degrees
     */
    @JsonProperty("course")
    private Double course;

    /**
     * Heading in degrees
     */
    @JsonProperty("heading")
    private Integer heading;

    /**
     * Navigation status (0-15)
     * 0: Under way using engine
     * 1: At anchor
     * 2: Not under command
     * 3: Restricted manoeuverability
     * 5: Moored
     * etc.
     */
    @JsonProperty("navigation_status")
    private Integer navigationStatus;

    /**
     * Vessel type code
     */
    @JsonProperty("vessel_type")
    private Integer vesselType;

    /**
     * Length of vessel in meters
     */
    @JsonProperty("length")
    private Double length;

    /**
     * Width of vessel in meters
     */
    @JsonProperty("width")
    private Double width;

    /**
     * Draught in meters
     */
    @JsonProperty("draught")
    private Double draught;

    /**
     * Destination port
     */
    @JsonProperty("destination")
    private String destination;

    /**
     * Estimated time of arrival
     */
    @JsonProperty("eta")
    private Instant eta;

    /**
     * Flag country (ISO 2-letter code)
     */
    @JsonProperty("flag_country")
    private String flagCountry;

    /**
     * Message timestamp
     */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /**
     * Data source (e.g., "satellite", "terrestrial", "api")
     */
    @JsonProperty("source")
    private String source;

    /**
     * Rate of turn (ROT) in degrees per minute
     */
    @JsonProperty("rot")
    private Double rateOfTurn;

    /**
     * Get human-readable navigation status
     */
    public String getNavigationStatusDescription() {
        if (navigationStatus == null) return "Unknown";
        
        return switch (navigationStatus) {
            case 0 -> "Under way using engine";
            case 1 -> "At anchor";
            case 2 -> "Not under command";
            case 3 -> "Restricted manoeuverability";
            case 4 -> "Constrained by draught";
            case 5 -> "Moored";
            case 6 -> "Aground";
            case 7 -> "Engaged in fishing";
            case 8 -> "Under way sailing";
            case 15 -> "Not defined";
            default -> "Reserved";
        };
    }

    /**
     * Validate AIS message data
     */
    public boolean isValid() {
        return mmsi != null 
                && mmsi > 0
                && latitude != null 
                && longitude != null
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180
                && timestamp != null;
    }
}
