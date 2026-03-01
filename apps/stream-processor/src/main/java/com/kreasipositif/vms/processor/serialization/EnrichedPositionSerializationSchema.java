package com.kreasipositif.vms.processor.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.processor.model.EnrichedPosition;
import org.apache.flink.api.common.serialization.SerializationSchema;

/**
 * Serialize enriched positions to Kafka JSON format.
 */
public class EnrichedPositionSerializationSchema implements SerializationSchema<EnrichedPosition> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    
    @Override
    public byte[] serialize(EnrichedPosition element) {
        try {
            return objectMapper.writeValueAsBytes(element);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize EnrichedPosition", e);
        }
    }
}
