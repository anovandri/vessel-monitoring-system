package com.kreasipositif.vms.collector.parser;

/**
 * Strategy pattern for parsing different data formats.
 * Each parser knows how to convert raw data into a structured format.
 * 
 * @param <T> The type of parsed data this parser produces
 */
public interface DataParser<T> {

    /**
     * Parse raw data string into structured format
     * 
     * @param rawData The raw data to parse (JSON, CSV, XML, etc.)
     * @return Parsed data object
     * @throws ParsingException if parsing fails
     */
    T parse(String rawData) throws ParsingException;

    /**
     * Validate raw data before parsing
     * 
     * @param rawData The raw data to validate
     * @return true if data is valid and can be parsed
     */
    boolean canParse(String rawData);

    /**
     * Get the type of data this parser handles
     * 
     * @return Data type identifier
     */
    String getDataType();

    /**
     * Get parser version
     * 
     * @return Version string
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * Custom exception for parsing errors
     */
    class ParsingException extends Exception {
        public ParsingException(String message) {
            super(message);
        }

        public ParsingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
