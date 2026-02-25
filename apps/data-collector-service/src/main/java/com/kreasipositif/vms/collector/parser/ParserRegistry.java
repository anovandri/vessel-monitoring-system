package com.kreasipositif.vms.collector.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for data parsers.
 * Allows dynamic registration and lookup of parsers by data type.
 */
@Slf4j
@Component
public class ParserRegistry {

    private final Map<String, DataParser<?>> parsers = new ConcurrentHashMap<>();

    /**
     * Register a parser
     */
    public <T> void registerParser(DataParser<T> parser) {
        String dataType = parser.getDataType();
        
        if (parsers.containsKey(dataType)) {
            log.warn("Parser for type {} already registered, replacing", dataType);
        }
        
        parsers.put(dataType, parser);
        log.info("Registered parser for data type: {} (version: {})", 
                dataType, parser.getVersion());
    }

    /**
     * Get parser for a specific data type
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<DataParser<T>> getParser(String dataType) {
        return Optional.ofNullable((DataParser<T>) parsers.get(dataType));
    }

    /**
     * Parse data using the appropriate parser
     */
    public <T> T parse(String dataType, String rawData) throws DataParser.ParsingException {
        DataParser<T> parser = this.<T>getParser(dataType)
                .orElseThrow(() -> new DataParser.ParsingException(
                        "No parser registered for data type: " + dataType));
        
        if (!parser.canParse(rawData)) {
            throw new DataParser.ParsingException(
                    "Parser cannot handle the provided data format");
        }
        
        return parser.parse(rawData);
    }

    /**
     * Check if a parser exists for a data type
     */
    public boolean hasParser(String dataType) {
        return parsers.containsKey(dataType);
    }

    /**
     * Get all registered parser types
     */
    public Set<String> getRegisteredTypes() {
        return Collections.unmodifiableSet(parsers.keySet());
    }

    /**
     * Get count of registered parsers
     */
    public int getParserCount() {
        return parsers.size();
    }

    /**
     * Clear all parsers (useful for testing)
     */
    public void clearAll() {
        parsers.clear();
        log.info("Cleared all parsers from registry");
    }

    @PostConstruct
    public void init() {
        log.info("ParserRegistry initialized");
    }
}
