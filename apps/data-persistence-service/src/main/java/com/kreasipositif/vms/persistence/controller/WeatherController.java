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
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Configure properly for production
public class WeatherController {

    @Qualifier("postgresJdbcTemplate")
    private final JdbcTemplate postgresJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get weather data for a specific grid
     * GET /api/weather/grid/{gridId}
     */
    @GetMapping("/grid/{gridId}")
    public ResponseEntity<Map<String, Object>> getWeatherByGrid(@PathVariable String gridId) {
        log.debug("Fetching weather for grid: {}", gridId);
        
        // Try Redis cache first
        String cacheKey = "weather:grid:" + gridId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(cached.toString());
                return ResponseEntity.ok(objectMapper.convertValue(jsonNode, Map.class));
            } catch (Exception e) {
                log.warn("Failed to parse cached weather data", e);
            }
        }
        
        // Fallback to PostgreSQL
        String sql = """
            SELECT grid_id, latitude, longitude, temperature, wind_speed, wind_direction,
                   wave_height, visibility, pressure, humidity, timestamp
            FROM weather_data
            WHERE grid_id = ?
            ORDER BY timestamp DESC
            LIMIT 1
            """;
        
        List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(sql, gridId);
        
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(results.get(0));
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
        
        log.debug("Fetching weather near lat={}, lon={}, radius={}km", lat, lon, radius);
        
        String sql = """
            SELECT grid_id, latitude, longitude, temperature, wind_speed, wind_direction,
                   wave_height, visibility, pressure, humidity, timestamp,
                   ST_Distance(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography) / 1000 as distance_km
            FROM weather_data
            WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ? * 1000)
            ORDER BY timestamp DESC, distance_km ASC
            LIMIT 10
            """;
        
        List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(
            sql, lon, lat, lon, lat, radius);
        
        return ResponseEntity.ok(results);
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
}
