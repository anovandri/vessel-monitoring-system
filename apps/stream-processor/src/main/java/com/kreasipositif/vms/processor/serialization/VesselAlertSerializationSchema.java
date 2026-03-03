package com.kreasipositif.vms.processor.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.processor.model.VesselAlert;
import org.apache.flink.api.common.serialization.SerializationSchema;

/**
 * Serialize vessel alerts to Kafka JSON format.
 * Configured to write numbers as strings to preserve precision for large longs (timestamps).
 */
public class VesselAlertSerializationSchema implements SerializationSchema<VesselAlert> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);
    
    @Override
    public byte[] serialize(VesselAlert element) {
        try {
            return objectMapper.writeValueAsBytes(element);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize VesselAlert", e);
        }
    }
}
