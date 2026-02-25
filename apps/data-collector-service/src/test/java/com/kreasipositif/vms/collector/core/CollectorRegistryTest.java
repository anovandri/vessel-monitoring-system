package com.kreasipositif.vms.collector.core;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("CollectorRegistry Tests")
class CollectorRegistryTest {

    private CollectorRegistry registry;
    private TestDataCollector collector1;
    private TestDataCollector collector2;
    private TestDataCollector collector3;

    @BeforeEach
    void setUp() {
        registry = new CollectorRegistry();
        
        collector1 = new TestDataCollector(
                "collector-1", 
                "Test Collector 1", 
                CollectorMetadata.CollectorDataType.AIS_RAW,
                true,
                1
        );
        
        collector2 = new TestDataCollector(
                "collector-2", 
                "Test Collector 2", 
                CollectorMetadata.CollectorDataType.WEATHER,
                true,
                2
        );
        
        collector3 = new TestDataCollector(
                "collector-3", 
                "Test Collector 3", 
                CollectorMetadata.CollectorDataType.PORT_DATA,
                false,
                3
        );
    }

    @Test
    @DisplayName("Should register collector successfully")
    void shouldRegisterCollector() {
        // When
        registry.register(collector1);

        // Then
        assertThat(registry.getCollector("collector-1"))
                .isPresent()
                .contains(collector1);
        assertThat(registry.getAllCollectors()).hasSize(1);
    }

    @Test
    @DisplayName("Should not register collector with duplicate ID")
    void shouldNotRegisterDuplicateCollector() {
        // Given
        registry.register(collector1);

        // When/Then
        assertThatThrownBy(() -> registry.register(collector1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Should unregister collector successfully")
    void shouldUnregisterCollector() {
        // Given
        registry.register(collector1);

        // When
        registry.unregister("collector-1");

        // Then
        assertThat(registry.getCollector("collector-1")).isEmpty();
        assertThat(registry.getAllCollectors()).isEmpty();
    }

    @Test
    @DisplayName("Should get all collectors")
    void shouldGetAllCollectors() {
        // Given
        registry.register(collector1);
        registry.register(collector2);
        registry.register(collector3);

        // When
        var collectors = registry.getAllCollectors();

        // Then
        assertThat(collectors).hasSize(3)
                .contains(collector1, collector2, collector3);
    }

    @Test
    @DisplayName("Should get only enabled collectors")
    void shouldGetEnabledCollectors() {
        // Given
        registry.register(collector1);
        registry.register(collector2);
        registry.register(collector3);

        // When
        List<DataCollector> enabledCollectors = registry.getEnabledCollectors();

        // Then
        assertThat(enabledCollectors).hasSize(2)
                .contains(collector1, collector2)
                .doesNotContain(collector3);
    }

    @Test
    @DisplayName("Should get collectors by type")
    void shouldGetCollectorsByType() {
        // Given
        registry.register(collector1);
        registry.register(collector2);
        registry.register(collector3);

        // When
        List<DataCollector> aisCollectors = registry.getCollectorsByType(
                CollectorMetadata.CollectorDataType.AIS_RAW
        );

        // Then
        assertThat(aisCollectors).hasSize(1).contains(collector1);
    }

    @Test
    @DisplayName("Should get collectors sorted by priority")
    void shouldGetCollectorsByPriority() {
        // Given
        registry.register(collector3);
        registry.register(collector1);
        registry.register(collector2);

        // When
        var sorted = registry.getCollectorsByPriority();

        // Then - Higher priority numbers come first (descending order)
        assertThat(sorted).hasSize(3)
                .containsExactly(collector3, collector2, collector1);
    }

    @Test
    @DisplayName("Should report healthy when all collectors healthy")
    void shouldReportHealthyWhenAllHealthy() {
        // Given
        registry.register(collector1);
        registry.register(collector2);
        
        collector1.setHealthy(true);
        collector2.setHealthy(true);

        // When/Then
        assertThat(registry.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should report unhealthy when any collector unhealthy")
    void shouldReportUnhealthyWhenAnyUnhealthy() {
        // Given
        registry.register(collector1);
        registry.register(collector2);
        
        collector1.setHealthy(true);
        collector2.setHealthy(false);

        // When/Then
        assertThat(registry.isHealthy()).isFalse();
    }

    @Test
    @DisplayName("Should get detailed health status")
    void shouldGetDetailedHealthStatus() {
        // Given
        registry.register(collector1);
        registry.register(collector2);
        
        collector1.setHealthy(true);
        collector2.setHealthy(false);

        // When
        Map<String, Boolean> healthStatus = registry.getHealthStatus();

        // Then
        assertThat(healthStatus)
                .hasSize(2)
                .containsEntry("collector-1", true)
                .containsEntry("collector-2", false);
    }

    // Test implementation of DataCollector
    private static class TestDataCollector extends DataCollector {
        private boolean healthy = true;

        public TestDataCollector(
                String id, 
                String name, 
                CollectorMetadata.CollectorDataType dataType,
                boolean enabled,
                int priority) {
            super(new SimpleMeterRegistry(), CollectorMetadata.builder()
                    .id(id)
                    .name(name)
                    .description("Test collector")
                    .dataType(dataType)
                    .enabled(enabled)
                    .priority(priority)
                    .pollInterval(Duration.ofMillis(10000))
                    .version("1.0.0")
                    .kafkaTopic("test-topic")
                    .build());
        }

        @Override
        protected List<CollectedData> doCollect() throws Exception {
            return List.of();
        }

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }
    }
}
