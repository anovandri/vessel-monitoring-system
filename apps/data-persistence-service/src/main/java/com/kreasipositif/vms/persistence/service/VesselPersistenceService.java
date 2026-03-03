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
 * Service for persisting vessel position data to multiple databases
 */
@Slf4j
@Service
public class VesselPersistenceService {

    private final JdbcTemplate postgresJdbcTemplate;
    private final JdbcTemplate clickhouseJdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    // TODO: Add ElasticsearchClient when implementing search functionality
    
    public VesselPersistenceService(
            @Qualifier("postgresJdbcTemplate") JdbcTemplate postgresJdbcTemplate,
            @Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate,
            RedisTemplate<String, Object> redisTemplate) {
        this.postgresJdbcTemplate = postgresJdbcTemplate;
        this.clickhouseJdbcTemplate = clickhouseJdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    private static final String POSTGRES_UPSERT_SQL = """
        INSERT INTO vessel_positions 
        (mmsi, vessel_name, vessel_type, latitude, longitude, speed, course, heading, 
         navigational_status, timestamp, location, country, flag_state, callsign, 
         imo_number, destination, eta, draught, cargo_type)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (mmsi) 
        DO UPDATE SET
            vessel_name = EXCLUDED.vessel_name,
            latitude = EXCLUDED.latitude,
            longitude = EXCLUDED.longitude,
            speed = EXCLUDED.speed,
            course = EXCLUDED.course,
            heading = EXCLUDED.heading,
            navigational_status = EXCLUDED.navigational_status,
            timestamp = EXCLUDED.timestamp,
            location = EXCLUDED.location,
            destination = EXCLUDED.destination,
            eta = EXCLUDED.eta,
            draught = EXCLUDED.draught,
            updated_at = CURRENT_TIMESTAMP
        """;

    private static final String CLICKHOUSE_INSERT_SQL = """
        INSERT INTO vessel_monitoring.vessel_positions_history
        (mmsi, vessel_name, vessel_type, latitude, longitude, speed, course, heading,
         navigational_status, timestamp, country, flag_state, callsign, imo_number,
         destination, eta, draught, cargo_type)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, toDateTime(?), ?, ?, ?, ?, toDateTime(?), ?, ?, ?)
        """;

    @Transactional
    public void persistVesselPositions(List<JsonNode> positions) {
        // Process asynchronously in parallel using virtual threads
        CompletableFuture<Void> postgresTask = CompletableFuture.runAsync(() -> 
            saveToPostgres(positions));
        
        CompletableFuture<Void> clickhouseTask = CompletableFuture.runAsync(() -> 
            saveToClickHouse(positions));
        
        CompletableFuture<Void> redisTask = CompletableFuture.runAsync(() -> 
            saveToRedis(positions));

        // Wait for all operations to complete
        CompletableFuture.allOf(postgresTask, clickhouseTask, redisTask).join();
    }

    private void saveToPostgres(List<JsonNode> positions) {
        try {
            positions.forEach(position -> {
                // Log the incoming message for debugging
                log.debug("Processing vessel position: {}", position.toPrettyString());
                
                // Convert timestamp from milliseconds to java.sql.Timestamp
                long timestampMillis = position.get("timestamp").asLong();
                Timestamp timestamp = new Timestamp(timestampMillis);
                
                postgresJdbcTemplate.update(POSTGRES_UPSERT_SQL,
                    position.get("mmsi").asInt(),
                    position.get("vesselName").asText(),
                    getStringOrNull(position, "vesselType"),
                    position.get("latitude").asDouble(),
                    position.get("longitude").asDouble(),
                    position.get("speed").asDouble(),
                    position.get("course").asDouble(),
                    getDoubleOrNull(position, "heading"),
                    getStringOrNull(position, "navigationalStatus"),
                    timestamp,  // Use Timestamp object instead of asText()
                    position.get("longitude").asDouble(), // for ST_MakePoint
                    position.get("latitude").asDouble(),  // for ST_MakePoint
                    getStringOrNull(position, "country"),
                    getStringOrNull(position, "flagState"),
                    getStringOrNull(position, "callsign"),
                    getStringOrNull(position, "imoNumber"),
                    getStringOrNull(position, "destination"),
                    getStringOrNull(position, "eta"),
                    getDoubleOrNull(position, "draught"),
                    getStringOrNull(position, "cargoType")
                );
            });
            log.debug("Saved {} positions to PostgreSQL", positions.size());
        } catch (Exception e) {
            log.error("Error saving to PostgreSQL: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void saveToClickHouse(List<JsonNode> positions) {
        try {
            positions.forEach(position -> {
                // Parse timestamp - handle both string and numeric formats
                JsonNode timestampNode = position.get("timestamp");
                long timestampMillis;
                
                if (timestampNode.isTextual()) {
                    // String format - might be with decimal (seconds.nanos) or without
                    String tsStr = timestampNode.asText();
                    try {
                        // Try parsing as double first (handles "1772524274.199106000")
                        double tsDouble = Double.parseDouble(tsStr);
                        timestampMillis = (long) (tsDouble * 1000); // Convert seconds to millis
                    } catch (NumberFormatException e) {
                        try {
                            // Try as plain long
                            timestampMillis = Long.parseLong(tsStr);
                        } catch (NumberFormatException e2) {
                            // Might be ISO-8601
                            timestampMillis = java.time.Instant.parse(tsStr).toEpochMilli();
                        }
                    }
                } else if (timestampNode.isNumber()) {
                    // Numeric format - could be seconds or millis
                    timestampMillis = timestampNode.longValue();
                } else {
                    timestampMillis = System.currentTimeMillis();
                    log.warn("Unable to parse timestamp, using current time");
                }
                
                long timestampSeconds = timestampMillis / 1000;
                
                log.info("ClickHouse Insert - MMSI: {}, Raw: {}, Parsed millis: {}, Seconds: {}", 
                    position.get("mmsi").asInt(), timestampNode, timestampMillis, timestampSeconds);
                
                // Convert eta similarly
                Long etaSeconds = null;
                if (position.has("eta") && !position.get("eta").isNull()) {
                    JsonNode etaNode = position.get("eta");
                    long etaMillis;
                    if (etaNode.isTextual()) {
                        try {
                            etaMillis = Long.parseLong(etaNode.asText());
                        } catch (NumberFormatException e) {
                            etaMillis = java.time.Instant.parse(etaNode.asText()).toEpochMilli();
                        }
                    } else {
                        etaMillis = etaNode.longValue();
                    }
                    etaSeconds = etaMillis / 1000;
                }
                
                clickhouseJdbcTemplate.update(CLICKHOUSE_INSERT_SQL,
                    position.get("mmsi").asInt(),
                    position.get("vesselName").asText(),
                    getStringOrEmpty(position, "vesselType"),
                    position.get("latitude").asDouble(),
                    position.get("longitude").asDouble(),
                    position.get("speed").asDouble(),
                    position.get("course").asDouble(),
                    getDoubleOrNull(position, "heading"),
                    getStringOrEmpty(position, "navigationalStatus"),
                    timestampSeconds,  // Use seconds for ClickHouse DateTime with toDateTime()
                    getStringOrEmpty(position, "country"),
                    getStringOrEmpty(position, "flagState"),
                    getStringOrEmpty(position, "callsign"),
                    getStringOrEmpty(position, "imoNumber"),
                    getStringOrEmpty(position, "destination"),
                    etaSeconds,  // Use seconds for ClickHouse DateTime
                    getDoubleOrNull(position, "draught"),
                    getStringOrEmpty(position, "cargoType")
                );
            });
            log.debug("Saved {} positions to ClickHouse", positions.size());
        } catch (Exception e) {
            log.error("Error saving to ClickHouse: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void saveToRedis(List<JsonNode> positions) {
        try {
            positions.forEach(position -> {
                String key = "vessel:position:" + position.get("mmsi").asInt();
                String jsonString = position.toString();  // JsonNode.toString() produces valid JSON
                redisTemplate.opsForValue().set(key, jsonString, Duration.ofMinutes(5));
                
                // Publish to Redis channel for WebSocket broadcasting (send as JSON string)
                redisTemplate.convertAndSend("vessel-positions-stream", jsonString);
            });
            log.debug("Cached {} positions in Redis and published to WebSocket stream", positions.size());
        } catch (Exception e) {
            log.error("Error caching to Redis: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Helper method to safely extract nullable string values from JSON
     * Returns null if field doesn't exist or if field value is null
     */
    private String getStringOrNull(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return null;
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNull() ? null : fieldNode.asText();
    }

    /**
     * Helper method to safely extract string values from JSON for ClickHouse
     * Returns empty string if field doesn't exist or if field value is null
     * (ClickHouse non-nullable string columns cannot accept NULL)
     */
    private String getStringOrEmpty(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return "";
        }
        JsonNode fieldNode = node.get(fieldName);
        return fieldNode.isNull() ? "" : fieldNode.asText();
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
}
