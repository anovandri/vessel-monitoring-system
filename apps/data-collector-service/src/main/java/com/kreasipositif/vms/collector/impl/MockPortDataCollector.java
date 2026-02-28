package com.kreasipositif.vms.collector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorConfig;
import com.kreasipositif.vms.collector.core.CollectorMetadata;
import com.kreasipositif.vms.collector.core.DataCollector;
import com.kreasipositif.vms.collector.mock.MockPortDataGenerator;
import com.kreasipositif.vms.collector.model.PortData;
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
 * Mock Port Data Collector for Indonesian maritime demo.
 * Generates realistic port data instead of calling external port data APIs.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "vms.collector.port-data.mock.enabled", havingValue = "true", matchIfMissing = false)
public class MockPortDataCollector extends DataCollector {

    private final MockPortDataGenerator mockGenerator;
    private final ObjectMapper objectMapper;
    private final CollectorConfig config;

    public MockPortDataCollector(
            MeterRegistry meterRegistry,
            CollectorConfig config,
            MockPortDataGenerator mockGenerator,
            ObjectMapper objectMapper) {
        
        super(meterRegistry, buildMetadata(config));
        this.config = config;
        this.mockGenerator = mockGenerator;
        this.objectMapper = objectMapper;
        
        log.info("🏗️ Mock Port Data Collector initialized for Indonesian maritime demo");
        log.info("📍 Monitoring {} Indonesian ports: {}", 
                mockGenerator.getPortCount(), 
                mockGenerator.getPortCodes());
    }

    private static CollectorMetadata buildMetadata(CollectorConfig config) {
        return CollectorMetadata.builder()
                .id("mock-port-data-collector")
                .name("Mock Port Data Collector (Indonesian Demo)")
                .description("Mock port data generator for Indonesian maritime ports")
                .version("1.0.0-DEMO")
                .dataType(CollectorMetadata.CollectorDataType.PORT_DATA)
                .enabled(true)
                .pollInterval(config.getPortData().getPollInterval())
                .priority(3)
                .kafkaTopic("port-data")
                .build();
    }

    @Override
    protected List<CollectedData> doCollect() throws Exception {
        log.info("🏗️ Generating mock port data for Indonesian ports...");
        
        List<CollectedData> collectedData = new ArrayList<>();
        
        // Generate data for all Indonesian ports
        List<PortData> portsData = mockGenerator.generateAllPortsData();
        
        for (PortData portData : portsData) {
            try {
                // Convert to JSON
                String json = objectMapper.writeValueAsString(portData);
                
                // Create collected data
                CollectedData data = convertToCollectedData(portData, json);
                collectedData.add(data);
                
                log.debug("✅ Generated port data - Port: {}, Vessels: {}, Congestion: {}%, Status: {}",
                        portData.getPortName(),
                        portData.getVesselsInPort(),
                        portData.getCongestionLevel(),
                        portData.getCongestionStatus());
                
            } catch (Exception e) {
                log.error("Failed to generate port data for {}: {}", portData.getPortCode(), e.getMessage());
            }
        }
        
        log.info("✅ Generated mock data for {} Indonesian ports", collectedData.size());
        return collectedData;
    }

    @Override
    protected List<CollectedData> postCollect(List<CollectedData> data) {
        if (data != null && !data.isEmpty()) {
            log.info("📦 Mock Port Data collector processed {} port updates", data.size());
            
            // Log summary statistics
            data.forEach(cd -> {
                Map<String, String> metadata = cd.getMetadata();
                log.debug("🏗️ Port: {} | Vessels: {} | Congestion: {} | Status: {}",
                        metadata.get("port_code"),
                        metadata.get("vessels_in_port"),
                        metadata.get("congestion_level") + "%",
                        metadata.get("congestion_status"));
            });
        }
        return data;
    }

    /**
     * Convert port data to CollectedData format
     */
    private CollectedData convertToCollectedData(PortData portData, String rawJson) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("collector_id", getMetadata().getId());
        metadata.put("collection_time", Instant.now().toString());
        metadata.put("source", "mock_generator");
        metadata.put("data_type", "PORT_DATA");
        metadata.put("port_code", portData.getPortCode());
        metadata.put("port_name", portData.getPortName());
        metadata.put("country_code", portData.getCountryCode());
        metadata.put("latitude", String.valueOf(portData.getLatitude()));
        metadata.put("longitude", String.valueOf(portData.getLongitude()));
        metadata.put("status", portData.getStatus());
        metadata.put("vessels_in_port", String.valueOf(portData.getVesselsInPort()));
        metadata.put("expected_arrivals", String.valueOf(portData.getExpectedArrivals()));
        metadata.put("expected_departures", String.valueOf(portData.getExpectedDepartures()));
        metadata.put("congestion_level", String.valueOf(portData.getCongestionLevel()));
        metadata.put("congestion_status", portData.getCongestionStatus());
        metadata.put("berth_count", String.valueOf(portData.getBerthCount()));
        metadata.put("demo_mode", "true");

        return CollectedData.builder()
                .id(java.util.UUID.randomUUID().toString())
                .collectorId(getMetadata().getId())
                .dataType(CollectorMetadata.CollectorDataType.PORT_DATA)
                .rawData(rawJson)
                .parsedData(portData)
                .metadata(metadata)
                .timestamp(Instant.now())
                .build();
    }
}
