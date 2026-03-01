package com.kreasipositif.vms.processor.functions;

import com.kreasipositif.vms.processor.model.AISMessage;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates AIS position messages.
 * Invalid messages are sent to side output for logging/dead letter queue.
 */
public class PositionValidationFunction extends ProcessFunction<AISMessage, AISMessage> {
    
    private static final Logger LOG = LoggerFactory.getLogger(PositionValidationFunction.class);
    private final OutputTag<AISMessage> invalidMessagesTag;
    
    public PositionValidationFunction(OutputTag<AISMessage> invalidMessagesTag) {
        this.invalidMessagesTag = invalidMessagesTag;
    }
    
    @Override
    public void processElement(AISMessage message, Context ctx, Collector<AISMessage> out) {
        
        // Validate MMSI
        if (!message.hasValidMMSI()) {
            LOG.debug("Invalid MMSI: {}", message.getMmsi());
            ctx.output(invalidMessagesTag, message);
            return;
        }
        
        // Validate coordinates
        if (!message.hasValidCoordinates()) {
            LOG.debug("Invalid coordinates for MMSI {}: lat={}, lon={}", 
                message.getMmsi(), message.getLatitude(), message.getLongitude());
            ctx.output(invalidMessagesTag, message);
            return;
        }
        
        // Validate speed
        if (!message.hasValidSpeed()) {
            LOG.debug("Invalid speed for MMSI {}: speed={}", message.getMmsi(), message.getSpeed());
            ctx.output(invalidMessagesTag, message);
            return;
        }
        
        // Validate timestamp (not in future, not too old)
        if (message.getTimestamp() != null) {
            long now = System.currentTimeMillis();
            long messageTime = message.getTimestamp().toEpochMilli();
            
            if (messageTime > now + 60000) { // More than 1 minute in future
                LOG.debug("Future timestamp for MMSI {}: {}", message.getMmsi(), message.getTimestamp());
                ctx.output(invalidMessagesTag, message);
                return;
            }
            
            if (messageTime < now - 3600000) { // More than 1 hour old
                LOG.debug("Old timestamp for MMSI {}: {}", message.getMmsi(), message.getTimestamp());
                ctx.output(invalidMessagesTag, message);
                return;
            }
        }
        
        // Valid message - pass through
        out.collect(message);
    }
}
