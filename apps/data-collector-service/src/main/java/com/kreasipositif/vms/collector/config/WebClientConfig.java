package com.kreasipositif.vms.collector.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
                .exchangeStrategies(strategies)
                .filter(logRequest())
                .filter(logResponse());
    }
    
    /**
     * Log outgoing HTTP requests with full details
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("=== WebClient Request ===");
            log.info("Method: {}", clientRequest.method());
            log.info("URL: {}", clientRequest.url());
            log.info("Headers: {}", clientRequest.headers());
            
            // Log cookies if any
            if (!clientRequest.cookies().isEmpty()) {
                log.info("Cookies: {}", clientRequest.cookies());
            }
            
            log.info("========================");
            return Mono.just(clientRequest);
        });
    }
    
    /**
     * Log incoming HTTP responses with status and headers
     */
    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            log.info("=== WebClient Response ===");
            log.info("Status Code: {}", clientResponse.statusCode());
            log.info("Headers: {}", clientResponse.headers().asHttpHeaders());
            log.info("=========================");
            return Mono.just(clientResponse);
        });
    }
}
