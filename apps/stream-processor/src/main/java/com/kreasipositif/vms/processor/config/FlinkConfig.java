package com.kreasipositif.vms.processor.config;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration for Flink stream processor.
 * Loaded from environment variables or configuration file.
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
     */
    public static FlinkConfig fromEnvironment() {
        return FlinkConfig.builder()
            // Kafka
            .kafkaBootstrapServers(getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"))
            .inputTopic(getEnv("KAFKA_INPUT_TOPIC", "ais-raw-data"))
            .positionsTopic(getEnv("KAFKA_POSITIONS_TOPIC", "vessel-positions"))
            .alertsTopic(getEnv("KAFKA_ALERTS_TOPIC", "vessel-alerts"))
            
            // Flink
            .parallelism(getEnvInt("FLINK_PARALLELISM", 4))
            .checkpointingEnabled(getEnvBoolean("FLINK_CHECKPOINTING_ENABLED", true))
            .checkpointInterval(getEnvLong("FLINK_CHECKPOINT_INTERVAL", 60000L))
            .stateBackend(getEnv("FLINK_STATE_BACKEND", "hashmap"))
            .checkpointDir(getEnv("FLINK_CHECKPOINT_DIR", "file:///tmp/flink-checkpoints"))
            
            // PostgreSQL
            .postgresUrl(getEnv("POSTGRES_URL", "jdbc:postgresql://localhost:5432/vms"))
            .postgresUsername(getEnv("POSTGRES_USERNAME", "postgres"))
            .postgresPassword(getEnv("POSTGRES_PASSWORD", "postgres"))
            
            // ClickHouse
            .clickhouseUrl(getEnv("CLICKHOUSE_URL", "jdbc:clickhouse://localhost:8123/vms"))
            .clickhouseUsername(getEnv("CLICKHOUSE_USERNAME", "default"))
            .clickhousePassword(getEnv("CLICKHOUSE_PASSWORD", ""))
            
            // Redis
            .redisHost(getEnv("REDIS_HOST", "localhost"))
            .redisPort(getEnvInt("REDIS_PORT", 6379))
            .redisPassword(getEnv("REDIS_PASSWORD", ""))
            
            .build();
    }
    
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
    
    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static long getEnvLong(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? Boolean.parseBoolean(value) : defaultValue;
    }
}
