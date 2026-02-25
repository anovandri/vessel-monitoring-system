package com.kreasipositif.vms.collector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Main application class for VMS Data Collector Service.
 * 
 * Features:
 * - Java 21 Virtual Threads for high concurrency
 * - Multiple data collectors (AIS, Weather, Port Data)
 * - Design patterns: Strategy, Factory, Registry, Template Method
 * - Kafka integration for data streaming
 * - Scheduled data collection
 * - Metrics and monitoring
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableRetry
public class DataCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataCollectorApplication.class, args);
    }
}
