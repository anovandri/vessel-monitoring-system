package com.kreasipositif.vms.persistence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

/**
 * Virtual Thread Configuration for Java 21
 * 
 * Enables virtual threads for:
 * - Kafka consumer threads
 * - Async database operations
 * - Redis pub/sub operations
 * - Elasticsearch bulk operations
 */
@Configuration
@EnableAsync
public class VirtualThreadConfig {

    /**
     * Configure virtual thread executor for async operations
     */
    @Bean(name = "virtualThreadExecutor")
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
