package com.kreasipositif.vms.collector.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OpenWeatherMap API response model.
 * Represents the JSON structure returned by OpenWeatherMap API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenWeatherMapResponse {

    @JsonProperty("coord")
    private Coord coord;

    @JsonProperty("weather")
    private Weather[] weather;

    @JsonProperty("base")
    private String base;

    @JsonProperty("main")
    private Main main;

    @JsonProperty("visibility")
    private Integer visibility;

    @JsonProperty("wind")
    private Wind wind;

    @JsonProperty("rain")
    private Rain rain;

    @JsonProperty("clouds")
    private Clouds clouds;

    @JsonProperty("dt")
    private Long dt;

    @JsonProperty("sys")
    private Sys sys;

    @JsonProperty("timezone")
    private Integer timezone;

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("cod")
    private Integer cod;

    /**
     * Convert to internal WeatherData model
     */
    public WeatherData toWeatherData() {
        return WeatherData.builder()
                .latitude(coord != null ? coord.getLat() : null)
                .longitude(coord != null ? coord.getLon() : null)
                .locationName(name)
                .temperature(main != null ? main.getTemp() : null)
                .feelsLike(main != null ? main.getFeelsLike() : null)
                .pressure(main != null ? main.getPressure() : null)
                .humidity(main != null ? main.getHumidity() : null)
                .visibility(visibility)
                .windSpeed(wind != null ? wind.getSpeed() : null)
                .windDirection(wind != null ? wind.getDeg() : null)
                .windGust(wind != null ? wind.getGust() : null)
                .condition(weather != null && weather.length > 0 ? weather[0].getMain() : null)
                .conditionCode(weather != null && weather.length > 0 ? weather[0].getId() : null)
                .cloudiness(clouds != null ? clouds.getAll() : null)
                .precipitation(rain != null ? rain.getOneH() : null)
                .timestamp(dt != null ? Instant.ofEpochSecond(dt) : Instant.now())
                .source("OpenWeatherMap")
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Coord {
        @JsonProperty("lon")
        private Double lon;

        @JsonProperty("lat")
        private Double lat;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Weather {
        @JsonProperty("id")
        private Integer id;

        @JsonProperty("main")
        private String main;

        @JsonProperty("description")
        private String description;

        @JsonProperty("icon")
        private String icon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Main {
        @JsonProperty("temp")
        private Double temp;

        @JsonProperty("feels_like")
        private Double feelsLike;

        @JsonProperty("temp_min")
        private Double tempMin;

        @JsonProperty("temp_max")
        private Double tempMax;

        @JsonProperty("pressure")
        private Integer pressure;

        @JsonProperty("humidity")
        private Integer humidity;

        @JsonProperty("sea_level")
        private Integer seaLevel;

        @JsonProperty("grnd_level")
        private Integer grndLevel;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Wind {
        @JsonProperty("speed")
        private Double speed;

        @JsonProperty("deg")
        private Integer deg;

        @JsonProperty("gust")
        private Double gust;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rain {
        @JsonProperty("1h")
        private Double oneH;

        @JsonProperty("3h")
        private Double threeH;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Clouds {
        @JsonProperty("all")
        private Integer all;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Sys {
        @JsonProperty("type")
        private Integer type;

        @JsonProperty("id")
        private Long id;

        @JsonProperty("country")
        private String country;

        @JsonProperty("sunrise")
        private Long sunrise;

        @JsonProperty("sunset")
        private Long sunset;
    }
}
