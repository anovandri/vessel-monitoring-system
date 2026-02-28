package com.kreasipositif.vms.collector.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Configuration class to automatically load environment variables from .env file.
 * This allows developers to use .env files without manually exporting variables.
 * 
 * The .env file should be placed in the project root directory.
 */
@Slf4j
@Configuration
public class DotenvConfig {

    @PostConstruct
    public void loadDotenv() {
        try {
            // Check if .env file exists
            if (Files.exists(Paths.get(".env"))) {
                log.info("📄 Loading environment variables from .env file...");
                
                try (BufferedReader reader = new BufferedReader(new FileReader(".env"))) {
                    String line;
                    int count = 0;
                    
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        
                        // Skip empty lines and comments
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        
                        // Parse KEY=VALUE
                        int equalsIndex = line.indexOf('=');
                        if (equalsIndex > 0) {
                            String key = line.substring(0, equalsIndex).trim();
                            String value = line.substring(equalsIndex + 1).trim();
                            
                            // Remove quotes if present
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            } else if (value.startsWith("'") && value.endsWith("'")) {
                                value = value.substring(1, value.length() - 1);
                            }
                            
                            // Only set if not already set by actual environment variable
                            if (System.getenv(key) == null) {
                                System.setProperty(key, value);
                                log.debug("✅ Loaded: {} = {}", key, maskSensitiveValue(key, value));
                                count++;
                            } else {
                                log.debug("⏭️  Skipped: {} (already set in environment)", key);
                            }
                        }
                    }
                    
                    log.info("✅ Loaded {} environment variables from .env file", count);
                }
                
            } else {
                log.info("ℹ️  No .env file found, using system environment variables");
            }
        } catch (Exception e) {
            log.warn("⚠️  Failed to load .env file: {}", e.getMessage());
        }
    }
    
    /**
     * Mask sensitive values in logs (API keys, passwords, etc.)
     */
    private String maskSensitiveValue(String key, String value) {
        if (key.toUpperCase().contains("KEY") || 
            key.toUpperCase().contains("PASSWORD") || 
            key.toUpperCase().contains("SECRET") ||
            key.toUpperCase().contains("TOKEN")) {
            if (value.length() <= 4) {
                return "****";
            }
            return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
        }
        return value;
    }
}
