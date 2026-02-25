package com.kreasipositif.vms.collector.impl;

import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorConfig;
import com.kreasipositif.vms.collector.core.CollectorMetadata;
import com.kreasipositif.vms.collector.core.DataCollector;
import com.kreasipositif.vms.collector.model.PortData;
import com.kreasipositif.vms.collector.parser.PortDataParser;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port Data Collector using Java 21 Virtual Threads.
 * Collects port information and vessel schedules.
 */
@Slf4j
@Component
public class PortDataCollector extends DataCollector {

    private final WebClient webClient;
    private final CollectorConfig config;
    private final PortDataParser parser;
    private final CollectorMetadata metadata;

    private static final List<String> MONITORED_PORTS = List.of(
            "SGSIN", // Singapore
            "NLRTM", // Rotterdam
            "USNYC", // New York
            "CNSHA"  // Shanghai
    );

    public PortDataCollector(
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry,
            CollectorConfig config,
            PortDataParser parser) {
        
        super(meterRegistry, buildMetadata(config));
        this.config = config;
        this.parser = parser;
        this.metadata = getMetadata();
        
        this.webClient = webClientBuilder
                .baseUrl(config.getPortData().getBaseUrl())
                .build();
        
        log.info("PortDataCollector initialized");
    }
    
    private static CollectorMetadata buildMetadata(CollectorConfig config) {
        return CollectorMetadata.builder()
                .id("port-data-collector")
                .name("Port Data Collector")
                .description("Collects port information and vessel schedules")
                .dataType(CollectorMetadata.CollectorDataType.PORT_DATA)
                .pollInterval(config.getPortData().getPollInterval())
                .enabled(config.getPortData().isEnabled())
                .priority(3)
                .version("1.0.0")
                .kafkaTopic("port-data")
                .build();
    }

    @Override
    protected List<CollectedData> doCollect() throws Exception {
        log.info("Collecting port data...");
        
        List<CollectedData> collectedData = new ArrayList<>();
        
        for (String portCode : MONITORED_PORTS) {
            try {
                String response = fetchPortData(portCode);
                
                if (response != null && !response.isBlank()) {
                    CollectedData data = convertToCollectedData(response);
                    if (data != null) {
                        collectedData.add(data);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to collect data for port {}: {}", portCode, e.getMessage());
            }
        }
        
        log.info("Collected {} port records", collectedData.size());
        return collectedData;
    }

    private String fetchPortData(String portCode) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/ports/{code}")
                        .queryParam("include_schedules", config.getPortData().isIncludeSchedules())
                        .queryParam("api_key", config.getPortData().getApiKey())
                        .build(portCode))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(config.getDefaultTimeout())
                .block();
    }

    private CollectedData convertToCollectedData(String rawJson) {
        try {
            PortData portData = parser.parse(rawJson);
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("port_code", portData.getPortCode());
            metadata.put("port_name", portData.getPortName());
            metadata.put("status", portData.getStatus());
            metadata.put("congestion", portData.getCongestionStatus());
            
            int qualityScore = portData.isOperational() ? 100 : 50;
            if (portData.isCongested()) qualityScore -= 20;
            
            return CollectedData.builder()
                    .dataType(CollectorMetadata.CollectorDataType.PORT_DATA)
                    .collectorId(getMetadata().getId())
                    .timestamp(Instant.now())
                    .rawData(rawJson)
                    .parsedData(portData)
                    .metadata(metadata)
                    .qualityScore(qualityScore)
                    .source(config.getPortData().getBaseUrl())
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to convert port data: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isHealthy() {
        return super.isHealthy() 
                && config.getPortData().getApiKey() != null
                && !config.getPortData().getApiKey().isBlank();
    }
}
