package com.kreasipositif.vms.collector;

import com.kreasipositif.vms.collector.core.CollectorRegistry;
import com.kreasipositif.vms.collector.parser.ParserRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Spring Boot Application Context Tests")
class DataCollectorApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CollectorRegistry collectorRegistry;

    @Autowired
    private ParserRegistry parserRegistry;

    @Test
    @DisplayName("Should load application context successfully")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("Should have collector registry bean")
    void shouldHaveCollectorRegistryBean() {
        assertThat(collectorRegistry).isNotNull();
    }

    @Test
    @DisplayName("Should have parser registry bean")
    void shouldHaveParserRegistryBean() {
        assertThat(parserRegistry).isNotNull();
    }

    @Test
    @DisplayName("Should have all collectors registered")
    void shouldHaveCollectorsRegistered() {
        // Then
        assertThat(collectorRegistry.getAllCollectors())
                .isNotEmpty()
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Should have all parsers registered")
    void shouldHaveParsersRegistered() {
        // Then
        assertThat(parserRegistry.getRegisteredTypes())
                .isNotEmpty()
                .contains("AIS", "WEATHER", "PORT_DATA");
    }

    @Test
    @DisplayName("Should have AIS collector")
    void shouldHaveAISCollector() {
        assertThat(collectorRegistry.getCollector("ais-collector"))
                .isPresent();
    }

    @Test
    @DisplayName("Should have Weather collector")
    void shouldHaveWeatherCollector() {
        assertThat(collectorRegistry.getCollector("weather-collector"))
                .isPresent();
    }

    @Test
    @DisplayName("Should have Port Data collector")
    void shouldHavePortDataCollector() {
        assertThat(collectorRegistry.getCollector("port-data-collector"))
                .isPresent();
    }
}
