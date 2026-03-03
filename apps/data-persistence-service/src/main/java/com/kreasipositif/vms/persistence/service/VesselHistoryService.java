package com.kreasipositif.vms.persistence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Service for retrieving vessel history data from ClickHouse
 */
@Slf4j
@Service
public class VesselHistoryService {

    private final JdbcTemplate clickhouseJdbcTemplate;

    public VesselHistoryService(@Qualifier("clickhouseJdbcTemplate") JdbcTemplate clickhouseJdbcTemplate) {
        this.clickhouseJdbcTemplate = clickhouseJdbcTemplate;
        log.info("VesselHistoryService initialized with JdbcTemplate: {}", clickhouseJdbcTemplate.getDataSource());
    }

    /**
     * Search vessels by name (for autocomplete)
     */
    public List<Map<String, Object>> searchVesselsByName(String query, int limit) {
        log.info("Searching vessels with query: '{}'", query);
        String sql = """
            SELECT 
                mmsi,
                vessel_name,
                vessel_type,
                any(country) as country,
                any(flag_state) as flag_state,
                any(imo_number) as imo_number,
                any(callsign) as callsign
            FROM vessel_monitoring.vessel_positions_history
            WHERE lower(vessel_name) LIKE ?
            GROUP BY mmsi, vessel_name, vessel_type
            ORDER BY vessel_name
            LIMIT ?
            """;
        
        String searchPattern = "%" + query.toLowerCase() + "%";
        return clickhouseJdbcTemplate.queryForList(sql, searchPattern, limit);
    }

    /**
     * Get vessel position history for playback
     */
    public List<Map<String, Object>> getVesselHistory(Long mmsi, LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
            SELECT 
                mmsi,
                vessel_name,
                vessel_type,
                latitude,
                longitude,
                speed,
                course,
                heading,
                navigational_status,
                timestamp,
                country,
                flag_state,
                callsign,
                imo_number,
                destination,
                eta,
                draught,
                cargo_type
            FROM vessel_monitoring.vessel_positions_history
            WHERE mmsi = ?
              AND timestamp >= ?
              AND timestamp <= ?
            ORDER BY timestamp ASC
            """;
        
        // Convert LocalDateTime to epoch milliseconds
        long startEpoch = startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endEpoch = endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        return clickhouseJdbcTemplate.queryForList(sql, mmsi, startEpoch, endEpoch);
    }

    /**
     * Get vessel info by MMSI
     */
    public Map<String, Object> getVesselInfo(Long mmsi) {
        String sql = """
            SELECT 
                mmsi,
                vessel_name,
                vessel_type,
                any(country) as country,
                any(flag_state) as flag_state,
                any(imo_number) as imo_number,
                any(callsign) as callsign,
                any(destination) as destination,
                any(latitude) as latitude,
                any(longitude) as longitude,
                max(timestamp) as last_seen
            FROM vessel_monitoring.vessel_positions_history
            WHERE mmsi = ?
            GROUP BY mmsi, vessel_name, vessel_type
            LIMIT 1
            """;
        
        List<Map<String, Object>> results = clickhouseJdbcTemplate.queryForList(sql, mmsi);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Get list of all vessels with recent activity
     */
    public List<Map<String, Object>> getActiveVessels(int hours) {
        String sql = """
            SELECT DISTINCT
                mmsi,
                vessel_name,
                vessel_type,
                any(country) as country,
                any(flag_state) as flag_state,
                max(timestamp) as last_seen,
                count(*) as position_count
            FROM vessel_monitoring.vessel_positions_history
            WHERE timestamp >= ?
            GROUP BY mmsi, vessel_name, vessel_type
            ORDER BY last_seen DESC
            LIMIT 1000
            """;
        
        // Calculate timestamp for X hours ago
        long hoursAgoEpoch = LocalDateTime.now()
            .minusHours(hours)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
        
        return clickhouseJdbcTemplate.queryForList(sql, hoursAgoEpoch);
    }
}
