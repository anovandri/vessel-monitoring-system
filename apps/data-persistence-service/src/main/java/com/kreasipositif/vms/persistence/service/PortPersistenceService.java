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
 * Service for persisting port operational data to multiple databases
 */
@Slf4j
@Service
public class PortPersistenceService {

    private final JdbcTemplate postgresJdbcTemplate;
    private final JdbcTemplate clickhouseJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public PortPersistenceService(
            @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate,
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate,
            RedisTemplate<String, Object> redisTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
        this.clickhouseJdbcTemplate = clickhouseJdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    private static final String POSTGRES_UPSERT_SQL = """
        INSERT INTO port_operations 
        (operation_id, port_id, port_name, mmsi, vessel_name, operation_type, 
         berth_number, arrival_time, departure_time, status, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (operation_id) 
        DO UPDATE SET
            status = EXCLUDED.status,
            departure_time = EXCLUDED.departure_time,
            updated_at = CURRENT_TIMESTAMP
        """;

    private static final String CLICKHOUSE_INSERT_SQL = """
        INSERT INTO port_operations_history
        (operation_id, port_id, port_name, mmsi, vessel_name, operation_type,
         berth_number, arrival_time, departure_time, status, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    @Transactional
    public void persistPortData(List<JsonNode> portData) {
        // Port data is queried via REST API, not pushed via WebSocket
        CompletableFuture<Void> postgresTask = CompletableFuture.runAsync(() -> 
            saveToPostgres(portData));
        
        CompletableFuture<Void> clickhouseTask = CompletableFuture.runAsync(() -> 
            saveToClickHouse(portData));
        
        CompletableFuture<Void> redisTask = CompletableFuture.runAsync(() -> 
            cacheToRedis(portData));

        CompletableFuture.allOf(postgresTask, clickhouseTask, redisTask).join();
    }

    private void saveToPostgres(List<JsonNode> portData) {
        try {
            portData.forEach(port -> {
                // Validate required fields
                if (!port.has("operationId") || port.get("operationId").isNull() ||
                    !port.has("timestamp") || port.get("timestamp").isNull()) {
                    log.warn("Skipping port operation with missing required fields");
                    return;
                }
                
                // Convert timestamp from milliseconds to java.sql.Timestamp
                long timestampMillis = port.get("timestamp").asLong();
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                // Convert time fields if present
                Timestamp arrivalTime = null;
                if (port.has("arrivalTime") && !port.get("arrivalTime").isNull()) {
                    long arrivalMillis = port.get("arrivalTime").asLong();
                    arrivalTime = new Timestamp(arrivalMillis);
                }
                
                Timestamp departureTime = null;
                if (port.has("departureTime") && !port.get("departureTime").isNull()) {
                    long departureMillis = port.get("departureTime").asLong();
                    departureTime = new Timestamp(departureMillis);
                }
                
                postgresJdbcTemplate.update(POSTGRES_UPSERT_SQL,
                    port.get("operationId").asText(),
                    getStringValue(port, "portId"),
                    getStringValue(port, "portName"),
                    getIntOrNull(port, "mmsi"),
                    getStringOrNull(port, "vesselName"),
                    getStringValue(port, "operationType"),
                    getStringOrNull(port, "berthNumber"),
                    arrivalTime,
                    departureTime,
                    getStringValue(port, "status"),
                    timestamp
                );
            });
            log.debug("Saved {} port operations to PostgreSQL", portData.size());
        } catch (Exception e) {
            log.error("Error saving port data to PostgreSQL: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void saveToClickHouse(List<JsonNode> portData) {
        try {
            portData.forEach(port -> {
                // Validate required fields
                if (!port.has("operationId") || port.get("operationId").isNull() ||
                    !port.has("timestamp") || port.get("timestamp").isNull()) {
                    return;
                }
                
                // Convert timestamp from milliseconds to java.sql.Timestamp
                long timestampMillis = port.get("timestamp").asLong();
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                // Convert time fields if present
                Timestamp arrivalTime = null;
                if (port.has("arrivalTime") && !port.get("arrivalTime").isNull()) {
                    long arrivalMillis = port.get("arrivalTime").asLong();
                    arrivalTime = new Timestamp(arrivalMillis);
                }
                
                Timestamp departureTime = null;
                if (port.has("departureTime") && !port.get("departureTime").isNull()) {
                    long departureMillis = port.get("departureTime").asLong();
                    departureTime = new Timestamp(departureMillis);
                }
                
                clickhouseJdbcTemplate.update(CLICKHOUSE_INSERT_SQL,
                    port.get("operationId").asText(),
                    getStringValue(port, "portId"),
                    getStringValue(port, "portName"),
                    getIntOrNull(port, "mmsi"),
                    getStringOrNull(port, "vesselName"),
                    getStringValue(port, "operationType"),
                    getStringOrNull(port, "berthNumber"),
                    arrivalTime,
                    departureTime,
                    getStringValue(port, "status"),
                    timestamp
                );
            });
            log.debug("Saved {} port operations to ClickHouse", portData.size());
        } catch (Exception e) {
            log.error("Error saving port data to ClickHouse: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void cacheToRedis(List<JsonNode> portData) {
        try {
            portData.forEach(port -> {
                // Validate required fields before caching
                if (!port.has("operationId") || port.get("operationId").isNull() ||
                    !port.has("portId") || port.get("portId").isNull()) {
                    return;
                }
                
                String operationId = port.get("operationId").asText();
                String portId = port.get("portId").asText();
                String jsonString = port.toString();
                
                String key = "port:operation:" + operationId;
                redisTemplate.opsForValue().set(key, jsonString, Duration.ofMinutes(10));
                
                // Also maintain active operations per port (for REST API queries)
                String portKey = "port:active:" + portId;
                redisTemplate.opsForSet().add(portKey, operationId);
                redisTemplate.expire(portKey, Duration.ofMinutes(10));
            });
            log.debug("Cached {} port operations in Redis (for REST API queries)", portData.size());
        } catch (Exception e) {
            log.error("Error caching port data to Redis: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Helper method to safely extract required string values from JSON
     */
    private String getStringValue(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return "";
    }
    
    /**
     * Helper method to safely extract nullable string values from JSON
     */
    private String getStringOrNull(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asText();
        }
        return null;
    }
    
    /**
     * Helper method to safely extract nullable integer values from JSON
     */
    private Integer getIntOrNull(JsonNode node, String fieldName) {
        if (node.has(fieldName) && !node.get(fieldName).isNull()) {
            return node.get(fieldName).asInt();
        }
        return null;
    }
}

