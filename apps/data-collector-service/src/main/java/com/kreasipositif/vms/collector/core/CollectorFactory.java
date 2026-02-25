package com.kreasipositif.vms.collector.core;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory pattern for creating collector instances.
 * Handles dependency injection and configuration for new collectors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorFactory {

    private final WebClient.Builder webClientBuilder;
    private final MeterRegistry meterRegistry;
    private final CollectorConfig config;

    /**
     * Create a collector of specified type with its dependencies injected
     */
    public <T extends DataCollector> T createCollector(
            Class<T> collectorClass,
            Object... additionalDependencies) {
        
        try {
            log.debug("Creating collector of type: {}", collectorClass.getSimpleName());
            
            // This is a simplified factory - in production, you might use reflection
            // or a more sophisticated DI mechanism
            
            return instantiateCollector(collectorClass, additionalDependencies);
            
        } catch (Exception e) {
            log.error("Failed to create collector {}: {}", 
                    collectorClass.getSimpleName(), e.getMessage(), e);
            throw new CollectorCreationException(
                    "Failed to create collector: " + collectorClass.getSimpleName(), e);
        }
    }

    /**
     * Instantiate collector with proper dependencies
     */
    private <T extends DataCollector> T instantiateCollector(
            Class<T> collectorClass,
            Object... additionalDependencies) throws Exception {
        
        // Get common dependencies
        WebClient webClient = webClientBuilder.build();
        
        // Use constructor injection - this is a simplified version
        // In a real implementation, you'd use reflection or Spring's BeanFactory
        
        var constructor = collectorClass.getDeclaredConstructors()[0];
        
        // Build parameter array
        Object[] params = buildConstructorParams(
                constructor.getParameterTypes(),
                webClient,
                additionalDependencies
        );
        
        @SuppressWarnings("unchecked")
        T instance = (T) constructor.newInstance(params);
        
        log.info("Successfully created collector: {}", collectorClass.getSimpleName());
        
        return instance;
    }

    /**
     * Build constructor parameters based on required types
     */
    private Object[] buildConstructorParams(
            Class<?>[] paramTypes,
            WebClient webClient,
            Object... additionalDependencies) {
        
        Object[] params = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> type = paramTypes[i];
            
            if (WebClient.class.isAssignableFrom(type)) {
                params[i] = webClient;
            } else if (MeterRegistry.class.isAssignableFrom(type)) {
                params[i] = meterRegistry;
            } else if (CollectorConfig.class.isAssignableFrom(type)) {
                params[i] = config;
            } else {
                // Try to match with additional dependencies
                params[i] = findMatchingDependency(type, additionalDependencies);
            }
        }
        
        return params;
    }

    /**
     * Find matching dependency from additional dependencies
     */
    private Object findMatchingDependency(Class<?> type, Object... dependencies) {
        for (Object dep : dependencies) {
            if (dep != null && type.isAssignableFrom(dep.getClass())) {
                return dep;
            }
        }
        throw new IllegalArgumentException(
                "No matching dependency found for type: " + type.getSimpleName());
    }

    /**
     * Create and configure WebClient for a specific base URL
     */
    public WebClient createWebClient(String baseUrl) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Exception thrown when collector creation fails
     */
    public static class CollectorCreationException extends RuntimeException {
        public CollectorCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
