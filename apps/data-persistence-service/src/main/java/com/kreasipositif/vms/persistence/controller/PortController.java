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

import java.util.*;

/**
 * REST API Controller for Port Data
 * Provides on-demand queries for port operations and information
 */
@Slf4j
@RestController
@RequestMapping("/api/ports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Configure properly for production
public class PortController {

    @Qualifier("postgresJdbcTemplate")
    private final JdbcTemplate postgresJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Get port operation by ID
     * GET /api/ports/operations/{operationId}
     */
    @GetMapping("/operations/{operationId}")
    public ResponseEntity<Map<String, Object>> getOperationById(@PathVariable String operationId) {
        log.debug("Fetching port operation: {}", operationId);
        
        // Try Redis cache first
        String cacheKey = "port:operation:" + operationId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(cached.toString());
                return ResponseEntity.ok(objectMapper.convertValue(jsonNode, Map.class));
            } catch (Exception e) {
                log.warn("Failed to parse cached port operation", e);
            }
        }
        
        // Fallback to PostgreSQL
        String sql = """
            SELECT operation_id, port_id, port_name, mmsi, vessel_name, operation_type,
                   berth_number, arrival_time, departure_time, status, timestamp
            FROM port_operations
            WHERE operation_id = ?
            """;
        
        List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(sql, operationId);
        
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(results.get(0));
    }

    /**
     * Get active operations for a port
     * GET /api/ports/{portId}/operations
     */
    @GetMapping("/{portId}/operations")
    public ResponseEntity<List<Map<String, Object>>> getPortOperations(
            @PathVariable String portId,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.debug("Fetching operations for port: {}", portId);
        
        String sql = """
            SELECT operation_id, port_id, port_name, mmsi, vessel_name, operation_type,
                   berth_number, arrival_time, departure_time, status, timestamp
            FROM port_operations
            WHERE port_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;
        
        List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(sql, portId, limit);
        
        return ResponseEntity.ok(results);
    }

    /**
     * Get operations by vessel MMSI
     * GET /api/ports/vessel/{mmsi}/operations
     */
    @GetMapping("/vessel/{mmsi}/operations")
    public ResponseEntity<List<Map<String, Object>>> getVesselOperations(
            @PathVariable int mmsi,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.debug("Fetching port operations for vessel: {}", mmsi);
        
        String sql = """
            SELECT operation_id, port_id, port_name, mmsi, vessel_name, operation_type,
                   berth_number, arrival_time, departure_time, status, timestamp
            FROM port_operations
            WHERE mmsi = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;
        
        List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(sql, mmsi, limit);
        
        return ResponseEntity.ok(results);
    }

    /**
     * Get list of all ports
     * GET /api/ports/list
     */
    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> getAllPorts() {
        String sql = """
            SELECT DISTINCT port_id, port_name,
                   COUNT(*) as operation_count,
                   MAX(timestamp) as last_update
            FROM port_operations
            GROUP BY port_id, port_name
            ORDER BY port_name
            """;
        
        List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(results);
    }

    /**
     * Get port statistics
     * GET /api/ports/{portId}/stats
     */
    @GetMapping("/{portId}/stats")
    public ResponseEntity<Map<String, Object>> getPortStats(@PathVariable String portId) {
        log.debug("Fetching statistics for port: {}", portId);
        
        String sql = """
            SELECT 
                port_id,
                port_name,
                COUNT(*) as total_operations,
                COUNT(DISTINCT mmsi) as unique_vessels,
                SUM(CASE WHEN operation_type = 'ARRIVAL' THEN 1 ELSE 0 END) as arrivals,
                SUM(CASE WHEN operation_type = 'DEPARTURE' THEN 1 ELSE 0 END) as departures,
                SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
                MAX(timestamp) as last_update
            FROM port_operations
            WHERE port_id = ?
            GROUP BY port_id, port_name
            """;
        
        List<Map<String, Object>> results = postgresJdbcTemplate.queryForList(sql, portId);
        
        if (results.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(results.get(0));
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "port-api");
        return ResponseEntity.ok(status);
    }
}
