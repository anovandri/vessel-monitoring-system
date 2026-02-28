package com.kreasipositif.vms.collector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kreasipositif.vms.collector.aisstream.AISStreamMessage;
import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorConfig;
import com.kreasipositif.vms.collector.core.CollectorMetadata;
import com.kreasipositif.vms.collector.core.DataCollector;
import com.kreasipositif.vms.collector.mock.MockAISDataGenerator;
import com.kreasipositif.vms.collector.model.AISMessage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mock AIS Collector for Indonesian Maritime Demo.
 * Generates realistic vessel data instead of connecting to external AIS Stream API.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vms.collector.ais.mock.enabled", havingValue = "true", matchIfMissing = false)
public class MockAISCollector extends DataCollector {

    private final MockAISDataGenerator mockGenerator;
    private final ObjectMapper objectMapper;
    private final CollectorConfig config;
    private final int vesselsPerCollection;

    public MockAISCollector(
            MeterRegistry meterRegistry,
            CollectorConfig config,
            MockAISDataGenerator mockGenerator,
            ObjectMapper objectMapper) {
        
        super(meterRegistry, buildMetadata(config));
        this.config = config;
        this.mockGenerator = mockGenerator;
        this.objectMapper = objectMapper;
        
        // Generate 3-8 vessel updates per collection cycle
        this.vesselsPerCollection = 3 + (int)(Math.random() * 6);
        
        log.info("🚢 Mock AIS Collector initialized for Indonesian maritime demo");
        log.info("📍 Monitoring areas: {}", mockGenerator.getMonitoredAreas());
        log.info("⚓ Fleet size: {} vessels", mockGenerator.getVesselCount());
        log.info("📊 Vessels per collection: {}", vesselsPerCollection);
    }

    private static CollectorMetadata buildMetadata(CollectorConfig config) {
        return CollectorMetadata.builder()
                .id("mock-ais-collector")
                .name("Mock AIS Collector (Indonesian Demo)")
                .description("Mock AIS data generator for Indonesian maritime vessels")
                .version("1.0.0-DEMO")
                .dataType(CollectorMetadata.CollectorDataType.AIS_RAW)
                .enabled(true)
                .pollInterval(config.getAis().getPollInterval())
                .priority(10)
                .kafkaTopic("ais-raw-data")
                .build();
    }

    @Override
    protected List<CollectedData> doCollect() throws Exception {
        log.info("🚢 Generating mock AIS data for Indonesian vessels...");
        
        List<CollectedData> collectedData = new ArrayList<>();
        
        // Generate multiple vessel updates
        List<String> mockMessages = mockGenerator.generateMockMessages(vesselsPerCollection);
        
        for (String messageJson : mockMessages) {
            try {
                CollectedData data = convertToCollectedData(messageJson);
                if (data != null) {
                    collectedData.add(data);
                }
            } catch (Exception e) {
                log.error("Failed to process mock AIS message", e);
            }
        }
        
        log.info("✅ Generated {} mock vessel updates for Indonesian maritime area", collectedData.size());
        return collectedData;
    }

    private CollectedData convertToCollectedData(String rawData) throws Exception {
        // Parse the mock AIS Stream format message
        AISStreamMessage streamMessage = objectMapper.readValue(rawData, AISStreamMessage.class);
        
        // Convert to internal AIS message format
        AISMessage aisMessage = streamMessage.toAISMessage();
        
        log.debug("✅ Generated mock AIS data - MMSI: {}, Vessel: {}, Position: {},{}, Speed: {} knots",
                aisMessage.getMmsi(),
                aisMessage.getVesselName(),
                aisMessage.getLatitude(),
                aisMessage.getLongitude(),
                aisMessage.getSpeed());
        
        // Create metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("collector_id", getMetadata().getId());
        metadata.put("collection_time", Instant.now().toString());
        metadata.put("source", "mock_generator");
        metadata.put("data_type", "AIS_RAW");
        metadata.put("message_type", streamMessage.getMessageType());
        metadata.put("demo_mode", "true");
        
        return CollectedData.builder()
                .rawData(rawData)
                .parsedData(aisMessage)
                .collectorId(getMetadata().getId())
                .dataType(CollectorMetadata.CollectorDataType.AIS_RAW)
                .timestamp(Instant.now())
                .metadata(metadata)
                .build();
    }

    @Override
    protected void preCollect() {
        log.debug("🎭 Preparing mock AIS data generation for Indonesian vessels");
    }

    @Override
    protected List<CollectedData> postCollect(List<CollectedData> data) {
        if (!data.isEmpty()) {
            log.info("📦 Mock AIS collector processed {} vessel updates", data.size());
            
            // Log summary
            long uniqueVessels = data.stream()
                    .map(d -> ((AISMessage) d.getParsedData()).getMmsi())
                    .distinct()
                    .count();
            log.debug("📊 Updates from {} unique vessels", uniqueVessels);
        }
        return data;
    }
}
