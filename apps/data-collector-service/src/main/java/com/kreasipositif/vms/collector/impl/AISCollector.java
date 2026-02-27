package com.kreasipositif.vms.collector.impl;

import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorConfig;
import com.kreasipositif.vms.collector.core.CollectorMetadata;
import com.kreasipositif.vms.collector.core.DataCollector;
import com.kreasipositif.vms.collector.model.AISMessage;
import com.kreasipositif.vms.collector.parser.AISParser;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AIS Data Collector using Java 21 Virtual Threads.
 * Collects vessel position data from AIS APIs.
 */
@Slf4j
@Component
public class AISCollector extends DataCollector {

    private final WebClient webClient;
    private final CollectorConfig config;
    private final AISParser parser;
    private final CollectorMetadata metadata;

    public AISCollector(
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry,
            CollectorConfig config,
            AISParser parser) {
        
        // Build metadata FIRST before calling super()
        super(meterRegistry, buildMetadata(config));
        this.config = config;
        this.parser = parser;
        
        // Build WebClient with configuration
        // NOTE: Do NOT use defaultHeader() for X-API-Key as it affects the shared builder
        // Instead, add X-API-Key at request level in doCollect()
        this.webClient = webClientBuilder
                .baseUrl(config.getAis().getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        
        // Store metadata reference
        this.metadata = getMetadata();
        
        log.info("AISCollector initialized with base URL: {}", config.getAis().getBaseUrl());
    }
    
    private static CollectorMetadata buildMetadata(CollectorConfig config) {
        return CollectorMetadata.builder()
                .id("ais-collector")
                .name("AIS Stream Collector")
                .description("Collects real-time AIS data from maritime traffic APIs")
                .dataType(CollectorMetadata.CollectorDataType.AIS_RAW)
                .pollInterval(config.getAis().getPollInterval())
                .enabled(config.getAis().isEnabled())
                .priority(10) // High priority
                .version("1.0.0")
                .kafkaTopic("ais-raw-data")
                .build();
    }

    @Override
    protected List<CollectedData> doCollect() throws Exception {
        log.info("Collecting AIS data from API...");
        
        List<CollectedData> collectedData = new ArrayList<>();
        
        try {
            // Make API call to fetch AIS data
            // This is a simplified example - adjust endpoint and parameters as needed
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/ais/positions")
                            .queryParam("limit", config.getAis().getBatchSize())
                            .queryParam("format", "json")
                            .build())
                    .header("X-API-Key", config.getAis().getApiKey()) // Add X-API-Key at request level
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(config.getDefaultTimeout())
                    .onErrorResume(error -> {
                        log.error("Error fetching AIS data: {}", error.getMessage());
                        return Mono.just("[]"); // Return empty array on error
                    })
                    .block(); // Block is acceptable here as we're using Virtual Threads
            
            if (response != null && !response.isBlank()) {
                // Parse response - assuming it's a JSON array of AIS messages
                collectedData.addAll(parseAISResponse(response));
            }
            
            log.info("Collected {} AIS records", collectedData.size());
            
        } catch (Exception e) {
            log.error("Failed to collect AIS data: {}", e.getMessage(), e);
            throw new CollectionException("AIS collection failed", e);
        }
        
        return collectedData;
    }

    /**
     * Parse AIS API response and convert to CollectedData
     */
    private List<CollectedData> parseAISResponse(String response) {
        List<CollectedData> dataList = new ArrayList<>();
        
        try {
            // Handle both single object and array responses
            if (response.trim().startsWith("[")) {
                // Array of AIS messages
                String[] messages = extractJsonObjects(response);
                for (String messageJson : messages) {
                    CollectedData data = convertToCollectedData(messageJson);
                    if (data != null) {
                        dataList.add(data);
                    }
                }
            } else {
                // Single AIS message
                CollectedData data = convertToCollectedData(response);
                if (data != null) {
                    dataList.add(data);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing AIS response: {}", e.getMessage(), e);
        }
        
        return dataList;
    }

    /**
     * Convert raw JSON to CollectedData
     */
    private CollectedData convertToCollectedData(String rawJson) {
        try {
            // Parse using AISParser to validate
            AISMessage aisMessage = parser.parse(rawJson);
            
            // Create metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("mmsi", String.valueOf(aisMessage.getMmsi()));
            metadata.put("vessel_name", aisMessage.getVesselName());
            metadata.put("navigation_status", aisMessage.getNavigationStatusDescription());
            
            return CollectedData.builder()
                    .dataType(CollectorMetadata.CollectorDataType.AIS_RAW)
                    .collectorId(getMetadata().getId())
                    .timestamp(Instant.now())
                    .rawData(rawJson)
                    .parsedData(aisMessage)
                    .metadata(metadata)
                    .qualityScore(calculateQualityScore(aisMessage))
                    .source(config.getAis().getBaseUrl())
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to convert AIS message to CollectedData: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Calculate quality score for AIS data (0-100)
     */
    private int calculateQualityScore(AISMessage message) {
        int score = 100;
        
        // Deduct points for missing optional fields
        if (message.getVesselName() == null || message.getVesselName().isBlank()) score -= 10;
        if (message.getSpeed() == null) score -= 10;
        if (message.getCourse() == null) score -= 10;
        if (message.getHeading() == null) score -= 5;
        if (message.getNavigationStatus() == null) score -= 5;
        if (message.getDestination() == null || message.getDestination().isBlank()) score -= 10;
        
        // Check data freshness (deduct points if old)
        if (message.getTimestamp() != null) {
            long ageMinutes = Duration.between(message.getTimestamp(), Instant.now()).toMinutes();
            if (ageMinutes > 30) score -= 20;
            else if (ageMinutes > 10) score -= 10;
        }
        
        return Math.max(0, score);
    }

    /**
     * Simple JSON array splitter (for demo purposes - use proper JSON library in production)
     */
    private String[] extractJsonObjects(String jsonArray) {
        // Remove brackets and split by },{ pattern
        String content = jsonArray.trim();
        if (content.startsWith("[")) content = content.substring(1);
        if (content.endsWith("]")) content = content.substring(0, content.length() - 1);
        
        List<String> objects = new ArrayList<>();
        int braceCount = 0;
        StringBuilder current = new StringBuilder();
        
        for (char c : content.toCharArray()) {
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            
            current.append(c);
            
            if (braceCount == 0 && current.length() > 0) {
                String obj = current.toString().trim();
                if (obj.startsWith(",")) obj = obj.substring(1).trim();
                if (!obj.isBlank()) {
                    objects.add(obj);
                }
                current = new StringBuilder();
            }
        }
        
        return objects.toArray(new String[0]);
    }

    @Override
    protected void preCollect() {
        log.debug("Preparing to collect AIS data");
        // Add any pre-collection setup here (e.g., check API health)
    }

    @Override
    protected List<CollectedData> postCollect(List<CollectedData> data) {
        log.info("Post-processing {} AIS records", data.size());
        
        // Filter out low-quality data
        return data.stream()
                .filter(d -> d.getQualityScore() >= 50)
                .toList();
    }

    @Override
    public boolean isHealthy() {
        // Check if collector is enabled and API is configured
        return super.isHealthy() 
                && config.getAis().getApiKey() != null
                && !config.getAis().getApiKey().isBlank();
    }
}
