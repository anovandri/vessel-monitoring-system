package com.kreasipositif.vms.collector.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.collector.model.AISMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parser for AIS (Automatic Identification System) data.
 * Converts JSON AIS messages into structured AISMessage objects.
 */
@Slf4j
@Component
public class AISParser implements DataParser<AISMessage> {

    private final ObjectMapper objectMapper;

    public AISParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public AISMessage parse(String rawData) throws ParsingException {
        try {
            log.debug("Parsing AIS data: {}", rawData.substring(0, Math.min(100, rawData.length())));
            
            AISMessage message = objectMapper.readValue(rawData, AISMessage.class);
            
            if (!message.isValid()) {
                throw new ParsingException("Invalid AIS message: missing required fields or invalid coordinates");
            }
            
            log.debug("Successfully parsed AIS message for MMSI: {}", message.getMmsi());
            return message;
            
        } catch (Exception e) {
            log.error("Failed to parse AIS data: {}", e.getMessage());
            throw new ParsingException("Failed to parse AIS data", e);
        }
    }

    @Override
    public boolean canParse(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return false;
        }
        
        // Check if it looks like JSON and contains AIS-specific fields
        String trimmed = rawData.trim();
        return trimmed.startsWith("{") 
                && trimmed.endsWith("}")
                && (trimmed.contains("\"mmsi\"") || trimmed.contains("\"MMSI\""));
    }

    @Override
    public String getDataType() {
        return "AIS";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
