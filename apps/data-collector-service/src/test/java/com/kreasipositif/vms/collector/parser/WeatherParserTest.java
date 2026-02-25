package com.kreasipositif.vms.collector.parser;

import com.kreasipositif.vms.collector.model.WeatherData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

@DisplayName("WeatherParser Tests")
class WeatherParserTest {

    private WeatherParser parser;

    @BeforeEach
    void setUp() {
        parser = new WeatherParser();
    }

    @Test
    @DisplayName("Should parse valid weather JSON successfully")
    void shouldParseValidWeatherJson() throws DataParser.ParsingException {
        // Given
        String validJson = """
                {
                    "location_name": "Singapore Strait",
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "temperature": 28.5,
                    "feels_like": 32.0,
                    "pressure": 1013,
                    "humidity": 75,
                    "visibility": 10000,
                    "wind_speed": 5.5,
                    "wind_direction": 180,
                    "wind_gust": 8.0,
                    "cloudiness": 50,
                    "condition": "Partly Cloudy",
                    "precipitation": 0.0,
                    "wave_height": 1.5,
                    "wave_period": 8.0,
                    "sea_state": 3,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        WeatherData result = parser.parse(validJson);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLocationName()).isEqualTo("Singapore Strait");
        assertThat(result.getTemperature()).isEqualTo(28.5);
        assertThat(result.getWindSpeed()).isEqualTo(5.5);
        assertThat(result.getWaveHeight()).isEqualTo(1.5);
        assertThat(result.getSeaState()).isEqualTo(3);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should parse minimal weather JSON")
    void shouldParseMinimalWeatherJson() throws DataParser.ParsingException {
        // Given
        String minimalJson = """
                {
                    "location_name": "Test Location",
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "temperature": 25.0,
                    "wind_speed": 5.0,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        WeatherData result = parser.parse(minimalJson);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getLocationName()).isEqualTo("Test Location");
        assertThat(result.getTemperature()).isEqualTo(25.0);
        assertThat(result.getWindSpeed()).isEqualTo(5.0);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Should calculate sea state description")
    void shouldCalculateSeaStateDescription() throws DataParser.ParsingException {
        // Given
        String json = """
                {
                    "location_name": "Test",
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "temperature": 25.0,
                    "wind_speed": 5.0,
                    "sea_state": 5,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        WeatherData result = parser.parse(json);

        // Then
        assertThat(result.getSeaStateDescription()).isEqualTo("Rough - 2.5-4m");
    }

    @Test
    @DisplayName("Should determine if weather is safe for navigation")
    void shouldDetermineNavigationSafety() throws DataParser.ParsingException {
        // Given - calm weather
        String safeWeather = """
                {
                    "location_name": "Test",
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "temperature": 25.0,
                    "wind_speed": 10.0,
                    "wave_height": 2.0,
                    "visibility": 5000,
                    "sea_state": 3,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        WeatherData result = parser.parse(safeWeather);

        // Then
        assertThat(result.isSafeForNavigation()).isTrue();
    }

    @Test
    @DisplayName("Should identify unsafe weather conditions")
    void shouldIdentifyUnsafeWeather() throws DataParser.ParsingException {
        // Given - high waves
        String unsafeWeather = """
                {
                    "location_name": "Test",
                    "latitude": 1.2345,
                    "longitude": 103.8765,
                    "temperature": 25.0,
                    "wind_speed": 10.0,
                    "wave_height": 5.5,
                    "visibility": 5000,
                    "sea_state": 7,
                    "timestamp": "2024-01-01T12:00:00Z"
                }
                """;

        // When
        WeatherData result = parser.parse(unsafeWeather);

        // Then
        assertThat(result.isSafeForNavigation()).isFalse();
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON")
    void shouldThrowExceptionForInvalidJson() {
        // Given
        String invalidJson = "not a json";

        // When/Then
        assertThatThrownBy(() -> parser.parse(invalidJson))
                .isInstanceOf(DataParser.ParsingException.class)
                .hasMessageContaining("Failed to parse weather data");
    }

    @Test
    @DisplayName("Should detect parseable weather data")
    void shouldDetectParseableWeatherData() {
        // Given
        String weatherJson = """
                {
                    "temperature": 25.5,
                    "wind_speed": 5.0
                }
                """;

        // When/Then
        assertThat(parser.canParse(weatherJson)).isTrue();
    }

    @Test
    @DisplayName("Should reject non-weather data")
    void shouldRejectNonWeatherData() {
        // Given
        String nonWeatherJson = """
                {
                    "mmsi": 123456789,
                    "latitude": 1.2345
                }
                """;

        // When/Then
        assertThat(parser.canParse(nonWeatherJson)).isFalse();
    }

    @Test
    @DisplayName("Should return correct data type")
    void shouldReturnCorrectDataType() {
        assertThat(parser.getDataType()).isEqualTo("WEATHER");
    }

    @Test
    @DisplayName("Should return version")
    void shouldReturnVersion() {
        assertThat(parser.getVersion()).isEqualTo("1.0.0");
    }
}
