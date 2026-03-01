package com.kreasipositif.vms.persistence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Data Persistence Service Application
 * 
 * This service consumes data from Kafka topics and persists to multiple databases:
 * - vessel-positions: Real-time vessel position data from Flink
 * - vessel-alerts: Anomaly alerts detected by Flink
 * - weather-data: Weather information per grid cell
 * - port-data: Port operations and vessel-berth assignments
 * 
 * Target Databases:
 * - PostgreSQL: Current state storage with spatial queries
 * - ClickHouse: Historical time-series analytics
 * - Redis: Real-time cache with TTL
 * - Elasticsearch: Full-text search and log analytics
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
public class DataPersistenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPersistenceServiceApplication.class, args);
    }
}
