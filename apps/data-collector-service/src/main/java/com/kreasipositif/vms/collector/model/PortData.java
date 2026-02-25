package com.kreasipositif.vms.collector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Port data model for port information and vessel schedules.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortData {

    /**
     * Port identification code (e.g., "SGSIN" for Singapore)
     */
    @JsonProperty("port_code")
    private String portCode;

    /**
     * Port name
     */
    @JsonProperty("port_name")
    private String portName;

    /**
     * Country code (ISO 2-letter)
     */
    @JsonProperty("country_code")
    private String countryCode;

    /**
     * Port latitude
     */
    @JsonProperty("latitude")
    private Double latitude;

    /**
     * Port longitude
     */
    @JsonProperty("longitude")
    private Double longitude;

    /**
     * Port timezone (e.g., "Asia/Singapore")
     */
    @JsonProperty("timezone")
    private String timezone;

    /**
     * Port status (e.g., "OPERATIONAL", "CLOSED", "RESTRICTED")
     */
    @JsonProperty("status")
    private String status;

    /**
     * Number of berths available
     */
    @JsonProperty("berth_count")
    private Integer berthCount;

    /**
     * Number of vessels currently in port
     */
    @JsonProperty("vessels_in_port")
    private Integer vesselsInPort;

    /**
     * Number of vessels expected (arrivals scheduled)
     */
    @JsonProperty("expected_arrivals")
    private Integer expectedArrivals;

    /**
     * Number of vessels departing soon
     */
    @JsonProperty("expected_departures")
    private Integer expectedDepartures;

    /**
     * Port congestion level (0-100)
     */
    @JsonProperty("congestion_level")
    private Integer congestionLevel;

    /**
     * Average wait time in hours
     */
    @JsonProperty("avg_wait_time")
    private Double avgWaitTime;

    /**
     * Maximum vessel length allowed in meters
     */
    @JsonProperty("max_vessel_length")
    private Double maxVesselLength;

    /**
     * Maximum vessel draught in meters
     */
    @JsonProperty("max_draught")
    private Double maxDraught;

    /**
     * Port facilities (list of available services)
     */
    @JsonProperty("facilities")
    private List<String> facilities;

    /**
     * Vessel schedules (arrivals and departures)
     */
    @JsonProperty("schedules")
    private List<VesselSchedule> schedules;

    /**
     * Tidal information
     */
    @JsonProperty("tide_info")
    private TideInfo tideInfo;

    /**
     * Port contact information
     */
    @JsonProperty("contact_email")
    private String contactEmail;

    @JsonProperty("contact_phone")
    private String contactPhone;

    /**
     * Website URL
     */
    @JsonProperty("website")
    private String website;

    /**
     * Data timestamp
     */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /**
     * Data source
     */
    @JsonProperty("source")
    private String source;

    /**
     * Nested class for vessel schedule
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VesselSchedule {
        @JsonProperty("mmsi")
        private Integer mmsi;

        @JsonProperty("vessel_name")
        private String vesselName;

        @JsonProperty("imo")
        private String imo;

        @JsonProperty("event_type")
        private String eventType; // "ARRIVAL" or "DEPARTURE"

        @JsonProperty("scheduled_time")
        private Instant scheduledTime;

        @JsonProperty("estimated_time")
        private Instant estimatedTime;

        @JsonProperty("actual_time")
        private Instant actualTime;

        @JsonProperty("berth_number")
        private String berthNumber;

        @JsonProperty("cargo_type")
        private String cargoType;

        @JsonProperty("status")
        private String status; // "SCHEDULED", "DELAYED", "ARRIVED", "DEPARTED"
    }

    /**
     * Nested class for tide information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TideInfo {
        @JsonProperty("current_height")
        private Double currentHeight; // meters

        @JsonProperty("next_high_tide")
        private Instant nextHighTide;

        @JsonProperty("next_low_tide")
        private Instant nextLowTide;

        @JsonProperty("high_tide_height")
        private Double highTideHeight;

        @JsonProperty("low_tide_height")
        private Double lowTideHeight;
    }

    /**
     * Check if port is operational
     */
    public boolean isOperational() {
        return "OPERATIONAL".equalsIgnoreCase(status);
    }

    /**
     * Check if port is congested
     */
    public boolean isCongested() {
        return congestionLevel != null && congestionLevel > 70;
    }

    /**
     * Get congestion status description
     */
    public String getCongestionStatus() {
        if (congestionLevel == null) return "Unknown";
        if (congestionLevel < 30) return "Low";
        if (congestionLevel < 70) return "Moderate";
        return "High";
    }

    /**
     * Validate port data
     */
    public boolean isValid() {
        return portCode != null 
                && !portCode.isBlank()
                && latitude != null 
                && longitude != null
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180
                && timestamp != null;
    }
}
