package com.kreasipositif.vms.collector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kreasipositif.vms.collector.aisstream.AISStreamClient;
import com.kreasipositif.vms.collector.aisstream.AISStreamMessage;
import com.kreasipositif.vms.collector.aisstream.BoundingBox;
import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorConfig;
import com.kreasipositif.vms.collector.core.CollectorMetadata;
import com.kreasipositif.vms.collector.core.DataCollector;
import com.kreasipositif.vms.collector.model.AISMessage;
import com.kreasipositif.vms.collector.parser.AISParser;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AIS Data Collector using AIS Stream WebSocket API.
 * Collects real-time vessel position data from aisstream.io
 */
@Slf4j
@Component
public class AISCollector extends DataCollector {

    private final CollectorConfig config;
    private final AISParser parser;
    private final CollectorMetadata metadata;
    private final ObjectMapper objectMapper;
    private AISStreamClient streamClient;

    public AISCollector(
            MeterRegistry meterRegistry,
            CollectorConfig config,
            AISParser parser) {
        
        super(meterRegistry, buildMetadata(config));
        this.config = config;
        this.parser = parser;
        this.metadata = getMetadata();
        this.objectMapper = new ObjectMapper();
        
        log.info("AISCollector initialized with WebSocket URL: {}", config.getAis().getWebsocketUrl());
    }
    
    @PostConstruct
    public void init() {
        if (!config.getAis().isEnabled()) {
            log.info("AIS Collector is disabled");
            return;
        }

        try {
            // Convert bounding boxes from config
            List<BoundingBox> boxes = config.getAis().getBoundingBoxes().stream()
                    .map(bb -> BoundingBox.fromCoordinates(bb.getName(), bb.getCoordinates()))
                    .toList();

            // Initialize WebSocket client
            streamClient = new AISStreamClient(
                    config.getAis().getWebsocketUrl(),
                    config.getAis().getApiKey(),
                    boxes,
                    config.getAis().getMaxReconnectAttempts(),
                    config.getAis().getReconnectDelay().toMillis()
            );

            // Connect to AIS Stream
            streamClient.connect();
            log.info("✅ AIS Stream client initialized and connected");
            
        } catch (Exception e) {
            log.error("Failed to initialize AIS Stream client: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void cleanup() {
        if (streamClient != null) {
            streamClient.disconnect();
            log.info("AIS Stream client disconnected");
        }
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
        log.info("Collecting AIS data from WebSocket stream...");
        
        List<CollectedData> collectedData = new ArrayList<>();
        
        if (streamClient == null || !streamClient.isConnected()) {
            log.warn("AIS Stream client not connected");
            return collectedData;
        }
        
        try {
            // Poll messages from the queue for the poll interval duration
            long endTime = System.currentTimeMillis() + config.getAis().getPollInterval().toMillis();
            int maxMessages = config.getAis().getBatchSize();
            
            while (System.currentTimeMillis() < endTime && collectedData.size() < maxMessages) {
                String message = streamClient.pollMessage(1, TimeUnit.SECONDS);
                
                if (message != null) {
                    CollectedData data = convertToCollectedData(message);
                    if (data != null) {
                        collectedData.add(data);
                    }
                } else {
                    // No message received, continue polling
                    if (collectedData.isEmpty()) {
                        // Still waiting for first message
                        Thread.sleep(100);
                    }
                }
            }
            
            log.info("Collected {} AIS records from stream (queue size: {})", 
                    collectedData.size(), streamClient.getQueueSize());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AIS collection interrupted");
        } catch (Exception e) {
            log.error("Failed to collect AIS data: {}", e.getMessage(), e);
            throw new CollectionException("AIS collection failed", e);
        }
        
        return collectedData;
    }

    /**
     * Convert raw JSON to CollectedData
     */
    private CollectedData convertToCollectedData(String rawJson) {
        try {
            // First parse as AIS Stream format
            AISStreamMessage streamMessage = objectMapper.readValue(rawJson, AISStreamMessage.class);
            
            // Convert to internal AIS message format
            AISMessage aisMessage = streamMessage.toAISMessage();
            
            // Validate the converted message
            if (!aisMessage.isValid()) {
                log.warn("Invalid AIS message after conversion: missing required fields");
                return null;
            }
            
            // Create metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("mmsi", String.valueOf(aisMessage.getMmsi()));
            metadata.put("vessel_name", aisMessage.getVesselName());
            metadata.put("message_type", streamMessage.getMessageType());
            if (aisMessage.getNavigationStatusDescription() != null) {
                metadata.put("navigation_status", aisMessage.getNavigationStatusDescription());
            }
            
            log.info("✅ Parsed AIS message - MMSI: {}, Vessel: {}, Position: {},{}", 
                    aisMessage.getMmsi(), 
                    aisMessage.getVesselName(),
                    aisMessage.getLatitude(),
                    aisMessage.getLongitude());
            
            return CollectedData.builder()
                    .dataType(CollectorMetadata.CollectorDataType.AIS_RAW)
                    .collectorId(getMetadata().getId())
                    .timestamp(Instant.now())
                    .rawData(rawJson)
                    .parsedData(aisMessage)
                    .metadata(metadata)
                    .qualityScore(calculateQualityScore(aisMessage))
                    .source(config.getAis().getWebsocketUrl())
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to convert AIS Stream message: {}. Raw: {}", 
                    e.getMessage(), 
                    rawJson.substring(0, Math.min(200, rawJson.length())));
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
        // Check if collector is enabled, API key is configured, and WebSocket is connected
        return super.isHealthy() 
                && config.getAis().getApiKey() != null
                && !config.getAis().getApiKey().isBlank()
                && streamClient != null
                && streamClient.isConnected();
    }
}
