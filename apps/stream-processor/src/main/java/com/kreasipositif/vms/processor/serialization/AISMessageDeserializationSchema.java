package com.kreasipositif.vms.processor.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.processor.model.AISMessage;
import com.kreasipositif.vms.processor.model.CollectorMessage;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

/**
 * Deserialize AIS messages from Kafka JSON format.
 * Handles the wrapper structure from data-collector-service.
 */
public class AISMessageDeserializationSchema implements DeserializationSchema<AISMessage> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    
    @Override
    public AISMessage deserialize(byte[] message) throws IOException {
        // First deserialize the wrapper CollectorMessage
        CollectorMessage collectorMessage = objectMapper.readValue(message, CollectorMessage.class);
        
        // Convert to AISMessage for processing
        AISMessage aisMessage = collectorMessage.toAISMessage();
        
        if (aisMessage == null) {
            throw new IOException("Failed to convert CollectorMessage to AISMessage - parsedData is null");
        }
        
        return aisMessage;
    }
    
    @Override
    public boolean isEndOfStream(AISMessage nextElement) {
        return false;
    }
    
    @Override
    public TypeInformation<AISMessage> getProducedType() {
        return TypeInformation.of(AISMessage.class);
    }
}
