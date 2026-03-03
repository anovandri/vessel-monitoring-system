package com.kreasipositif.vms.persistence.controller;

import com.kreasipositif.vms.persistence.service.VesselHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for vessel history tracking and playback
 */
@Slf4j
@RestController
@RequestMapping("/api/vessels/history")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VesselHistoryController {

    private final VesselHistoryService vesselHistoryService;

    /**
     * Search vessels by name (for autocomplete)
     */
    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> searchVessels(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Searching vessels with query: {}", query);
        List<Map<String, Object>> vessels = vesselHistoryService.searchVesselsByName(query, limit);
        return ResponseEntity.ok(vessels);
    }

    /**
     * Get vessel position history for playback
     */
    @GetMapping("/{mmsi}")
    public ResponseEntity<List<Map<String, Object>>> getVesselHistory(
            @PathVariable Long mmsi,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.info("Fetching history for MMSI: {} from {} to {}", mmsi, startDate, endDate);
        List<Map<String, Object>> history = vesselHistoryService.getVesselHistory(mmsi, startDate, endDate);
        return ResponseEntity.ok(history);
    }

    /**
     * Get vessel info by MMSI
     */
    @GetMapping("/{mmsi}/info")
    public ResponseEntity<Map<String, Object>> getVesselInfo(@PathVariable Long mmsi) {
        log.info("Fetching vessel info for MMSI: {}", mmsi);
        Map<String, Object> vesselInfo = vesselHistoryService.getVesselInfo(mmsi);
        
        if (vesselInfo == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(vesselInfo);
    }

    /**
     * Get list of all vessels with recent activity
     */
    @GetMapping("/active")
    public ResponseEntity<List<Map<String, Object>>> getActiveVessels(
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Fetching active vessels from last {} hours", hours);
        List<Map<String, Object>> vessels = vesselHistoryService.getActiveVessels(hours);
        return ResponseEntity.ok(vessels);
    }
}
