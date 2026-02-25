package com.kreasipositif.vms.collector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient used by collectors.
 * Configures HTTP clients with appropriate timeouts and buffer sizes.
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        log.info("Configuring WebClient for data collectors");
        
        // Increase buffer size for large responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
        
        return WebClient.builder()
                .exchangeStrategies(strategies);
    }
}
