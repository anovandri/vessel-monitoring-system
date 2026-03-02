package com.kreasipositif.vms.persistence.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for persisting weather data to multiple databases
 */
@Slf4j
@Service
public class WeatherPersistenceService {

    private final JdbcTemplate postgresJdbcTemplate;
    private final JdbcTemplate clickhouseJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public WeatherPersistenceService(
            @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate,
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate,
            RedisTemplate<String, Object> redisTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
        this.clickhouseJdbcTemplate = clickhouseJdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    private static final String POSTGRES_UPSERT_SQL = """
        INSERT INTO weather_data 
        (grid_id, latitude, longitude, temperature, wind_speed, wind_direction, 
         wave_height, visibility, pressure, humidity, timestamp, location)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))
        ON CONFLICT (grid_id) 
        DO UPDATE SET
            temperature = EXCLUDED.temperature,
            wind_speed = EXCLUDED.wind_speed,
            wind_direction = EXCLUDED.wind_direction,
            wave_height = EXCLUDED.wave_height,
            visibility = EXCLUDED.visibility,
            pressure = EXCLUDED.pressure,
            humidity = EXCLUDED.humidity,
            timestamp = EXCLUDED.timestamp,
            location = EXCLUDED.location,
            updated_at = CURRENT_TIMESTAMP
        """;

    private static final String CLICKHOUSE_INSERT_SQL = """
        INSERT INTO weather_data_history
        (grid_id, latitude, longitude, temperature, wind_speed, wind_direction,
         wave_height, visibility, pressure, humidity, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    @Transactional
    public void persistWeatherData(List<JsonNode> weatherData) {
        // Weather data is queried via REST API, not pushed via WebSocket
        CompletableFuture<Void> postgresTask = CompletableFuture.runAsync(() -> 
            saveToPostgres(weatherData));
        
        CompletableFuture<Void> clickhouseTask = CompletableFuture.runAsync(() -> 
            saveToClickHouse(weatherData));
        
        CompletableFuture<Void> redisTask = CompletableFuture.runAsync(() -> 
            cacheToRedis(weatherData));

        CompletableFuture.allOf(postgresTask, clickhouseTask, redisTask).join();
    }

    private void saveToPostgres(List<JsonNode> weatherData) {
        try {
            weatherData.forEach(weather -> {
                // Validate required fields (handle both camelCase and snake_case)
                String gridId = weather.has("gridId") ? weather.get("gridId").asText() 
                              : weather.has("grid_id") ? weather.get("grid_id").asText() : null;
                              
                if (gridId == null || gridId.isEmpty()) {
                    log.warn("Skipping weather record: missing gridId/grid_id");
                    return;
                }
                
                // Get timestamp from various possible fields
                long timestampMillis;
                if (weather.has("timestamp") && !weather.get("timestamp").isNull()) {
                    String timestampStr = weather.get("timestamp").asText();
                    // Parse ISO timestamp string to milliseconds
                    try {
                        timestampMillis = java.time.Instant.parse(timestampStr).toEpochMilli();
                    } catch (Exception e) {
                        timestampMillis = System.currentTimeMillis();
                        log.warn("Failed to parse timestamp '{}', using current time", timestampStr);
                    }
                } else {
                    timestampMillis = System.currentTimeMillis();
                    log.warn("Using current time for gridId {} (no timestamp)", gridId);
                }
                
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                // Extract values with fallbacks for both naming conventions
                double latitude = getFieldValue(weather, "latitude", "latitude");
                double longitude = getFieldValue(weather, "longitude", "longitude");
                
                postgresJdbcTemplate.update(POSTGRES_UPSERT_SQL,
                    gridId,
                    latitude,
                    longitude,
                    getFieldValueOrNull(weather, "temperature", "temperature"),
                    getFieldValueOrNull(weather, "windSpeed", "wind_speed"),
                    getFieldValueOrNull(weather, "windDirection", "wind_direction"),
                    getFieldValueOrNull(weather, "waveHeight", "wave_height"),
                    getFieldValueOrNull(weather, "visibility", "visibility"),
                    getFieldValueOrNull(weather, "pressure", "pressure"),
                    getFieldValueOrNull(weather, "humidity", "humidity"),
                    timestamp,
                    longitude, // for ST_MakePoint
                    latitude   // for ST_MakePoint
                );
            });
            log.info("Saved {} weather records to PostgreSQL", weatherData.size());
        } catch (Exception e) {
            log.error("Error saving weather to PostgreSQL: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void saveToClickHouse(List<JsonNode> weatherData) {
        try {
            weatherData.forEach(weather -> {
                // Validate required fields
                String gridId = weather.has("gridId") ? weather.get("gridId").asText() 
                              : weather.has("grid_id") ? weather.get("grid_id").asText() : null;
                if (gridId == null || gridId.isEmpty()) {
                    return;
                }
                
                // Get timestamp
                long timestampMillis;
                if (weather.has("timestamp") && !weather.get("timestamp").isNull()) {
                    String timestampStr = weather.get("timestamp").asText();
                    try {
                        timestampMillis = java.time.Instant.parse(timestampStr).toEpochMilli();
                    } catch (Exception e) {
                        timestampMillis = System.currentTimeMillis();
                    }
                } else {
                    timestampMillis = System.currentTimeMillis();
                }
                
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                clickhouseJdbcTemplate.update(CLICKHOUSE_INSERT_SQL,
                    gridId,
                    getFieldValue(weather, "latitude", "latitude"),
                    getFieldValue(weather, "longitude", "longitude"),
                    getFieldValueOrNull(weather, "temperature", "temperature"),
                    getFieldValueOrNull(weather, "windSpeed", "wind_speed"),
                    getFieldValueOrNull(weather, "windDirection", "wind_direction"),
                    getFieldValueOrNull(weather, "waveHeight", "wave_height"),
                    getFieldValueOrNull(weather, "visibility", "visibility"),
                    getFieldValueOrNull(weather, "pressure", "pressure"),
                    getFieldValueOrNull(weather, "humidity", "humidity"),
                    timestamp
                );
            });
            log.info("Saved {} weather records to ClickHouse", weatherData.size());
        } catch (Exception e) {
            log.error("Error saving weather to ClickHouse: {}", e.getMessage(), e);
            // Don't throw - allow other saves to continue
        }
    }

    private void cacheToRedis(List<JsonNode> weatherData) {
        try {
            weatherData.forEach(weather -> {
                String gridId = weather.has("gridId") ? weather.get("gridId").asText() 
                              : weather.has("grid_id") ? weather.get("grid_id").asText() : null;
                if (gridId != null && !gridId.isEmpty()) {
                    String key = "weather:grid:" + gridId;
                    String jsonString = weather.toString();
                    redisTemplate.opsForValue().set(key, jsonString, Duration.ofMinutes(30));
                }
            });
            log.info("Cached {} weather records in Redis (for REST API queries)", weatherData.size());
        } catch (Exception e) {
            log.error("Error caching weather to Redis: {}", e.getMessage(), e);
            // Don't throw - allow other saves to continue
        }
    }

    /**
     * Helper method to safely extract nullable double values from JSON
     * Returns null if field doesn't exist or if field value is null
     */
    private Double getDoubleOrNull(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return null;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNull() ? null : fieldNode.asDouble();
    }

    /**
     * Helper method to get required double value with default fallback
     * Returns 0.0 if field doesn't exist or is null
     */
    private Double getDoubleValue(JsonNode node, String fieldName) {
        Double value = getDoubleOrNull(node, fieldName);
        return value != null ? value : 0.0;
    }
    
    /**
     * Helper method to get field value with both camelCase and snake_case fallback
     * Returns null if neither field exists
     */
    private Double getFieldValueOrNull(JsonNode node, String camelCase, String snakeCase) {
        if (node.has(camelCase) && !node.get(camelCase).isNull()) {
            return node.get(camelCase).asDouble();
        }
        if (node.has(snakeCase) && !node.get(snakeCase).isNull()) {
            return node.get(snakeCase).asDouble();
        }
        return null;
    }
    
    /**
     * Helper method to get required field value with fallback to 0.0
     */
    private double getFieldValue(JsonNode node, String camelCase, String snakeCase) {
        Double value = getFieldValueOrNull(node, camelCase, snakeCase);
        return value != null ? value : 0.0;
    }
}
