package com.kreasipositif.vms.collector.mock;

import com.kreasipositif.vms.collector.aisstream.AISStreamMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock AIS Data Generator for Indonesian Maritime Demo.
 * Generates realistic vessel data for major Indonesian shipping routes and ports.
 */
@Slf4j
@Component
public class MockAISDataGenerator {

    private final ObjectMapper objectMapper;
    private final Random random = new Random();
    
    // Indonesian vessel fleet for demo
    private static final List<VesselTemplate> INDONESIAN_VESSELS = List.of(
            // Container ships around Jakarta
            new VesselTemplate(525000001L, "MERATUS MANADO", 220150001L, "YBAM", 70, 
                    -6.11, 106.89, "Jakarta Area"),
            new VesselTemplate(525000002L, "TANTO INTIM", 220150002L, "YBAK", 70,
                    -6.11, 106.89, "Jakarta Area"),
            new VesselTemplate(525000003L, "SAMUDERA INDONESIA", 220150003L, "YBAS", 70,
                    -6.11, 106.89, "Jakarta Area"),
            
            // Container ships around Surabaya
            new VesselTemplate(525000010L, "TEMAS LINE", 220150010L, "YBAU", 70,
                    -7.21, 112.74, "Surabaya Area"),
            new VesselTemplate(525000011L, "SPIL GEMILANG", 220150011L, "YBAV", 70,
                    -7.21, 112.74, "Surabaya Area"),
            
            // Tankers around Balikpapan
            new VesselTemplate(525000020L, "PERTAMINA GAS 1", 220150020L, "YBBP", 80,
                    -1.25, 116.83, "Balikpapan Area"),
            new VesselTemplate(525000021L, "PERTAMINA GAS 2", 220150021L, "YBBQ", 80,
                    -1.25, 116.83, "Balikpapan Area"),
            
            // Cargo ships around Makassar
            new VesselTemplate(525000030L, "PELNI TILONGKABILA", 220150030L, "YBMK", 79,
                    -5.13, 119.43, "Makassar Area"),
            new VesselTemplate(525000031L, "PELNI LEUSER", 220150031L, "YBML", 79,
                    -5.13, 119.43, "Makassar Area"),
            
            // Container ships around Batam/Singapore Strait
            new VesselTemplate(525000040L, "BATAM EXPRESS", 220150040L, "YBBT", 70,
                    1.15, 104.03, "Batam/Singapore Strait"),
            new VesselTemplate(525000041L, "RIAU CONTAINER", 220150041L, "YBBU", 70,
                    1.15, 104.03, "Batam/Singapore Strait"),
            new VesselTemplate(525000042L, "KEPRI SHIPPING", 220150042L, "YBBV", 70,
                    1.15, 104.03, "Batam/Singapore Strait"),
            
            // Passenger ships around Bali
            new VesselTemplate(525000050L, "GILICAT FAST FERRY", 220150050L, "YBBN", 60,
                    -8.75, 115.17, "Benoa/Bali Area"),
            new VesselTemplate(525000051L, "BALI HAI CRUISE", 220150051L, "YBBO", 60,
                    -8.75, 115.17, "Benoa/Bali Area"),
            
            // Cargo ships around Belawan/Medan
            new VesselTemplate(525000060L, "SUMATRA CARGO", 220150060L, "YBBW", 79,
                    3.59, 98.67, "Belawan/Medan Area"),
            new VesselTemplate(525000061L, "MEDAN LOGISTICS", 220150061L, "YBBX", 79,
                    3.59, 98.67, "Belawan/Medan Area")
    );

    public MockAISDataGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        log.info("Mock AIS Data Generator initialized with {} Indonesian vessels", INDONESIAN_VESSELS.size());
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
     * Create a realistic AIS message with vessel movement simulation
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
        
        // Add realistic random movement around base position (±0.1 degrees ~ ±11km)
        double latOffset = (random.nextDouble() - 0.5) * 0.2;
        double lonOffset = (random.nextDouble() - 0.5) * 0.2;
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
