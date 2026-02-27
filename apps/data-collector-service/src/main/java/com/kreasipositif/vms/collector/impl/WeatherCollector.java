package com.kreasipositif.vms.collector.impl;

import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorConfig;
import com.kreasipositif.vms.collector.core.CollectorMetadata;
import com.kreasipositif.vms.collector.core.DataCollector;
import com.kreasipositif.vms.collector.model.WeatherData;
import com.kreasipositif.vms.collector.parser.WeatherParser;
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
 * Weather Data Collector using Java 21 Virtual Threads.
 * Collects maritime weather data from weather APIs.
 */
@Slf4j
@Component
public class WeatherCollector extends DataCollector {

    private final WebClient webClient;
    private final CollectorConfig config;
    private final WeatherParser parser;
    private final CollectorMetadata metadata;

    // South Asia and Indonesia maritime weather monitoring locations
    private static final List<Map<String, Double>> MONITORING_LOCATIONS = List.of(
            Map.of("lat", -6.11, "lon", 106.89),  // Jakarta, Indonesia
            Map.of("lat", -3.32, "lon", 114.59),  // Balikpapan, Indonesia
            Map.of("lat", 1.45, "lon", 124.84),   // Bitung, Indonesia
            Map.of("lat", -0.95, "lon", 100.35),  // Padang, Indonesia
            Map.of("lat", -8.65, "lon", 115.22),  // Benoa (Bali), Indonesia
            Map.of("lat", -5.45, "lon", 105.27),  // Panjang (Lampung), Indonesia
            Map.of("lat", 3.59, "lon", 98.67)     // Belawan (Medan), Indonesia
    );

    public WeatherCollector(
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry,
            CollectorConfig config,
            WeatherParser parser) {
        
        super(meterRegistry, buildMetadata(config));
        this.config = config;
        this.parser = parser;
        this.metadata = getMetadata();
        
        this.webClient = webClientBuilder
                .baseUrl(config.getWeather().getBaseUrl())
                .build();
        
        log.info("WeatherCollector initialized");
    }
    
    private static CollectorMetadata buildMetadata(CollectorConfig config) {
        return CollectorMetadata.builder()
                .id("weather-collector")
                .name("Weather API Collector")
                .description("Collects maritime weather data for key locations")
                .dataType(CollectorMetadata.CollectorDataType.WEATHER)
                .pollInterval(config.getWeather().getPollInterval())
                .enabled(config.getWeather().isEnabled())
                .priority(5)
                .version("1.0.0")
                .kafkaTopic("weather-data")
                .build();
    }

    @Override
    protected List<CollectedData> doCollect() throws Exception {
        String apiKey = config.getWeather().getApiKey();
        log.info("Collecting weather data... (API Key: {}...)", 
                apiKey != null && apiKey.length() > 8 ? apiKey.substring(0, 8) : "MISSING");
        
        List<CollectedData> collectedData = new ArrayList<>();
        
        // Collect weather for each monitoring location using Virtual Threads
        for (Map<String, Double> location : MONITORING_LOCATIONS) {
            try {
                String response = fetchWeatherData(location.get("lat"), location.get("lon"));
                
                if (response != null && !response.isBlank()) {
                    CollectedData data = convertToCollectedData(response);
                    if (data != null) {
                        collectedData.add(data);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to collect weather for location {}: {}", location, e.getMessage());
            }
        }
        
        log.info("Collected {} weather records", collectedData.size());
        return collectedData;
    }

    private String fetchWeatherData(double lat, double lon) {
        String apiKey = config.getWeather().getApiKey();
        String baseUrl = config.getWeather().getBaseUrl();
        String units = config.getWeather().getUnits();
        
        String url = String.format("%s/data/2.5/weather?lat=%s&lon=%s&appid=%s&units=%s",
                baseUrl, lat, lon, apiKey, units);
        log.info("Calling Weather API: {} (key: {}...)", 
                url.replace(apiKey, apiKey.substring(0, 8) + "***"), 
                apiKey.substring(0, 8));
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/data/2.5/weather")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("appid", apiKey)
                        .queryParam("units", units)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(config.getDefaultTimeout())
                .block();
    }

    private CollectedData convertToCollectedData(String rawJson) {
        try {
            WeatherData weatherData = parser.parse(rawJson);
            
            Map<String, String> metadata = new HashMap<>();
            metadata.put("location", weatherData.getLocationName());
            metadata.put("condition", weatherData.getCondition());
            metadata.put("sea_state", weatherData.getSeaStateDescription());
            
            return CollectedData.builder()
                    .dataType(CollectorMetadata.CollectorDataType.WEATHER)
                    .collectorId(getMetadata().getId())
                    .timestamp(Instant.now())
                    .rawData(rawJson)
                    .parsedData(weatherData)
                    .metadata(metadata)
                    .qualityScore(weatherData.isSafeForNavigation() ? 100 : 70)
                    .source(config.getWeather().getBaseUrl())
                    .build();
                    
        } catch (Exception e) {
            log.warn("Failed to convert weather data: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isHealthy() {
        return super.isHealthy() 
                && config.getWeather().getApiKey() != null
                && !config.getWeather().getApiKey().isBlank();
    }
}
