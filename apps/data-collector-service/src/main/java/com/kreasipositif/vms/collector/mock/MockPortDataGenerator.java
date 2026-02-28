package com.kreasipositif.vms.collector.mock;

import com.kreasipositif.vms.collector.model.PortData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Mock Port Data Generator for Indonesian maritime demo.
 * Generates realistic port information and vessel schedules for major Indonesian ports.
 */
@Slf4j
@Component
public class MockPortDataGenerator {

    private final Random random = new Random();
    private final List<PortTemplate> ports;

    public MockPortDataGenerator() {
        this.ports = initializeIndonesianPorts();
        log.info("Mock Port Data Generator initialized with {} Indonesian ports", ports.size());
    }

    /**
     * Indonesian port templates with real port information
     */
    private record PortTemplate(
            String portCode,
            String portName,
            String countryCode,
            double latitude,
            double longitude,
            String timezone,
            int berthCount,
            int maxCongestion,
            List<String> facilities
    ) {}

    /**
     * Initialize Indonesian port data
     */
    private List<PortTemplate> initializeIndonesianPorts() {
        return List.of(
                // Tanjung Priok - Jakarta's main port (largest in Indonesia)
                new PortTemplate(
                        "IDTPP",
                        "Tanjung Priok Port",
                        "ID",
                        -6.1055,
                        106.8833,
                        "Asia/Jakarta",
                        32,
                        85,
                        List.of("Container Terminal", "Bulk Cargo", "Ro-Ro", "Passenger Terminal", 
                               "Ship Repair", "Bunker Services", "Cold Storage")
                ),
                
                // Tanjung Perak - Surabaya (second largest)
                new PortTemplate(
                        "IDSUB",
                        "Tanjung Perak Port",
                        "ID",
                        -7.2105,
                        112.7397,
                        "Asia/Jakarta",
                        28,
                        75,
                        List.of("Container Terminal", "Multipurpose Terminal", "Bulk Cargo", 
                               "Ship Repair", "Bunker Services", "Passenger Terminal")
                ),
                
                // Makassar Port (Soekarno-Hatta)
                new PortTemplate(
                        "IDMKS",
                        "Makassar New Port",
                        "ID",
                        -5.1308,
                        119.4289,
                        "Asia/Makassar",
                        18,
                        60,
                        List.of("Container Terminal", "Multipurpose Terminal", "Passenger Terminal",
                               "Bunker Services", "Ship Repair")
                ),
                
                // Balikpapan Port
                new PortTemplate(
                        "IDBPN",
                        "Semayang Port",
                        "ID",
                        -1.2625,
                        116.8289,
                        "Asia/Makassar",
                        15,
                        55,
                        List.of("Bulk Cargo", "Liquid Bulk Terminal", "Container Terminal",
                               "Bunker Services", "Coal Loading")
                ),
                
                // Batam Port (Batu Ampar)
                new PortTemplate(
                        "IDBTH",
                        "Batu Ampar Port",
                        "ID",
                        1.1405,
                        104.0297,
                        "Asia/Jakarta",
                        22,
                        70,
                        List.of("Container Terminal", "Ro-Ro", "Passenger Ferry", "Ship Repair",
                               "Bunker Services", "Offshore Support")
                ),
                
                // Benoa Port - Bali
                new PortTemplate(
                        "IDDPS",
                        "Benoa Port",
                        "ID",
                        -8.7467,
                        115.2139,
                        "Asia/Makassar",
                        12,
                        50,
                        List.of("Passenger Terminal", "Cruise Terminal", "Container Terminal",
                               "Ro-Ro", "Bunker Services")
                ),
                
                // Belawan Port - Medan
                new PortTemplate(
                        "IDBLW",
                        "Belawan International Port",
                        "ID",
                        3.7833,
                        98.6833,
                        "Asia/Jakarta",
                        20,
                        65,
                        List.of("Container Terminal", "Bulk Cargo", "Palm Oil Terminal",
                               "Ship Repair", "Bunker Services", "Cold Storage")
                )
        );
    }

    /**
     * Generate mock port data for a specific port
     */
    public PortData generatePortData(String portCode) {
        PortTemplate template = ports.stream()
                .filter(p -> p.portCode.equals(portCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown port code: " + portCode));

        return createRealisticPortData(template);
    }

    /**
     * Generate mock port data for all ports
     */
    public List<PortData> generateAllPortsData() {
        List<PortData> result = new ArrayList<>();
        for (PortTemplate template : ports) {
            result.add(createRealisticPortData(template));
        }
        log.info("Generated mock data for {} Indonesian ports", result.size());
        return result;
    }

    /**
     * Create realistic port data with dynamic values
     */
    private PortData createRealisticPortData(PortTemplate template) {
        Instant now = Instant.now();
        
        // Generate realistic operational metrics
        int vesselsInPort = random.nextInt(15) + 5; // 5-20 vessels
        int expectedArrivals = random.nextInt(10) + 3; // 3-13 arrivals
        int expectedDepartures = random.nextInt(10) + 2; // 2-12 departures
        int congestionLevel = random.nextInt(template.maxCongestion);
        
        // Calculate wait time based on congestion
        double avgWaitTime = congestionLevel > 70 ? 
                (random.nextDouble() * 12 + 6) :  // 6-18 hours if congested
                (random.nextDouble() * 4 + 1);     // 1-5 hours if normal
        
        return PortData.builder()
                .portCode(template.portCode)
                .portName(template.portName)
                .countryCode(template.countryCode)
                .latitude(template.latitude)
                .longitude(template.longitude)
                .timezone(template.timezone)
                .status("OPERATIONAL")
                .berthCount(template.berthCount)
                .vesselsInPort(vesselsInPort)
                .expectedArrivals(expectedArrivals)
                .expectedDepartures(expectedDepartures)
                .congestionLevel(congestionLevel)
                .avgWaitTime(avgWaitTime)
                .maxVesselLength(350.0) // Standard for most Indonesian ports
                .maxDraught(14.5) // meters
                .facilities(template.facilities)
                .schedules(generateVesselSchedules(expectedArrivals, expectedDepartures))
                .tideInfo(generateTideInfo(now))
                .contactEmail("operations@" + template.portCode.toLowerCase() + ".port.id")
                .contactPhone("+62-21-" + (random.nextInt(9000000) + 1000000))
                .website("https://www." + template.portCode.toLowerCase() + ".port.id")
                .timestamp(now)
                .source("mock_generator")
                .build();
    }

    /**
     * Generate realistic vessel schedules (arrivals and departures)
     */
    private List<PortData.VesselSchedule> generateVesselSchedules(int arrivals, int departures) {
        List<PortData.VesselSchedule> schedules = new ArrayList<>();
        Instant now = Instant.now();
        
        // Generate arrival schedules
        for (int i = 0; i < arrivals; i++) {
            long hoursAhead = random.nextInt(48) + 1; // Next 48 hours
            Instant scheduledTime = now.plus(hoursAhead, ChronoUnit.HOURS);
            Instant estimatedTime = scheduledTime.plus(random.nextInt(120) - 60, ChronoUnit.MINUTES);
            
            schedules.add(PortData.VesselSchedule.builder()
                    .mmsi(525000000 + random.nextInt(100))
                    .vesselName(generateVesselName())
                    .imo("IMO" + (9000000 + random.nextInt(999999)))
                    .eventType("ARRIVAL")
                    .scheduledTime(scheduledTime)
                    .estimatedTime(estimatedTime)
                    .actualTime(null)
                    .berthNumber("B" + (random.nextInt(20) + 1))
                    .cargoType(getRandomCargoType())
                    .status(random.nextDouble() > 0.9 ? "DELAYED" : "SCHEDULED")
                    .build());
        }
        
        // Generate departure schedules
        for (int i = 0; i < departures; i++) {
            long hoursAhead = random.nextInt(24) + 1; // Next 24 hours
            Instant scheduledTime = now.plus(hoursAhead, ChronoUnit.HOURS);
            
            schedules.add(PortData.VesselSchedule.builder()
                    .mmsi(525000000 + random.nextInt(100))
                    .vesselName(generateVesselName())
                    .imo("IMO" + (9000000 + random.nextInt(999999)))
                    .eventType("DEPARTURE")
                    .scheduledTime(scheduledTime)
                    .estimatedTime(scheduledTime)
                    .actualTime(null)
                    .berthNumber("B" + (random.nextInt(20) + 1))
                    .cargoType(getRandomCargoType())
                    .status("SCHEDULED")
                    .build());
        }
        
        return schedules;
    }

    /**
     * Generate realistic tide information
     */
    private PortData.TideInfo generateTideInfo(Instant now) {
        double currentHeight = 1.5 + (random.nextDouble() * 2.5); // 1.5-4.0 meters
        double highTideHeight = 3.5 + (random.nextDouble() * 1.5); // 3.5-5.0 meters
        double lowTideHeight = 0.5 + (random.nextDouble() * 1.0); // 0.5-1.5 meters
        
        // Next high tide in 3-9 hours
        Instant nextHighTide = now.plus(random.nextInt(6) + 3, ChronoUnit.HOURS);
        // Next low tide in 6-15 hours
        Instant nextLowTide = now.plus(random.nextInt(9) + 6, ChronoUnit.HOURS);
        
        return PortData.TideInfo.builder()
                .currentHeight(Math.round(currentHeight * 100.0) / 100.0)
                .nextHighTide(nextHighTide)
                .nextLowTide(nextLowTide)
                .highTideHeight(Math.round(highTideHeight * 100.0) / 100.0)
                .lowTideHeight(Math.round(lowTideHeight * 100.0) / 100.0)
                .build();
    }

    /**
     * Generate random vessel names (Indonesian and international)
     */
    private String generateVesselName() {
        String[] prefixes = {
            "MV", "MS", "MT", "KM"  // KM = Kapal Motor (Indonesian)
        };
        
        String[] names = {
            "MERATUS", "TANTO", "SAMUDERA", "PELNI", "PERTAMINA",
            "TEMAS", "SPIL", "BATAM EXPRESS", "SINAR", "DHARMA",
            "PACIFIC", "OCEAN", "MARITIME", "EASTERN", "WESTERN"
        };
        
        String[] suffixes = {
            "I", "II", "III", "PRIMA", "JAYA", "INDAH", "BARU"
        };
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String name = names[random.nextInt(names.length)];
        String suffix = random.nextDouble() > 0.5 ? " " + suffixes[random.nextInt(suffixes.length)] : "";
        
        return prefix + " " + name + suffix;
    }

    /**
     * Get random cargo type
     */
    private String getRandomCargoType() {
        String[] cargoTypes = {
            "Container", "Bulk", "Break Bulk", "Liquid Bulk", "Ro-Ro",
            "General Cargo", "Palm Oil", "Coal", "CPO", "Petroleum Products"
        };
        return cargoTypes[random.nextInt(cargoTypes.length)];
    }

    /**
     * Get list of all port codes
     */
    public List<String> getPortCodes() {
        return ports.stream().map(PortTemplate::portCode).toList();
    }

    /**
     * Get port count
     */
    public int getPortCount() {
        return ports.size();
    }
}
