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
 * Service for persisting vessel alert data to multiple databases
 */
@Slf4j
@Service
public class AlertPersistenceService {

    private final JdbcTemplate postgresJdbcTemplate;
    private final JdbcTemplate clickhouseJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    
    public AlertPersistenceService(
            @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate,
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate,
            RedisTemplate<String, Object> redisTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
        this.clickhouseJdbcTemplate = clickhouseJdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    private static final String POSTGRES_INSERT_SQL = """
        INSERT INTO vessel_alerts 
        (alert_id, mmsi, vessel_name, alert_type, severity, latitude, longitude, 
         speed, timestamp, description, metadata)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
        """;

    private static final String CLICKHOUSE_INSERT_SQL = """
        INSERT INTO vessel_alerts_history
        (alert_id, mmsi, vessel_name, alert_type, severity, latitude, longitude,
         speed, timestamp, description)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    @Transactional
    public void persistVesselAlerts(List<JsonNode> alerts) {
        // Process asynchronously in parallel
        CompletableFuture<Void> postgresTask = CompletableFuture.runAsync(() -> 
            saveToPostgres(alerts));
        
        CompletableFuture<Void> clickhouseTask = CompletableFuture.runAsync(() -> 
            saveToClickHouse(alerts));
        
        CompletableFuture<Void> redisTask = CompletableFuture.runAsync(() -> 
            saveToRedis(alerts));

        CompletableFuture.allOf(postgresTask, clickhouseTask, redisTask).join();
    }

    private void saveToPostgres(List<JsonNode> alerts) {
        try {
            alerts.forEach(alert -> {
                // Validate required fields
                if (!alert.has("alertId") || alert.get("alertId").isNull()) {
                    log.warn("Skipping alert: missing alertId");
                    return;
                }
                if (!alert.has("timestamp") || alert.get("timestamp").isNull()) {
                    log.warn("Skipping alert: missing timestamp for alertId {}", alert.get("alertId").asText());
                    return;
                }
                
                String alertId = alert.get("alertId").asText();
                String metadata = alert.has("metadata") && !alert.get("metadata").isNull() 
                    ? alert.get("metadata").toString() 
                    : "{}";
                
                // Convert timestamp
                long timestampMillis = alert.get("timestamp").asLong();
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                postgresJdbcTemplate.update(POSTGRES_INSERT_SQL,
                    alertId,
                    getIntValue(alert, "mmsi"),
                    getStringValue(alert, "vesselName"),
                    getStringValue(alert, "alertType"),
                    getStringValue(alert, "severity"),
                    getDoubleValue(alert, "latitude"),
                    getDoubleValue(alert, "longitude"),
                    getDoubleValue(alert, "speed"),
                    timestamp,
                    getStringValue(alert, "description"),
                    metadata
                );
            });
            log.info("Saved {} alerts to PostgreSQL", alerts.size());
        } catch (Exception e) {
            log.error("Error saving alerts to PostgreSQL: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void saveToClickHouse(List<JsonNode> alerts) {
        try {
            alerts.forEach(alert -> {
                // Validate required fields
                if (!alert.has("alertId") || alert.get("alertId").isNull()) {
                    return;
                }
                if (!alert.has("timestamp") || alert.get("timestamp").isNull()) {
                    return;
                }
                
                // Convert timestamp
                long timestampMillis = alert.get("timestamp").asLong();
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                clickhouseJdbcTemplate.update(CLICKHOUSE_INSERT_SQL,
                    alert.get("alertId").asText(),
                    getIntValue(alert, "mmsi"),
                    getStringValue(alert, "vesselName"),
                    getStringValue(alert, "alertType"),
                    getStringValue(alert, "severity"),
                    getDoubleValue(alert, "latitude"),
                    getDoubleValue(alert, "longitude"),
                    getDoubleValue(alert, "speed"),
                    timestamp,
                    getStringValue(alert, "description")
                );
            });
            log.info("Saved {} alerts to ClickHouse", alerts.size());
        } catch (Exception e) {
            log.error("Error saving alerts to ClickHouse: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void saveToRedis(List<JsonNode> alerts) {
        try {
            alerts.forEach(alert -> {
                if (alert.has("alertId") && !alert.get("alertId").isNull()) {
                    String alertId = alert.get("alertId").asText();
                    String key = "vessel:alert:" + alertId;
                    redisTemplate.opsForValue().set(key, alert.toString(), Duration.ofHours(1));
                    
                    // Publish to Redis channel for WebSocket broadcasting
                    redisTemplate.convertAndSend("vessel-alerts-stream", alert.toString());
                    
                    // Also maintain a set of active alerts per vessel
                    if (alert.has("mmsi") && !alert.get("mmsi").isNull()) {
                        String vesselKey = "vessel:alerts:" + alert.get("mmsi").asInt();
                        redisTemplate.opsForSet().add(vesselKey, alertId);
                        redisTemplate.expire(vesselKey, Duration.ofHours(1));
                    }
                }
            });
            log.info("Cached {} alerts in Redis and published to WebSocket stream", alerts.size());
        } catch (Exception e) {
            log.error("Error caching alerts to Redis: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Helper method to safely extract string values from JSON
     */
    private String getStringValue(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return "";
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNull() ? "" : fieldNode.asText();
    }

    /**
     * Helper method to safely extract integer values from JSON
     */
    private Integer getIntValue(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return 0;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNull() ? 0 : fieldNode.asInt();
    }

    /**
     * Helper method to safely extract double values from JSON
     */
    private Double getDoubleValue(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return 0.0;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNull() ? 0.0 : fieldNode.asDouble();
    }
}
