package com.kreasipositif.vms.collector.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration properties for data collectors.
 * Each collector can have its own configuration section.
 */
@Data
@Component
@ConfigurationProperties(prefix = "vms.collector")
public class CollectorConfig {

    /**
     * Global collector settings
     */
    private boolean enabled = true;
    private int maxConcurrentCollectors = 10;
    private Duration defaultTimeout = Duration.ofSeconds(30);
    private int maxRetries = 3;
    private Duration retryDelay = Duration.ofSeconds(5);

    /**
     * AIS Collector Configuration
     */
    private AisCollectorConfig ais = new AisCollectorConfig();

    /**
     * Weather Collector Configuration
     */
    private WeatherCollectorConfig weather = new WeatherCollectorConfig();

    /**
     * Port Data Collector Configuration
     */
    private PortDataCollectorConfig portData = new PortDataCollectorConfig();

    @Data
    public static class AisCollectorConfig {
        private boolean enabled = true;
        private String websocketUrl = "wss://stream.aisstream.io/v0/stream";
        private String apiKey;
        private Duration pollInterval = Duration.ofSeconds(30);
        private int batchSize = 100;
        private Duration reconnectDelay = Duration.ofSeconds(5);
        private int maxReconnectAttempts = 10;
        private java.util.List<BoundingBoxConfig> boundingBoxes = new java.util.ArrayList<>();
        private Map<String, String> additionalHeaders;
    }

    @Data
    public static class BoundingBoxConfig {
        private String name;
        private double[][] coordinates; // [[minLon, minLat], [maxLon, maxLat]]
    }

    @Data
    public static class WeatherCollectorConfig {
        private boolean enabled = true;
        private String baseUrl = "https://api.openweathermap.org";
        private String apiKey = "";
        private Duration pollInterval = Duration.ofMinutes(5);
        private String units = "metric";
    }

    @Data
    public static class PortDataCollectorConfig {
        private boolean enabled = true;
        private String baseUrl = "https://api.portdata.com";
        private String apiKey;
        private Duration pollInterval = Duration.ofMinutes(10);
        private boolean includeSchedules = true;
    }
}
