package com.kreasipositif.vms.processor.config;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for Flink stream processor.
 * Loaded from environment variables, .env file, or uses defaults.
 */
@Data
@Builder
public class FlinkConfig {
    
    // Kafka Configuration
    private String kafkaBootstrapServers;
    private String inputTopic;
    private String positionsTopic;
    private String alertsTopic;
    
    // Flink Configuration
    private int parallelism;
    private boolean checkpointingEnabled;
    private long checkpointInterval;
    private String stateBackend;
    private String checkpointDir;
    
    // PostgreSQL Configuration
    private String postgresUrl;
    private String postgresUsername;
    private String postgresPassword;
    
    // ClickHouse Configuration
    private String clickhouseUrl;
    private String clickhouseUsername;
    private String clickhousePassword;
    
    // Redis Configuration
    private String redisHost;
    private int redisPort;
    private String redisPassword;
    
    /**
     * Load configuration from environment variables with defaults.
     * Uses DotenvConfig to support .env files for local development.
     */
    public static FlinkConfig fromEnvironment() {
        return FlinkConfig.builder()
            // Kafka
            .kafkaBootstrapServers(DotenvConfig.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
            .inputTopic(DotenvConfig.get("KAFKA_INPUT_TOPIC", "ais-raw-data"))
            .positionsTopic(DotenvConfig.get("KAFKA_POSITIONS_TOPIC", "vessel-positions"))
            .alertsTopic(DotenvConfig.get("KAFKA_ALERTS_TOPIC", "vessel-alerts"))
            
            // Flink
            .parallelism(DotenvConfig.getInt("FLINK_PARALLELISM", 4))
            .checkpointingEnabled(DotenvConfig.getBoolean("FLINK_CHECKPOINTING_ENABLED", true))
            .checkpointInterval(DotenvConfig.getLong("FLINK_CHECKPOINT_INTERVAL", 60000L))
            .stateBackend(DotenvConfig.get("FLINK_STATE_BACKEND", "hashmap"))
            .checkpointDir(DotenvConfig.get("FLINK_CHECKPOINT_DIR", "file:///tmp/flink-checkpoints"))
            
            // PostgreSQL
            .postgresUrl(DotenvConfig.get("POSTGRES_URL", "jdbc:postgresql://localhost:5432/vms"))
            .postgresUsername(DotenvConfig.get("POSTGRES_USERNAME", "postgres"))
            .postgresPassword(DotenvConfig.get("POSTGRES_PASSWORD", "postgres"))
            
            // ClickHouse
            .clickhouseUrl(DotenvConfig.get("CLICKHOUSE_URL", "jdbc:clickhouse://localhost:8123/vms"))
            .clickhouseUsername(DotenvConfig.get("CLICKHOUSE_USERNAME", "default"))
            .clickhousePassword(DotenvConfig.get("CLICKHOUSE_PASSWORD", ""))
            
            // Redis
            .redisHost(DotenvConfig.get("REDIS_HOST", "localhost"))
            .redisPort(DotenvConfig.getInt("REDIS_PORT", 6379))
            .redisPassword(DotenvConfig.get("REDIS_PASSWORD", ""))
            
            .build();
    }
}
