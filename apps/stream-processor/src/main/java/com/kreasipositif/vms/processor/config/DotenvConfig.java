package com.kreasipositif.vms.processor.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Configuration utility for loading environment variables from .env file.
 * This allows the Flink application to read configuration from .env file
 * when running locally, while still supporting traditional environment variables
 * in production deployments.
 */
@Slf4j
public class DotenvConfig {

    private static Dotenv dotenv;

    static {
        try {
            // Try to load .env file from current directory or parent directories
            dotenv = Dotenv.configure()
                    .directory(".")
                    .ignoreIfMalformed()
                    .ignoreIfMissing()
                    .load();
            
            log.info("✅ .env file loaded successfully");
        } catch (Exception e) {
            log.warn("⚠️  No .env file found or failed to load. Using system environment variables.");
            dotenv = null;
        }
    }

    /**
     * Get environment variable value with fallback to system environment.
     * Tries .env file first, then falls back to system environment variables.
     *
     * @param key Environment variable key
     * @return Optional containing the value if found
     */
    public static Optional<String> get(String key) {
        String value = null;
        
        // Try .env file first
        if (dotenv != null) {
            value = dotenv.get(key);
        }
        
        // Fallback to system environment
        if (value == null) {
            value = System.getenv(key);
        }
        
        return Optional.ofNullable(value);
    }

    /**
     * Get environment variable value with default.
     *
     * @param key Environment variable key
     * @param defaultValue Default value if not found
     * @return The value or default
     */
    public static String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * Get environment variable as integer with default.
     *
     * @param key Environment variable key
     * @param defaultValue Default value if not found or not parseable
     * @return The integer value or default
     */
    public static int getInt(String key, int defaultValue) {
        return get(key)
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid integer value for {}: {}. Using default: {}", key, value, defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    /**
     * Get environment variable as long with default.
     *
     * @param key Environment variable key
     * @param defaultValue Default value if not found or not parseable
     * @return The long value or default
     */
    public static long getLong(String key, long defaultValue) {
        return get(key)
                .map(value -> {
                    try {
                        return Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid long value for {}: {}. Using default: {}", key, value, defaultValue);
                        return defaultValue;
                    }
                })
                .orElse(defaultValue);
    }

    /**
     * Get environment variable as boolean with default.
     *
     * @param key Environment variable key
     * @param defaultValue Default value if not found
     * @return The boolean value or default
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        return get(key)
                .map(Boolean::parseBoolean)
                .orElse(defaultValue);
    }

    /**
     * Check if a key exists in environment.
     *
     * @param key Environment variable key
     * @return true if key exists
     */
    public static boolean has(String key) {
        return get(key).isPresent();
    }

    /**
     * Get required environment variable or throw exception.
     *
     * @param key Environment variable key
     * @return The value
     * @throws IllegalStateException if key is not found
     */
    public static String getRequired(String key) {
        return get(key)
                .orElseThrow(() -> new IllegalStateException(
                        "Required environment variable not found: " + key));
    }
}
