package com.kreasipositif.vms.collector.parser;

import com.kreasipositif.vms.collector.model.AISMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AISParser Tests")
class AISParserTest {

    private AISParser parser;

    @BeforeEach
    void setUp() {
        parser = new AISParser();
    }

    @Test
    @DisplayName("Should parse valid AIS JSON successfully")
    void shouldParseValidAISJson() throws DataParser.ParsingException {
        // Given
        String validJson = """
                {
                    "mmsi": 123456789,
                    "imo": "IMO9876543",
                    "vessel_name": "TEST VESSEL",
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
                    "flag_country": "SG",
                    "source": "SATELLITE",
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        AISMessage result = parser.parse(validJson);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMmsi()).isEqualTo(123456789);
        assertThat(result.getImo()).isEqualTo("IMO9876543");
        assertThat(result.getVesselName()).isEqualTo("TEST VESSEL");
        assertThat(result.getLatitude()).isEqualTo(1.2345);
        assertThat(result.getLongitude()).isEqualTo(103.8765);
        assertThat(result.getSpeed()).isEqualTo(12.5);
        assertThat(result.getCourse()).isEqualTo(180.0);
        assertThat(result.getHeading()).isEqualTo(185);
        assertThat(result.getNavigationStatus()).isEqualTo(0);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should parse minimal AIS JSON")
    void shouldParseMinimalAISJson() throws DataParser.ParsingException {
        // Given
        String minimalJson = """
                {
                    "mmsi": 123456789,
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        AISMessage result = parser.parse(minimalJson);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getMmsi()).isEqualTo(123456789);
        assertThat(result.getLatitude()).isEqualTo(1.2345);
        assertThat(result.getLongitude()).isEqualTo(103.8765);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON")
    void shouldThrowExceptionForInvalidJson() {
        // Given
        String invalidJson = "not a json";

        // When/Then
        assertThatThrownBy(() -> parser.parse(invalidJson))
                .isInstanceOf(DataParser.ParsingException.class)
                .hasMessageContaining("Failed to parse AIS data");
    }

    @Test
    @DisplayName("Should detect parseable AIS data")
    void shouldDetectParseableAISData() {
        // Given
        String aisJson = """
                {
                    "mmsi": 123456789,
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When/Then
        assertThat(parser.canParse(aisJson)).isTrue();
    }

    @Test
    @DisplayName("Should reject non-AIS data")
    void shouldRejectNonAISData() {
        // Given
        String nonAISJson = """
                {
                    "temperature": 25.5,
                    "humidity": 80
                }
                """;

        // When/Then
        assertThat(parser.canParse(nonAISJson)).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid JSON format")
    void shouldRejectInvalidJsonFormat() {
        // Given
        String invalidJson = "not a json";

        // When/Then
        assertThat(parser.canParse(invalidJson)).isFalse();
    }

    @Test
    @DisplayName("Should return correct data type")
    void shouldReturnCorrectDataType() {
        assertThat(parser.getDataType()).isEqualTo("AIS");
    }

    @Test
    @DisplayName("Should return version")
    void shouldReturnVersion() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("Should handle navigation status descriptions")
    void shouldHandleNavigationStatus() throws DataParser.ParsingException {
        // Given
        String jsonWithStatus = """
                {
                    "mmsi": 123456789,
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "navigation_status": 5,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        AISMessage result = parser.parse(jsonWithStatus);

        // Then
        assertThat(result.getNavigationStatusDescription()).isEqualTo("Moored");
    }
}
