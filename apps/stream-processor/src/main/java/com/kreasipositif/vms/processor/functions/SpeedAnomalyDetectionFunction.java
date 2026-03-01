package com.kreasipositif.vms.processor.functions;

import com.kreasipositif.vms.processor.model.EnrichedPosition;
import com.kreasipositif.vms.processor.model.VesselAlert;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects speed anomalies (excessive speed, erratic speed, sudden stops).
 * Alerts are sent to side output stream.
 */
public class SpeedAnomalyDetectionFunction extends KeyedProcessFunction<Long, EnrichedPosition, EnrichedPosition> {
    
    private static final Logger LOG = LoggerFactory.getLogger(SpeedAnomalyDetectionFunction.class);
    
    // Thresholds
    private static final double MAX_CARGO_SPEED = 25.0;      // knots
    private static final double MAX_PASSENGER_SPEED = 35.0;  // knots
    private static final double MAX_TANKER_SPEED = 20.0;     // knots
    private static final double SUDDEN_STOP_THRESHOLD = 10.0; // Speed drop > 10 knots
    
    private final OutputTag<VesselAlert> alertsTag;
    
    // State
    private transient ValueState<EnrichedPosition> previousPositionState;
    private transient ValueState<Long> lastAlertTimeState;
    
    public SpeedAnomalyDetectionFunction(OutputTag<VesselAlert> alertsTag) {
        this.alertsTag = alertsTag;
    }
    
    @Override
    public void open(Configuration parameters) throws Exception {
        previousPositionState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("previous-position", EnrichedPosition.class)
        );
        lastAlertTimeState = getRuntimeContext().getState(
            new ValueStateDescriptor<>("last-alert-time", Long.class)
        );
    }
    
    @Override
    public void processElement(EnrichedPosition position, Context ctx, Collector<EnrichedPosition> out) throws Exception {
        
        EnrichedPosition previousPosition = previousPositionState.value();
        
        if (previousPosition != null && position.getSpeed() != null && previousPosition.getSpeed() != null) {
            
            // Check for excessive speed
            checkExcessiveSpeed(position, ctx);
            
            // Check for sudden stop
            checkSuddenStop(position, previousPosition, ctx);
            
            // Check for impossible acceleration
            checkImpossibleAcceleration(position, previousPosition, ctx);
        }
        
        // Update state
        previousPositionState.update(position);
        
        // Pass through position
        out.collect(position);
    }
    
    /**
     * Check if vessel is traveling too fast for its type.
     */
    private void checkExcessiveSpeed(EnrichedPosition position, Context ctx) throws Exception {
        double speed = position.getSpeed();
        double maxSpeed = getMaxSpeedForVesselType(position.getVesselType());
        
        if (speed > maxSpeed * 1.5) { // 1.5x normal max speed
            
            // Throttle alerts (don't spam)
            if (shouldSendAlert(ctx.timerService().currentProcessingTime())) {
                VesselAlert alert = VesselAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .alertType(VesselAlert.AlertType.EXCESSIVE_SPEED)
                    .severity(VesselAlert.AlertSeverity.HIGH)
                    .mmsi(position.getMmsi())
                    .vesselName(position.getVesselName())
                    .latitude(position.getLatitude())
                    .longitude(position.getLongitude())
                    .title("Excessive Speed Detected")
                    .description(String.format(
                        "Vessel %s traveling at %.1f knots (max expected: %.1f knots)",
                        position.getVesselName(), speed, maxSpeed
                    ))
                    .details(createSpeedAlertDetails(position, speed, maxSpeed))
                    .detectedAt(Instant.now())
                    .alertTime(position.getTimestamp())
                    .actionRequired(true)
                    .acknowledged(false)
                    .build();
                
                ctx.output(alertsTag, alert);
                updateLastAlertTime(ctx.timerService().currentProcessingTime());
                
                LOG.info("🚨 EXCESSIVE SPEED: MMSI={}, Speed={} knots (max={})", 
                    position.getMmsi(), speed, maxSpeed);
            }
        }
    }
    
    /**
     * Check for sudden stop (speed drops dramatically).
     */
    private void checkSuddenStop(EnrichedPosition current, EnrichedPosition previous, Context ctx) throws Exception {
        double currentSpeed = current.getSpeed();
        double previousSpeed = previous.getSpeed();
        
        if (previousSpeed > SUDDEN_STOP_THRESHOLD && currentSpeed < 1.0) {
            
            if (shouldSendAlert(ctx.timerService().currentProcessingTime())) {
                VesselAlert alert = VesselAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .alertType(VesselAlert.AlertType.SUDDEN_STOP)
                    .severity(VesselAlert.AlertSeverity.MEDIUM)
                    .mmsi(current.getMmsi())
                    .vesselName(current.getVesselName())
                    .latitude(current.getLatitude())
                    .longitude(current.getLongitude())
                    .title("Sudden Stop Detected")
                    .description(String.format(
                        "Vessel %s suddenly stopped from %.1f knots to %.1f knots",
                        current.getVesselName(), previousSpeed, currentSpeed
                    ))
                    .details(new java.util.HashMap<>(Map.of(
                        "previous_speed", previousSpeed,
                        "current_speed", currentSpeed,
                        "speed_drop", previousSpeed - currentSpeed
                    )))
                    .detectedAt(Instant.now())
                    .alertTime(current.getTimestamp())
                    .actionRequired(false)
                    .acknowledged(false)
                    .build();
                
                ctx.output(alertsTag, alert);
                updateLastAlertTime(ctx.timerService().currentProcessingTime());
                
                LOG.info("🚨 SUDDEN STOP: MMSI={}, Speed drop from {} to {} knots", 
                    current.getMmsi(), previousSpeed, currentSpeed);
            }
        }
    }
    
    /**
     * Check for impossible acceleration (beyond physical limits).
     */
    private void checkImpossibleAcceleration(EnrichedPosition current, EnrichedPosition previous, Context ctx) throws Exception {
        if (current.getTimeSinceLastUpdate() == null || current.getTimeSinceLastUpdate() == 0) {
            return;
        }
        
        double speedChange = Math.abs(current.getSpeed() - previous.getSpeed());
        double timeSeconds = current.getTimeSinceLastUpdate() / 1000.0;
        double acceleration = speedChange / timeSeconds; // knots per second
        
        // Max acceleration ~0.5 knots/sec for large vessels
        if (acceleration > 1.0) {
            
            if (shouldSendAlert(ctx.timerService().currentProcessingTime())) {
                VesselAlert alert = VesselAlert.builder()
                    .alertId(UUID.randomUUID().toString())
                    .alertType(VesselAlert.AlertType.IMPOSSIBLE_ACCELERATION)
                    .severity(VesselAlert.AlertSeverity.LOW)
                    .mmsi(current.getMmsi())
                    .vesselName(current.getVesselName())
                    .latitude(current.getLatitude())
                    .longitude(current.getLongitude())
                    .title("Impossible Acceleration Detected")
                    .description(String.format(
                        "Vessel %s acceleration rate: %.2f knots/sec (likely GPS error)",
                        current.getVesselName(), acceleration
                    ))
                    .details(new java.util.HashMap<>(Map.of(
                        "acceleration", acceleration,
                        "speed_change", speedChange,
                        "time_seconds", timeSeconds
                    )))
                    .detectedAt(Instant.now())
                    .alertTime(current.getTimestamp())
                    .actionRequired(false)
                    .acknowledged(false)
                    .build();
                
                ctx.output(alertsTag, alert);
                updateLastAlertTime(ctx.timerService().currentProcessingTime());
                
                LOG.debug("⚠️  IMPOSSIBLE ACCELERATION: MMSI={}, Acceleration={} knots/sec", 
                    current.getMmsi(), acceleration);
            }
        }
    }
    
    /**
     * Get max speed threshold for vessel type.
     */
    private double getMaxSpeedForVesselType(String vesselType) {
        if (vesselType == null) return MAX_CARGO_SPEED;
        
        return switch (vesselType.toUpperCase()) {
            case "PASSENGER" -> MAX_PASSENGER_SPEED;
            case "TANKER" -> MAX_TANKER_SPEED;
            case "CARGO" -> MAX_CARGO_SPEED;
            default -> MAX_CARGO_SPEED;
        };
    }
    
    /**
     * Throttle alerts - only send if enough time has passed since last alert.
     */
    private boolean shouldSendAlert(long currentTime) throws Exception {
        Long lastAlertTime = lastAlertTimeState.value();
        if (lastAlertTime == null) {
            return true;
        }
        
        // Send alert at most once every 5 minutes
        return (currentTime - lastAlertTime) > 300000;
    }
    
    private void updateLastAlertTime(long time) throws Exception {
        lastAlertTimeState.update(time);
    }
    
    private Map<String, Object> createSpeedAlertDetails(EnrichedPosition position, double speed, double maxSpeed) {
        Map<String, Object> details = new HashMap<>();
        details.put("current_speed", speed);
        details.put("max_expected_speed", maxSpeed);
        details.put("vessel_type", position.getVesselType());
        details.put("course", position.getCourse());
        details.put("heading", position.getHeading());
        details.put("status", position.getStatus());
        return details;
    }
}
