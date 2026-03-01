package com.kreasipositif.vms.processor.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.processor.model.AISMessage;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

/**
 * Deserialize AIS messages from Kafka JSON format.
 */
public class AISMessageDeserializationSchema implements DeserializationSchema<AISMessage> {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());
    
    @Override
    public AISMessage deserialize(byte[] message) throws IOException {
        return objectMapper.readValue(message, AISMessage.class);
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
