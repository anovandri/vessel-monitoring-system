package com.kreasipositif.vms.processor.functions;

import com.kreasipositif.vms.processor.config.FlinkConfig;
import com.kreasipositif.vms.processor.model.AISMessage;
import com.kreasipositif.vms.processor.model.EnrichedPosition;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Enriches AIS messages with vessel master data and calculates derived fields.
 * In production, this would lookup vessel data from PostgreSQL or Redis cache.
 * For demo, we'll use simplified enrichment.
 */
public class VesselEnrichmentFunction extends KeyedProcessFunction<Long, AISMessage, EnrichedPosition> {
    
    private static final Logger LOG = LoggerFactory.getLogger(VesselEnrichmentFunction.class);
    private final FlinkConfig config;
    
    // State to track previous position for distance calculation
    private transient ValueState<EnrichedPosition> previousPositionState;
    
    public VesselEnrichmentFunction(FlinkConfig config) {
        this.config = config;
    }
    
    @Override
    public void open(Configuration parameters) throws Exception {
        ValueStateDescriptor<EnrichedPosition> descriptor = new ValueStateDescriptor<>(
            "previous-position",
            EnrichedPosition.class
        );
        previousPositionState = getRuntimeContext().getState(descriptor);
    }
    
    @Override
    public void processElement(AISMessage message, Context ctx, Collector<EnrichedPosition> out) throws Exception {
        
        // Get previous position for distance calculation
        EnrichedPosition previousPosition = previousPositionState.value();
        
        // Calculate distance from previous position
        Double distanceFromPrevious = null;
        Long timeSinceLastUpdate = null;
        
        if (previousPosition != null) {
            distanceFromPrevious = calculateDistance(
                previousPosition.getLatitude(), previousPosition.getLongitude(),
                message.getLatitude(), message.getLongitude()
            );
            
            if (message.getTimestamp() != null && previousPosition.getTimestamp() != null) {
                timeSinceLastUpdate = message.getTimestamp().toEpochMilli() - 
                                     previousPosition.getTimestamp().toEpochMilli();
            }
        }
        
        // Build enriched position
        // In production, lookup vessel type, flag, destination from database
        EnrichedPosition enriched = EnrichedPosition.builder()
            .mmsi(message.getMmsi())
            .vesselName(message.getVesselName())
            .imo(message.getImo())
            .callSign(message.getCallSign())
            .latitude(message.getLatitude())
            .longitude(message.getLongitude())
            .speed(message.getSpeed())
            .course(message.getCourse())
            .heading(message.getHeading())
            .status(message.getStatus())
            // Enriched fields (mock for demo)
            .vesselType(inferVesselType(message))
            .flag("ID")  // Indonesian flag for demo vessels
            .destination("Unknown")
            .eta(null)
            .draught(null)
            .length(null)
            .width(null)
            // Calculated fields
            .distanceFromPrevious(distanceFromPrevious)
            .timeSinceLastUpdate(timeSinceLastUpdate)
            // Timestamps
            .timestamp(message.getTimestamp())
            .processedTime(Instant.now())
            // Metadata
            .source(message.getSource())
            .validated(true)
            .validationStatus("VALID")
            .build();
        
        // Update state with current position
        previousPositionState.update(enriched);
        
        // Emit enriched position
        out.collect(enriched);
    }
    
    /**
     * Calculate distance between two coordinates using Haversine formula.
     * Returns distance in nautical miles.
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 3440.065; // Earth radius in nautical miles
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
    
    /**
     * Infer vessel type from vessel name (simple heuristic for demo).
     * In production, lookup from vessel master database.
     */
    private String inferVesselType(AISMessage message) {
        if (message.getVesselName() == null) {
            return "UNKNOWN";
        }
        
        String name = message.getVesselName().toUpperCase();
        
        if (name.contains("FERRY") || name.contains("PELNI")) {
            return "PASSENGER";
        } else if (name.contains("TANKER") || name.contains("MT ")) {
            return "TANKER";
        } else if (name.contains("CARGO") || name.contains("MV ") || name.contains("MS ")) {
            return "CARGO";
        } else if (name.contains("FISH")) {
            return "FISHING";
        } else {
            return "OTHER";
        }
    }
}
