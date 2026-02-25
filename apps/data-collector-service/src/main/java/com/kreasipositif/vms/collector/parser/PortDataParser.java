package com.kreasipositif.vms.collector.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.collector.model.PortData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parser for port data.
 * Converts JSON port information into structured PortData objects.
 */
@Slf4j
@Component
public class PortDataParser implements DataParser<PortData> {

    private final ObjectMapper objectMapper;

    public PortDataParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public PortData parse(String rawData) throws ParsingException {
        try {
            log.debug("Parsing port data");
            
            PortData portData = objectMapper.readValue(rawData, PortData.class);
            
            if (!portData.isValid()) {
                throw new ParsingException("Invalid port data: missing required fields or invalid coordinates");
            }
            
            log.debug("Successfully parsed port data for: {} ({})", 
                    portData.getPortName(), portData.getPortCode());
            return portData;
            
        } catch (Exception e) {
            log.error("Failed to parse port data: {}", e.getMessage());
            throw new ParsingException("Failed to parse port data", e);
        }
    }

    @Override
    public boolean canParse(String rawData) {
        if (rawData == null || rawData.isBlank()) {
            return false;
        }
        
        String trimmed = rawData.trim();
        return trimmed.startsWith("{") 
                && trimmed.endsWith("}")
                && (trimmed.contains("\"port_code\"") 
                    || trimmed.contains("\"port_name\"")
                    || trimmed.contains("\"berth_count\""));
    }

    @Override
    public String getDataType() {
        return "PORT_DATA";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
