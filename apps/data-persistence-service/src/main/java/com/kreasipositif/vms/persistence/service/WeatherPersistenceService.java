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
                // Validate required fields
                if (!weather.has("gridId") || weather.get("gridId").isNull()) {
                    log.warn("Skipping weather record: missing gridId");
                    return;
                }
                if (!weather.has("timestamp") || weather.get("timestamp").isNull()) {
                    log.warn("Skipping weather record: missing timestamp for gridId {}", weather.get("gridId").asText());
                    return;
                }
                
                // Convert timestamp
                long timestampMillis = weather.get("timestamp").asLong();
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                postgresJdbcTemplate.update(POSTGRES_UPSERT_SQL,
                    weather.get("gridId").asText(),
                    getDoubleValue(weather, "latitude"),
                    getDoubleValue(weather, "longitude"),
                    getDoubleOrNull(weather, "temperature"),
                    getDoubleOrNull(weather, "windSpeed"),
                    getDoubleOrNull(weather, "windDirection"),
                    getDoubleOrNull(weather, "waveHeight"),
                    getDoubleOrNull(weather, "visibility"),
                    getDoubleOrNull(weather, "pressure"),
                    getDoubleOrNull(weather, "humidity"),
                    timestamp,
                    getDoubleValue(weather, "longitude"), // for ST_MakePoint
                    getDoubleValue(weather, "latitude")   // for ST_MakePoint
                );
            });
            log.debug("Saved {} weather records to PostgreSQL", weatherData.size());
        } catch (Exception e) {
            log.error("Error saving weather to PostgreSQL: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void saveToClickHouse(List<JsonNode> weatherData) {
        try {
            weatherData.forEach(weather -> {
                // Validate required fields
                if (!weather.has("gridId") || weather.get("gridId").isNull()) {
                    return;
                }
                if (!weather.has("timestamp") || weather.get("timestamp").isNull()) {
                    return;
                }
                
                // Convert timestamp
                long timestampMillis = weather.get("timestamp").asLong();
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                clickhouseJdbcTemplate.update(CLICKHOUSE_INSERT_SQL,
                    weather.get("gridId").asText(),
                    getDoubleValue(weather, "latitude"),
                    getDoubleValue(weather, "longitude"),
                    getDoubleOrNull(weather, "temperature"),
                    getDoubleOrNull(weather, "windSpeed"),
                    getDoubleOrNull(weather, "windDirection"),
                    getDoubleOrNull(weather, "waveHeight"),
                    getDoubleOrNull(weather, "visibility"),
                    getDoubleOrNull(weather, "pressure"),
                    getDoubleOrNull(weather, "humidity"),
                    timestamp
                );
            });
            log.debug("Saved {} weather records to ClickHouse", weatherData.size());
        } catch (Exception e) {
            log.error("Error saving weather to ClickHouse: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void cacheToRedis(List<JsonNode> weatherData) {
        try {
            weatherData.forEach(weather -> {
                if (weather.has("gridId") && !weather.get("gridId").isNull()) {
                    String key = "weather:grid:" + weather.get("gridId").asText();
                    String jsonString = weather.toString();
                    redisTemplate.opsForValue().set(key, jsonString, Duration.ofMinutes(30));
                }
            });
            log.debug("Cached {} weather records in Redis (for REST API queries)", weatherData.size());
        } catch (Exception e) {
            log.error("Error caching weather to Redis: {}", e.getMessage(), e);
            throw e;
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
}
