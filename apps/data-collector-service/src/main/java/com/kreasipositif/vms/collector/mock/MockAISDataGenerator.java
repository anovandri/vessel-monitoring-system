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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock AIS Data Generator for Indonesian Maritime Demo.
 * Loads vessel data from indonesia_mock_ais_1000_v4.txt file and generates realistic AIS messages.
 * Vessels are positioned in actual shipping lanes: Malacca Strait, Java Sea, 
 * Makassar Strait, Banda Sea, Celebes Sea, and Singapore Strait.
 * 
 * This generator maintains vessel state across collection cycles to simulate realistic movement.
 * Each vessel maintains consistent ID, heading, speed, and position history.
 */
@Slf4j
@Component
public class MockAISDataGenerator {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    
    // Indonesian vessel fleet loaded from text file
    private final List<VesselTemplate> INDONESIAN_VESSELS;
    
    // Track current state of each vessel (position, heading, speed)
    private final Map<Long, VesselState> vesselStates = new ConcurrentHashMap<>();
    
    // Track when each vessel was last used to ensure fair distribution
    private final Map<Long, Long> lastUsedTimestamp = new ConcurrentHashMap<>();
    
    // Time between position updates (in milliseconds)
    private static final long UPDATE_INTERVAL_MS = 30000; // 30 seconds
    
    // Flag to track if vessels have been initialized
    private volatile boolean vesselsInitialized = false;

    public MockAISDataGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.INDONESIAN_VESSELS = loadVesselsFromFile();
        log.info("Mock AIS Data Generator initialized with {} Indonesian vessels", INDONESIAN_VESSELS.size());
    }

    /**
     * Load vessel templates from the indonesia_mock_ais_1000_v4.txt file
     */
    private List<VesselTemplate> loadVesselsFromFile() {
        List<VesselTemplate> vessels = new ArrayList<>();
        
        try {
            ClassPathResource resource = new ClassPathResource("indonesia_mock_ais_1000_v4.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                
                vessels = reader.lines()
                    .skip(1) // Skip header line
                    .filter(line -> !line.trim().isEmpty() && !line.startsWith("#"))
                    .map(this::parseVesselLine)
                    .filter(vessel -> vessel != null)
                    .collect(Collectors.toList());
                
                log.info("Successfully loaded {} vessels from indonesia_mock_ais_1000_v4.txt", vessels.size());
            }
        } catch (Exception e) {
            log.error("Failed to load vessels from indonesia_mock_ais_1000_v4.txt: {}", e.getMessage(), e);
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
     * Generate a batch of mock AIS messages for Indonesian vessels.
     * On first call, initializes all vessels with their base positions.
     * On subsequent calls, updates existing vessel positions based on their heading and speed.
     * 
     * @param count Number of messages to generate
     * @return List of JSON strings representing AIS messages
     */
    public List<String> generateMockMessages(int count) {
        List<String> messages = new ArrayList<>();
        
        // Initialize vessels on first call
        if (!vesselsInitialized) {
            initializeAllVessels();
            vesselsInitialized = true;
        }
        
        // Select vessels to update in this batch
        List<Long> vesselsToUpdate = selectVesselsForUpdate(count);
        
        for (Long mmsi : vesselsToUpdate) {
            VesselState state = vesselStates.get(mmsi);
            if (state == null) continue;
            
            // Update vessel position based on heading and speed
            updateVesselPosition(state);
            
            // Create AIS message with updated position
            AISStreamMessage message = createMessageFromState(state);
            
            try {
                String json = objectMapper.writeValueAsString(message);
                messages.add(json);
                
                // Update last used timestamp
                lastUsedTimestamp.put(mmsi, System.currentTimeMillis());
                
                log.debug("Generated mock AIS message for vessel: {} at ({}, {})", 
                        state.template.shipName, state.currentLat, state.currentLon);
            } catch (Exception e) {
                log.error("Failed to serialize mock AIS message for MMSI {}", mmsi, e);
            }
        }
        
        log.info("Generated {} mock AIS messages from {} total vessels", messages.size(), vesselStates.size());
        return messages;
    }
    
    /**
     * Initialize all vessels with their base positions and random heading/speed
     */
    private void initializeAllVessels() {
        log.info("Initializing {} vessels with base positions...", INDONESIAN_VESSELS.size());
        
        for (VesselTemplate vessel : INDONESIAN_VESSELS) {
            VesselState state = new VesselState(vessel);
            
            // Initialize with base position
            state.currentLat = vessel.baseLat;
            state.currentLon = vessel.baseLon;
            
            // Random initial heading (0-360 degrees)
            state.heading = random.nextDouble() * 360.0;
            
            // Random initial speed based on vessel type
            double maxSpeed = vessel.shipType == 60 ? 25.0 : // Passenger ships faster
                             vessel.shipType == 80 ? 15.0 : // Tankers slower
                             18.0; // Container/cargo ships
            state.speed = random.nextDouble() * maxSpeed;
            
            // Random navigational status (most vessels under way)
            state.navigationalStatus = state.speed < 0.5 ? 1 : 0; // At anchor if speed < 0.5 knots
            
            // Store initial position in history
            state.addPositionToHistory();
            
            vesselStates.put(vessel.mmsi, state);
        }
        
        log.info("Successfully initialized {} vessels", vesselStates.size());
    }
    
    /**
     * Select which vessels to update in this batch.
     * Prioritizes vessels that haven't been updated recently.
     */
    private List<Long> selectVesselsForUpdate(int count) {
        long currentTime = System.currentTimeMillis();
        
        // Get all vessel MMSIs sorted by last used time (oldest first)
        List<Long> allMMSIs = new ArrayList<>(vesselStates.keySet());
        allMMSIs.sort((mmsi1, mmsi2) -> {
            long time1 = lastUsedTimestamp.getOrDefault(mmsi1, 0L);
            long time2 = lastUsedTimestamp.getOrDefault(mmsi2, 0L);
            return Long.compare(time1, time2);
        });
        
        // Take the oldest 'count' vessels, or all if fewer than count
        int actualCount = Math.min(count, allMMSIs.size());
        return allMMSIs.subList(0, actualCount);
    }
    
    /**
     * Update vessel position based on heading and speed.
     * Position update calculation:
     * - Speed is in knots (nautical miles per hour)
     * - 1 nautical mile = 1.852 km = 1/60 degree of latitude (approximately)
     * - Time interval is UPDATE_INTERVAL_MS (30 seconds = 0.00833 hours)
     */
    private void updateVesselPosition(VesselState state) {
        // Convert speed from knots to degrees per update interval
        // 1 knot = 1 nautical mile/hour = 1/60 degree latitude/hour
        double hoursElapsed = UPDATE_INTERVAL_MS / 3600000.0; // Convert ms to hours
        double distanceInDegrees = (state.speed / 60.0) * hoursElapsed;
        
        // Calculate latitude and longitude changes
        double headingRadians = Math.toRadians(state.heading);
        double latChange = distanceInDegrees * Math.cos(headingRadians);
        double lonChange = distanceInDegrees * Math.sin(headingRadians) / Math.cos(Math.toRadians(state.currentLat));
        
        // Update position
        state.currentLat += latChange;
        state.currentLon += lonChange;
        
        // Add small random variation to heading (±5 degrees) to simulate realistic navigation
        state.heading += (random.nextDouble() - 0.5) * 10.0;
        if (state.heading < 0) state.heading += 360;
        if (state.heading >= 360) state.heading -= 360;
        
        // Add small random variation to speed (±10%)
        double speedVariation = (random.nextDouble() - 0.5) * 0.2;
        state.speed *= (1 + speedVariation);
        
        // Ensure speed stays within reasonable bounds
        double maxSpeed = state.template.shipType == 60 ? 25.0 :
                         state.template.shipType == 80 ? 15.0 : 18.0;
        state.speed = Math.max(0, Math.min(state.speed, maxSpeed));
        
        // Update navigational status based on speed
        state.navigationalStatus = state.speed < 0.5 ? 1 : 0;
        
        // Check if vessel has drifted too far from base area - if so, reset towards base
        double distanceFromBase = calculateDistance(state.currentLat, state.currentLon, 
                                                   state.template.baseLat, state.template.baseLon);
        
        // If more than 2 degrees (~220km) from base, adjust heading towards base
        if (distanceFromBase > 2.0) {
            double bearingToBase = calculateBearing(state.currentLat, state.currentLon,
                                                   state.template.baseLat, state.template.baseLon);
            state.heading = bearingToBase + (random.nextDouble() - 0.5) * 30.0; // Add some randomness
            log.debug("Vessel {} drifted {}° from base, adjusting heading to {}", 
                     state.template.shipName, distanceFromBase, state.heading);
        }
        
        // Store position in history
        state.addPositionToHistory();
    }
    
    /**
     * Calculate distance between two points in degrees
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }
    
    /**
     * Calculate bearing from point 1 to point 2 (in degrees)
     */
    private double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = lon2 - lon1;
        double dLat = lat2 - lat1;
        double bearing = Math.toDegrees(Math.atan2(dLon, dLat));
        return (bearing + 360) % 360; // Normalize to 0-360
    }
    
    /**
     * Create AIS message from current vessel state
     */
    private AISStreamMessage createMessageFromState(VesselState state) {
        AISStreamMessage message = new AISStreamMessage();
        message.setMessageType("PositionReport");
        
        // Create metadata
        AISStreamMessage.MetaData metaData = new AISStreamMessage.MetaData();
        metaData.setMmsi(state.template.mmsi);
        metaData.setShipName(state.template.shipName);
        metaData.setImoNumber(state.template.imoNumber);
        metaData.setCallSign(state.template.callSign);
        metaData.setShipType(state.template.shipType);
        message.setMetaData(metaData);
        
        // Create position report with current state
        AISStreamMessage.Message msg = new AISStreamMessage.Message();
        AISStreamMessage.PositionReport posReport = new AISStreamMessage.PositionReport();
        
        posReport.setLatitude(state.currentLat);
        posReport.setLongitude(state.currentLon);
        posReport.setSog(state.speed);
        posReport.setCog(state.heading);
        posReport.setTrueHeading((int) Math.round(state.heading));
        posReport.setNavigationalStatus(state.navigationalStatus);
        posReport.setTimestamp(Instant.now().toString());
        
        msg.setPositionReport(posReport);
        message.setMessage(msg);
        
        return message;
    }

    /**
     * Generate a single mock AIS message for a random Indonesian vessel
     */
    public String generateSingleMessage() {
        if (!vesselsInitialized) {
            initializeAllVessels();
            vesselsInitialized = true;
        }
        
        // Select random vessel
        VesselTemplate vessel = INDONESIAN_VESSELS.get(random.nextInt(INDONESIAN_VESSELS.size()));
        VesselState state = vesselStates.get(vessel.mmsi);
        
        if (state == null) return null;
        
        // Update position
        updateVesselPosition(state);
        
        // Create message
        AISStreamMessage message = createMessageFromState(state);
        
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("Failed to serialize mock AIS message", e);
            return null;
        }
    }
    
    /**
     * Get position history for a specific vessel
     */
    public List<PositionHistory> getVesselHistory(Long mmsi) {
        VesselState state = vesselStates.get(mmsi);
        return state != null ? new ArrayList<>(state.positionHistory) : Collections.emptyList();
    }
    
    /**
     * Get current state of all vessels (for analytics/debugging)
     */
    public Map<Long, VesselState> getAllVesselStates() {
        return new HashMap<>(vesselStates);
    }

    /**
     * Create a realistic AIS message with vessel movement simulation.
     * Movement is constrained to stay in maritime areas.
     * 
     * @deprecated Use createMessageFromState() instead. This method is kept for backward compatibility.
     */
    @Deprecated
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
    
    /**
     * Current state of a vessel including position, heading, speed, and history
     */
    public static class VesselState {
        final VesselTemplate template;
        double currentLat;
        double currentLon;
        double heading;  // 0-360 degrees
        double speed;    // knots
        int navigationalStatus;
        final List<PositionHistory> positionHistory = new ArrayList<>();
        
        // Keep only last 100 positions to prevent memory issues
        private static final int MAX_HISTORY_SIZE = 100;
        
        VesselState(VesselTemplate template) {
            this.template = template;
        }
        
        void addPositionToHistory() {
            positionHistory.add(new PositionHistory(
                currentLat, 
                currentLon, 
                heading, 
                speed, 
                System.currentTimeMillis()
            ));
            
            // Trim history if too large
            while (positionHistory.size() > MAX_HISTORY_SIZE) {
                positionHistory.remove(0);
            }
        }
        
        public VesselTemplate getTemplate() {
            return template;
        }
        
        public double getCurrentLat() {
            return currentLat;
        }
        
        public double getCurrentLon() {
            return currentLon;
        }
        
        public double getHeading() {
            return heading;
        }
        
        public double getSpeed() {
            return speed;
        }
        
        public List<PositionHistory> getPositionHistory() {
            return new ArrayList<>(positionHistory);
        }
    }
    
    /**
     * Historical position data for vessel journey tracking
     */
    public static class PositionHistory {
        final double latitude;
        final double longitude;
        final double heading;
        final double speed;
        final long timestamp;
        
        PositionHistory(double latitude, double longitude, double heading, double speed, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.heading = heading;
            this.speed = speed;
            this.timestamp = timestamp;
        }
        
        public double getLatitude() {
            return latitude;
        }
        
        public double getLongitude() {
            return longitude;
        }
        
        public double getHeading() {
            return heading;
        }
        
        public double getSpeed() {
            return speed;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}
