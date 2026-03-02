package com.kreasipositif.vms.persistence.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Weather Data
 * Provides on-demand queries for weather information
 */
@Slf4j
@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = "*") // Configure properly for production
public class WeatherController {

    private final JdbcTemplate postgresJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public WeatherController(
            @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Get weather data for a specific grid
     * GET /api/weather/grid/{gridId}
     */
    @GetMapping("/grid/{gridId}")
    public ResponseEntity<Map<String, Object>> getWeatherByGrid(@PathVariable String gridId) {
        try {
            log.info("Fetching weather for grid: {}", gridId);
            
            // Try Redis cache first
            String cacheKey = "weather:grid:" + gridId;
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(cached.toString());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = objectMapper.convertValue(jsonNode, Map.class);
                    log.info("Cache hit for grid: {}", gridId);
                    return ResponseEntity.ok(result);
                } catch (Exception e) {
                    log.warn("Failed to parse cached weather data: {}", e.getMessage());
                }
            }
            
            // Fallback to PostgreSQL - exclude geometry column
            String sql = """
                SELECT grid_id, latitude, longitude, temperature, wind_speed, wind_direction,
                       wave_height, visibility, pressure, humidity, 
                       EXTRACT(EPOCH FROM timestamp) * 1000 as timestamp
                FROM weather_data
                WHERE grid_id = ?
                ORDER BY timestamp DESC
                LIMIT 1
                """;
            
            List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(sql, gridId);
            
            if (results.isEmpty()) {
                log.info("No weather data found for grid: {}", gridId);
                return ResponseEntity.notFound().build();
            }
            
            log.info("Found weather data for grid: {}", gridId);
            return ResponseEntity.ok(results.get(0));
        } catch (Exception e) {
            log.error("Error fetching weather for grid {}: {}", gridId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get weather data for nearby location
     * GET /api/weather/nearby?lat=1.0&lon=104.0&radius=50
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<Map<String, Object>>> getWeatherNearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "50") double radius) {
        
        try {
            log.info("Fetching weather near lat={}, lon={}, radius={}km", lat, lon, radius);
            
            // Exclude geometry column and convert timestamp to epoch milliseconds
            String sql = """
                SELECT grid_id, latitude, longitude, temperature, wind_speed, wind_direction,
                       wave_height, visibility, pressure, humidity, 
                       EXTRACT(EPOCH FROM timestamp) * 1000 as timestamp,
                       ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 as distance_km
                FROM weather_data
                WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ? * 1000)
                ORDER BY timestamp DESC, distance_km ASC
                LIMIT 10
                """;
            
            List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(
                sql, lon, lat, lon, lat, radius);
            
            log.info("Found {} weather records near lat={}, lon={}", results.size(), lat, lon);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error fetching weather near lat={}, lon={}: {}", lat, lon, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all weather grid IDs
     * GET /api/weather/grids
     */
    @GetMapping("/grids")
    public ResponseEntity<List<String>> getAllGrids() {
        String sql = "SELECT DISTINCT grid_id FROM weather_data ORDER BY grid_id";
        List<String> gridIds = postgresJdbcTemplate.queryForList(sql, String.class);
        return ResponseEntity.ok(gridIds);
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "weather-api");
        return ResponseEntity.ok(status);
    }

    /**
     * Simple test endpoint
     */
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        try {
            log.info("Test endpoint called");
            String sql = "SELECT COUNT(*) FROM weather_data";
            Integer count = postgresJdbcTemplate.queryForObject(sql, Integer.class);
            log.info("Weather data count: {}", count);
            
            // Test actual query
            String testSql = """
                SELECT grid_id, latitude, longitude, temperature
                FROM weather_data
                WHERE grid_id = ?
                LIMIT 1
                """;
            List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(testSql, "-61_1069");
            log.info("Query result size: {}", results.size());
            
            return ResponseEntity.ok("Database accessible. Weather records: " + count + ", Test query result: " + results.size());
        } catch (Exception e) {
            log.error("Test endpoint error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
