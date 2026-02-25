package com.kreasipositif.vms.collector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executors;

/**
 * Configuration for Java 21 Virtual Threads.
 * Enables lightweight, high-concurrency thread execution for data collectors.
 * 
 * Benefits of Virtual Threads:
 * - Can handle 100K+ concurrent tasks
 * - Low memory footprint (KB vs MB for platform threads)
 * - Simplified async programming
 * - Ideal for I/O-bound operations like HTTP calls
 */
@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class VirtualThreadConfig {

    /**
     * Configure Tomcat to use Virtual Threads for request handling.
     * This allows the web server to handle massive concurrent requests efficiently.
     */
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            log.info("Configuring Tomcat to use Virtual Threads");
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }

    /**
     * Configure Spring's async task executor to use Virtual Threads.
     * Used by @Async methods and scheduled tasks.
     */
    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor applicationTaskExecutor() {
        log.info("Configuring application task executor with Virtual Threads");
        
        return new TaskExecutorAdapter(
                Executors.newVirtualThreadPerTaskExecutor()
        );
    }

    /**
     * Virtual thread executor for parallel data collection.
     * Each collector can use this for concurrent operations.
     */
    @Bean("collectorExecutor")
    public java.util.concurrent.ExecutorService collectorExecutor() {
        log.info("Creating collector executor with Virtual Threads");
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Log virtual thread configuration on startup
     */
    @Bean
    public VirtualThreadLogger virtualThreadLogger() {
        return new VirtualThreadLogger();
    }

    @Slf4j
    static class VirtualThreadLogger {
        public VirtualThreadLogger() {
            log.info("==========================================");
            log.info("Java 21 Virtual Threads ENABLED");
            log.info("Platform: {}", System.getProperty("java.version"));
            log.info("Max concurrency: 100,000+ Virtual Threads");
            log.info("Memory per thread: ~1 KB (vs ~1 MB platform thread)");
            log.info("Ideal for: I/O-bound operations (HTTP, DB, Kafka)");
            log.info("==========================================");
        }
    }
}
