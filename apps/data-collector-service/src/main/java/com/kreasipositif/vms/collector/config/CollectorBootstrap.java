package com.kreasipositif.vms.collector.config;

import com.kreasipositif.vms.collector.core.CollectorRegistry;
import com.kreasipositif.vms.collector.impl.AISCollector;
import com.kreasipositif.vms.collector.impl.PortDataCollector;
import com.kreasipositif.vms.collector.impl.WeatherCollector;
import com.kreasipositif.vms.collector.parser.AISParser;
import com.kreasipositif.vms.collector.parser.ParserRegistry;
import com.kreasipositif.vms.collector.parser.PortDataParser;
import com.kreasipositif.vms.collector.parser.WeatherParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Bootstrap configuration that registers collectors and parsers on application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorBootstrap {

    private final CollectorRegistry collectorRegistry;
    private final ParserRegistry parserRegistry;
    private final AISCollector aisCollector;
    private final WeatherCollector weatherCollector;
    private final PortDataCollector portDataCollector;
    private final AISParser aisParser;
    private final WeatherParser weatherParser;
    private final PortDataParser portDataParser;

    /**
     * Initialize and register all collectors and parsers when application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("===============================================");
        log.info("Bootstrapping VMS Data Collector Service");
        log.info("===============================================");
        
        registerParsers();
        registerCollectors();
        
        logRegistrationSummary();
        
        log.info("===============================================");
        log.info("Data Collector Service is READY");
        log.info("===============================================");
    }

    /**
     * Register all parsers
     */
    private void registerParsers() {
        log.info("Registering data parsers...");
        
        parserRegistry.registerParser(aisParser);
        parserRegistry.registerParser(weatherParser);
        parserRegistry.registerParser(portDataParser);
        
        log.info("Registered {} parsers", parserRegistry.getParserCount());
    }

    /**
     * Register all collectors
     */
    private void registerCollectors() {
        log.info("Registering data collectors...");
        
        collectorRegistry.register(aisCollector);
        collectorRegistry.register(weatherCollector);
        collectorRegistry.register(portDataCollector);
        
        log.info("Registered {} collectors", collectorRegistry.getCollectorCount());
    }

    /**
     * Log registration summary
     */
    private void logRegistrationSummary() {
        log.info("");
        log.info("=== Registration Summary ===");
        log.info("Total Collectors: {}", collectorRegistry.getCollectorCount());
        log.info("Enabled Collectors: {}", collectorRegistry.getEnabledCollectorCount());
        log.info("Total Parsers: {}", parserRegistry.getParserCount());
        log.info("");
        
        log.info("Registered Collectors:");
        collectorRegistry.getAllMetadata().forEach(meta -> {
            log.info("  - {} [{}] - Enabled: {}, Priority: {}, Topic: {}",
                    meta.getName(),
                    meta.getId(),
                    meta.isEnabled(),
                    meta.getPriority(),
                    meta.getKafkaTopic());
        });
        
        log.info("");
        log.info("Registered Parsers:");
        parserRegistry.getRegisteredTypes().forEach(type -> {
            log.info("  - {} Parser", type);
        });
        log.info("");
    }
}
