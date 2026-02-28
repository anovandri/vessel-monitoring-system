package com.kreasipositif.vms.collector.impl;

import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorConfig;
import com.kreasipositif.vms.collector.parser.AISParser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AISCollector Integration Tests")
class AISCollectorIntegrationTest {

    private MockWebServer mockWebServer;
    private AISCollector collector;
    private AISParser parser;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        CollectorConfig config = new CollectorConfig();
        CollectorConfig.AisCollectorConfig aisConfig = new CollectorConfig.AisCollectorConfig();
        aisConfig.setEnabled(false); // Disable to prevent WebSocket connection in tests
        aisConfig.setWebsocketUrl(mockWebServer.url("/").toString());
        aisConfig.setApiKey("test-key");
        aisConfig.setPollInterval(Duration.ofMillis(10000L));
        aisConfig.setBatchSize(100);
        config.setAis(aisConfig);

        parser = new AISParser();
        
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        
        collector = new AISCollector(meterRegistry, config, parser);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should collect AIS data successfully")
    void shouldCollectAISDataSuccessfully() {
        // Given
        String responseJson = """
                [
                    {
                        "mmsi": 123456789,
                        "imo": "IMO9876543",
                        "vessel_name": "TEST VESSEL 1",
                        "latitude": 1.2345,
                        "longitude": 103.8765,
                        "speed": 12.5,
                        "course": 180.0,
                        "heading": 185,
                        "navigation_status": 0,
                        "vessel_type": 70,
                        "destination": "SINGAPORE",
                        "source": "SATELLITE",
                        "timestamp": "2024-01-01T12:00:00Z"
                    },
                    {
                        "mmsi": 987654321,
                        "vessel_name": "TEST VESSEL 2",
                        "latitude": 2.3456,
                        "longitude": 104.9876,
                        "speed": 8.0,
                        "course": 90.0,
                        "source": "TERRESTRIAL",
                        "timestamp": "2024-01-01T12:00:00Z"
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(responseJson)
                .addHeader("Content-Type", "application/json"));

        // When
        List<CollectedData> results = collector.collect();

        // Then
        assertThat(results).isNotNull()
                .hasSize(2);
        
        assertThat(results.get(0).getCollectorId()).isEqualTo("ais-collector");
        assertThat(results.get(0).getRawData()).contains("TEST VESSEL 1");
        assertThat(results.get(0).getParsedData()).isNotNull();
        assertThat(results.get(0).getQualityScore()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should handle empty response")
    void shouldHandleEmptyResponse() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));

        // When
        List<CollectedData> results = collector.collect();

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle API errors")
    void shouldHandleAPIErrors() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // When/Then - API errors should be handled gracefully by returning empty list
        List<CollectedData> results = collector.collect();
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should calculate quality score correctly")
    void shouldCalculateQualityScore() {
        // Given - complete data
        String completeData = """
                [
                    {
                        "mmsi": 123456789,
                        "imo": "IMO9876543",
                        "vessel_name": "COMPLETE VESSEL",
                        "latitude": 1.2345,
                        "longitude": 103.8765,
                        "speed": 12.5,
                        "course": 180.0,
                        "heading": 185,
                        "navigation_status": 0,
                        "vessel_type": 70,
                        "length": 150.0,
                        "width": 25.0,
                        "draught": 8.5,
                        "destination": "SINGAPORE",
                        "eta": "2024-01-01T12:00:00Z",
                        "flag_country": "SG",
                        "source": "SATELLITE",
                        "timestamp": "2024-01-01T12:00:00Z"
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(completeData)
                .addHeader("Content-Type", "application/json"));

        // When
        List<CollectedData> results = collector.collect();

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getQualityScore())
                .isGreaterThanOrEqualTo(80); // High quality for complete data
    }

    @Test
    @DisplayName("Should verify collector metadata")
    void shouldVerifyCollectorMetadata() {
        // When
        var metadata = collector.getMetadata();

        // Then
        assertThat(metadata.getId()).isEqualTo("ais-collector");
        assertThat(metadata.getName()).isEqualTo("AIS Stream Collector");
        assertThat(metadata.getDataType()).isEqualTo(
                com.kreasipositif.vms.collector.core.CollectorMetadata.CollectorDataType.AIS_RAW
        );
        assertThat(metadata.isEnabled()).isTrue();
        assertThat(metadata.getKafkaTopic()).isEqualTo("ais-raw-data");
    }
}
