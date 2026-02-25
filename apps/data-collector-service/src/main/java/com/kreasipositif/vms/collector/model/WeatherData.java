package com.kreasipositif.vms.collector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Weather data model for maritime weather information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherData {

    /**
     * Location latitude
     */
    @JsonProperty("latitude")
    private Double latitude;

    /**
     * Location longitude
     */
    @JsonProperty("longitude")
    private Double longitude;

    /**
     * Location name (e.g., "Singapore Strait", "Port of Rotterdam")
     */
    @JsonProperty("location_name")
    private String locationName;

    /**
     * Temperature in Celsius
     */
    @JsonProperty("temperature")
    private Double temperature;

    /**
     * Feels like temperature in Celsius
     */
    @JsonProperty("feels_like")
    private Double feelsLike;

    /**
     * Atmospheric pressure in hPa
     */
    @JsonProperty("pressure")
    private Integer pressure;

    /**
     * Humidity percentage (0-100)
     */
    @JsonProperty("humidity")
    private Integer humidity;

    /**
     * Visibility in meters
     */
    @JsonProperty("visibility")
    private Integer visibility;

    /**
     * Wind speed in meters/second
     */
    @JsonProperty("wind_speed")
    private Double windSpeed;

    /**
     * Wind direction in degrees
     */
    @JsonProperty("wind_direction")
    private Integer windDirection;

    /**
     * Wind gust speed in meters/second
     */
    @JsonProperty("wind_gust")
    private Double windGust;

    /**
     * Wave height in meters
     */
    @JsonProperty("wave_height")
    private Double waveHeight;

    /**
     * Wave period in seconds
     */
    @JsonProperty("wave_period")
    private Double wavePeriod;

    /**
     * Weather condition (e.g., "Clear", "Rain", "Fog")
     */
    @JsonProperty("condition")
    private String condition;

    /**
     * Weather condition code
     */
    @JsonProperty("condition_code")
    private Integer conditionCode;

    /**
     * Cloudiness percentage (0-100)
     */
    @JsonProperty("cloudiness")
    private Integer cloudiness;

    /**
     * Precipitation in mm
     */
    @JsonProperty("precipitation")
    private Double precipitation;

    /**
     * Sea state code (0-9)
     * 0: Calm (glassy)
     * 1: Calm (rippled)
     * 2: Smooth (wavelets)
     * 3: Slight
     * 4: Moderate
     * 5: Rough
     * 6: Very rough
     * 7: High
     * 8: Very high
     * 9: Phenomenal
     */
    @JsonProperty("sea_state")
    private Integer seaState;

    /**
     * Sea surface temperature in Celsius
     */
    @JsonProperty("sea_temperature")
    private Double seaTemperature;

    /**
     * Current speed in knots
     */
    @JsonProperty("current_speed")
    private Double currentSpeed;

    /**
     * Current direction in degrees
     */
    @JsonProperty("current_direction")
    private Integer currentDirection;

    /**
     * Timestamp of weather observation
     */
    @JsonProperty("timestamp")
    private Instant timestamp;

    /**
     * Data source (e.g., "OpenWeatherMap", "NOAA")
     */
    @JsonProperty("source")
    private String source;

    /**
     * Get sea state description
     */
    public String getSeaStateDescription() {
        if (seaState == null) return "Unknown";
        
        return switch (seaState) {
            case 0 -> "Calm (glassy) - 0m";
            case 1 -> "Calm (rippled) - 0-0.1m";
            case 2 -> "Smooth (wavelets) - 0.1-0.5m";
            case 3 -> "Slight - 0.5-1.25m";
            case 4 -> "Moderate - 1.25-2.5m";
            case 5 -> "Rough - 2.5-4m";
            case 6 -> "Very rough - 4-6m";
            case 7 -> "High - 6-9m";
            case 8 -> "Very high - 9-14m";
            case 9 -> "Phenomenal - 14m+";
            default -> "Invalid";
        };
    }

    /**
     * Check if weather conditions are safe for navigation
     */
    public boolean isSafeForNavigation() {
        return visibility != null && visibility > 1000
                && seaState != null && seaState <= 6
                && windSpeed != null && windSpeed < 20;
    }

    /**
     * Validate weather data
     */
    public boolean isValid() {
        return latitude != null 
                && longitude != null
                && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180
                && timestamp != null;
    }
}
