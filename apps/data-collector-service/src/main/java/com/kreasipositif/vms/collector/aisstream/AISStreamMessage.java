package com.kreasipositif.vms.collector.aisstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kreasipositif.vms.collector.model.AISMessage;
import lombok.Data;

import java.time.Instant;

/**
 * DTO for AIS Stream WebSocket messages
 * Matches the format from aisstream.io API
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AISStreamMessage {
    
    @JsonProperty("MessageType")
    private String messageType;
    
    @JsonProperty("MetaData")
    private MetaData metaData;
    
    @JsonProperty("Message")
    private Message message;
    
    /**
     * Convert AIS Stream message to internal AISMessage format
     */
    public AISMessage toAISMessage() {
        AISMessage ais = new AISMessage();
        
        if (metaData != null) {
            ais.setMmsi(metaData.mmsi != null ? metaData.mmsi.intValue() : null);
            ais.setVesselName(metaData.shipName != null ? metaData.shipName : "Unknown");
            ais.setImo(metaData.imoNumber != null ? String.valueOf(metaData.imoNumber) : null);
            ais.setCallSign(metaData.callSign);
            ais.setVesselType(metaData.shipType);
        }
        
        if (message != null && message.positionReport != null) {
            PositionReport pos = message.positionReport;
            ais.setLatitude(pos.latitude);
            ais.setLongitude(pos.longitude);
            ais.setSpeed(pos.sog != null ? pos.sog : 0.0);
            ais.setCourse(pos.cog != null ? pos.cog : 0.0);
            ais.setHeading(pos.trueHeading);
            ais.setNavigationStatus(pos.navigationalStatus);
            
            if (pos.timestamp != null) {
                ais.setTimestamp(Instant.parse(pos.timestamp));
            } else {
                ais.setTimestamp(Instant.now());
            }
        }
        
        if (message != null && message.staticDataReport != null) {
            StaticDataReport sdr = message.staticDataReport;
            ais.setDestination(sdr.destination);
            if (sdr.dimension != null) {
                ais.setLength(Double.valueOf((sdr.dimension.a != null ? sdr.dimension.a : 0) + 
                             (sdr.dimension.b != null ? sdr.dimension.b : 0)));
                ais.setWidth(Double.valueOf((sdr.dimension.c != null ? sdr.dimension.c : 0) + 
                            (sdr.dimension.d != null ? sdr.dimension.d : 0)));
            }
        }
        
        // Set timestamp if not already set
        if (ais.getTimestamp() == null) {
            ais.setTimestamp(Instant.now());
        }
        
        return ais;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetaData {
        @JsonProperty("MMSI")
        private Long mmsi;
        
        @JsonProperty("ShipName")
        private String shipName;
        
        @JsonProperty("IMO")
        private Long imoNumber;
        
        @JsonProperty("CallSign")
        private String callSign;
        
        @JsonProperty("ShipType")
        private Integer shipType;
        
        @JsonProperty("time_utc")
        private String timeUtc;
        
        @JsonProperty("latitude")
        private Double latitude;
        
        @JsonProperty("longitude")
        private Double longitude;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        @JsonProperty("PositionReport")
        private PositionReport positionReport;
        
        @JsonProperty("StaticDataReport")
        private StaticDataReport staticDataReport;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PositionReport {
        @JsonProperty("Latitude")
        private Double latitude;
        
        @JsonProperty("Longitude")
        private Double longitude;
        
        @JsonProperty("Sog")
        private Double sog; // Speed over ground
        
        @JsonProperty("Cog")
        private Double cog; // Course over ground
        
        @JsonProperty("TrueHeading")
        private Integer trueHeading;
        
        @JsonProperty("NavigationalStatus")
        private Integer navigationalStatus;
        
        @JsonProperty("Timestamp")
        private String timestamp;
        
        @JsonProperty("RateOfTurn")
        private Integer rateOfTurn;
        
        @JsonProperty("SpecialManoeuvreIndicator")
        private Integer specialManoeuvreIndicator;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StaticDataReport {
        @JsonProperty("Destination")
        private String destination;
        
        @JsonProperty("Eta")
        private String eta;
        
        @JsonProperty("Dimension")
        private Dimension dimension;
        
        @JsonProperty("MaximumStaticDraught")
        private Double maximumStaticDraught;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dimension {
        @JsonProperty("A")
        private Integer a; // Distance from ref point to bow
        
        @JsonProperty("B")
        private Integer b; // Distance from ref point to stern
        
        @JsonProperty("C")
        private Integer c; // Distance from ref point to port
        
        @JsonProperty("D")
        private Integer d; // Distance from ref point to starboard
    }
}
