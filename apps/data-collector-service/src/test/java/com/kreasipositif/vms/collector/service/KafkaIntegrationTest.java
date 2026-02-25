package com.kreasipositif.vms.collector.service;

import com.kreasipositif.vms.collector.core.CollectedData;
import com.kreasipositif.vms.collector.core.CollectorMetadata;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        brokerProperties = {
                "listeners=PLAINTEXT://localhost:9092",
                "port=9092"
        },
        topics = {"test-topic", "ais-data", "weather-data", "port-data"}
)
@DirtiesContext
@DisplayName("Kafka Integration Tests")
class KafkaIntegrationTest {

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaConsumer<String, String> consumer;
    private static final String TEST_TOPIC = "test-topic";

    @BeforeEach
    void setUp() {
        String bootstrapServers = embeddedKafkaBroker.getBrokersAsString();
        
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(TEST_TOPIC));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Should publish message to Kafka successfully")
    void shouldPublishMessageSuccessfully() throws Exception {
        // Given
        CollectedData data = createTestCollectedData();

        // When
        var future = kafkaProducerService.publishAsync(TEST_TOPIC, data);
        future.get(5, TimeUnit.SECONDS);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    assertThat(records.count()).isGreaterThan(0);
                    records.forEach(record -> {
                        assertThat(record.topic()).isEqualTo(TEST_TOPIC);
                        assertThat(record.value()).contains(data.getId());
                    });
                });
    }

    @Test
    @DisplayName("Should publish batch of messages successfully")
    void shouldPublishBatchSuccessfully() throws Exception {
        // Given
        var data1 = createTestCollectedData();
        var data2 = createTestCollectedData();
        var data3 = createTestCollectedData();

        // When
        var future = kafkaProducerService.publishBatchAsync(
                TEST_TOPIC, 
                java.util.List.of(data1, data2, data3)
        );
        future.get(5, TimeUnit.SECONDS);

        // Then
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    assertThat(records.count()).isGreaterThanOrEqualTo(3);
                });
    }

    @Test
    @DisplayName("Should handle publishing errors gracefully")
    void shouldHandlePublishingErrors() {
        // Given
        CollectedData invalidData = null;

        // When/Then
        assertThatThrownBy(() -> 
                kafkaProducerService.publishAsync(TEST_TOPIC, invalidData).get(5, TimeUnit.SECONDS)
        ).hasCauseInstanceOf(NullPointerException.class);
    }

    private CollectedData createTestCollectedData() {
        return CollectedData.builder()
                .id(UUID.randomUUID().toString())
                .dataType(CollectorMetadata.CollectorDataType.AIS_RAW)
                .collectorId("test-collector")
                .timestamp(Instant.now())
                .rawData("{\"test\": \"data\"}")
                .parsedData(new HashMap<>())
                .metadata(Map.of("test", "metadata"))
                .qualityScore(85)
                .source("TEST")
                .build();
    }
}
