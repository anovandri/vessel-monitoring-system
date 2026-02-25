package com.kreasipositif.vms.collector.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.collector.model.WeatherData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parser for weather data.
 * Converts JSON weather data into structured WeatherData objects.
 */
@Slf4j
@Component
public class WeatherParser implements DataParser<WeatherData> {

    private final ObjectMapper objectMapper;

    public WeatherParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public WeatherData parse(String rawData) throws ParsingException {
        try {
            log.debug("Parsing weather data");
            
            WeatherData weather = objectMapper.readValue(rawData, WeatherData.class);
            
            if (!weather.isValid()) {
                throw new ParsingException("Invalid weather data: missing required fields or invalid coordinates");
            }
            
            log.debug("Successfully parsed weather data for location: {}", weather.getLocationName());
            return weather;
            
        } catch (Exception e) {
            log.error("Failed to parse weather data: {}", e.getMessage());
            throw new ParsingException("Failed to parse weather data", e);
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
                && (trimmed.contains("\"temperature\"") 
                    || trimmed.contains("\"wind_speed\"")
                    || trimmed.contains("\"weather\""));
    }

    @Override
    public String getDataType() {
        return "WEATHER";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}
