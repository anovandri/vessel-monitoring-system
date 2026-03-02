package com.kreasipositif.vms.collector.mock;

import com.kreasipositif.vms.collector.aisstream.AISStreamMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Mock AIS Data Generator for Indonesian Maritime Demo.
 * Loads vessel data from mock-vessels.txt file and generates realistic AIS messages.
 * Vessels are positioned in actual shipping lanes: Malacca Strait, Java Sea, 
 * Makassar Strait, Banda Sea, Celebes Sea, and Singapore Strait.
 */
@Slf4j
@Component
public class MockAISDataGenerator {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    
    // Indonesian vessel fleet loaded from text file
    private final List<VesselTemplate> INDONESIAN_VESSELS;

    public MockAISDataGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.INDONESIAN_VESSELS = loadVesselsFromFile();
        log.info("Mock AIS Data Generator initialized with {} Indonesian vessels", INDONESIAN_VESSELS.size());
    }

    /**
     * Load vessel templates from the mock-vessels.txt file
     */
    private List<VesselTemplate> loadVesselsFromFile() {
        List<VesselTemplate> vessels = new ArrayList<>();
        
        try {
            ClassPathResource resource = new ClassPathResource("mock-vessels.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                
                vessels = reader.lines()
                    .filter(line -> !line.trim().isEmpty() && !line.startsWith("#"))
                    .map(this::parseVesselLine)
                    .filter(vessel -> vessel != null)
                    .collect(Collectors.toList());
                
                log.info("Successfully loaded {} vessels from mock-vessels.txt", vessels.size());
            }
        } catch (Exception e) {
            log.error("Failed to load vessels from mock-vessels.txt: {}", e.getMessage(), e);
            // Return empty list instead of crashing
        }
        
        return vessels;
    }

    /**
     * Parse a single vessel line from the text file
     * Format: MMSI|Ship Name|IMO|Call Sign|Type Code|Latitude|Longitude|Area
     */
    private VesselTemplate parseVesselLine(String line) {
        try {
            String[] parts = line.split("\\|");
            if (parts.length != 8) {
                log.warn("Invalid vessel line format (expected 8 parts): {}", line);
                return null;
            }
            
            Long mmsi = Long.parseLong(parts[0].trim());
            String shipName = parts[1].trim();
            Long imoNumber = Long.parseLong(parts[2].trim());
            String callSign = parts[3].trim();
            Integer shipType = Integer.parseInt(parts[4].trim());
            double baseLat = Double.parseDouble(parts[5].trim());
            double baseLon = Double.parseDouble(parts[6].trim());
            String area = parts[7].trim();
            
            return new VesselTemplate(mmsi, shipName, imoNumber, callSign, shipType, baseLat, baseLon, area);
        } catch (Exception e) {
            log.warn("Failed to parse vessel line: {} - Error: {}", line, e.getMessage());
            return null;
        }
    }

    /**
     * Generate a batch of mock AIS messages for Indonesian vessels
     * @param count Number of messages to generate
     * @return List of JSON strings representing AIS messages
     */
    public List<String> generateMockMessages(int count) {
        List<String> messages = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            VesselTemplate vessel = INDONESIAN_VESSELS.get(random.nextInt(INDONESIAN_VESSELS.size()));
            AISStreamMessage message = createRealisticMessage(vessel);
            
            try {
                String json = objectMapper.writeValueAsString(message);
                messages.add(json);
                log.debug("Generated mock AIS message for vessel: {} in {}", 
                        vessel.shipName, vessel.area);
            } catch (Exception e) {
                log.error("Failed to serialize mock AIS message", e);
            }
        }
        
        return messages;
    }

    /**
     * Generate a single mock AIS message for a random Indonesian vessel
     */
    public String generateSingleMessage() {
        VesselTemplate vessel = INDONESIAN_VESSELS.get(random.nextInt(INDONESIAN_VESSELS.size()));
        AISStreamMessage message = createRealisticMessage(vessel);
        
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize mock AIS message", e);
            return null;
        }
    }

    /**
     * Create a realistic AIS message with vessel movement simulation.
     * Movement is constrained to stay in maritime areas.
     */
    private AISStreamMessage createRealisticMessage(VesselTemplate vessel) {
        AISStreamMessage message = new AISStreamMessage();
        message.setMessageType("PositionReport");
        
        // Create metadata
        AISStreamMessage.MetaData metaData = new AISStreamMessage.MetaData();
        metaData.setMmsi(vessel.mmsi);
        metaData.setShipName(vessel.shipName);
        metaData.setImoNumber(vessel.imoNumber);
        metaData.setCallSign(vessel.callSign);
        metaData.setShipType(vessel.shipType);
        message.setMetaData(metaData);
        
        // Create message with position report
        AISStreamMessage.Message msg = new AISStreamMessage.Message();
        
        // Create position report with realistic movement
        AISStreamMessage.PositionReport posReport = new AISStreamMessage.PositionReport();
        
        // Reduced random movement to stay in maritime areas (±0.05 degrees ~ ±5.5km)
        // This keeps vessels in shipping lanes without drifting to land
        double latOffset = (random.nextDouble() - 0.5) * 0.1;
        double lonOffset = (random.nextDouble() - 0.5) * 0.1;
        posReport.setLatitude(vessel.baseLat + latOffset);
        posReport.setLongitude(vessel.baseLon + lonOffset);
        
        // Realistic speed: 0-20 knots depending on vessel type
        double maxSpeed = vessel.shipType == 60 ? 25.0 : // Passenger ships faster
                         vessel.shipType == 80 ? 15.0 : // Tankers slower
                         18.0; // Container/cargo ships
        posReport.setSog(random.nextDouble() * maxSpeed);
        
        // Course over ground: 0-360 degrees
        posReport.setCog(random.nextDouble() * 360.0);
        
        // True heading
        posReport.setTrueHeading(random.nextInt(360));
        
        // Navigational status (0=under way using engine, 1=at anchor, 5=moored)
        int navStatus = posReport.getSog() < 0.5 ? 1 : 0; // At anchor if speed < 0.5 knots
        posReport.setNavigationalStatus(navStatus);
        
        // Timestamp
        posReport.setTimestamp(Instant.now().toString());
        
        msg.setPositionReport(posReport);
        message.setMessage(msg);
        
        return message;
    }

    /**
     * Get list of areas being monitored
     */
    public List<String> getMonitoredAreas() {
        return INDONESIAN_VESSELS.stream()
                .map(v -> v.area)
                .distinct()
                .toList();
    }

    /**
     * Get count of vessels in fleet
     */
    public int getVesselCount() {
        return INDONESIAN_VESSELS.size();
    }

    /**
     * Template for Indonesian vessels with base positions
     */
    private static class VesselTemplate {
        final Long mmsi;
        final String shipName;
        final Long imoNumber;
        final String callSign;
        final Integer shipType;
        final double baseLat;
        final double baseLon;
        final String area;

        VesselTemplate(Long mmsi, String shipName, Long imoNumber, String callSign, 
                      Integer shipType, double baseLat, double baseLon, String area) {
            this.mmsi = mmsi;
            this.shipName = shipName;
            this.imoNumber = imoNumber;
            this.callSign = callSign;
            this.shipType = shipType;
            this.baseLat = baseLat;
            this.baseLon = baseLon;
            this.area = area;
        }
    }
}
