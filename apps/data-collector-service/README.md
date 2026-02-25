# VMS Data Collector Service

A production-grade data collection service for the Vessel Monitoring System (VMS), built with **Java 21**, **Spring Boot 3.2**, and **Virtual Threads** for high-concurrency data collection.

## 🎯 Overview

This service collects maritime data from multiple sources:
- **AIS Data**: Real-time vessel positions from AIS APIs
- **Weather Data**: Maritime weather conditions from weather APIs  
- **Port Data**: Port information and vessel schedules

## 🏗️ Architecture & Design Patterns

### Design Patterns Used

1. **Strategy Pattern**
   - `DataParser<T>` interface with concrete implementations (AISParser, WeatherParser, PortDataParser)
   - Allows flexible parsing of different data formats

2. **Template Method Pattern**
   - `DataCollector` abstract class defines collection workflow
   - Subclasses override specific steps (doCollect, preCollect, postCollect)

3. **Factory Pattern**
   - `CollectorFactory` creates collector instances with proper dependencies
   - Centralizes object creation logic

4. **Registry Pattern**
   - `CollectorRegistry` manages all available collectors
   - `ParserRegistry` manages all data parsers
   - Provides centralized access and discovery

### Component Structure

```
com.kreasipositif.vms.collector
├── core/                    # Core framework
│   ├── DataCollector        # Abstract base collector (Template Method)
│   ├── CollectorRegistry    # Registry for collectors
│   ├── CollectorFactory     # Factory for creating collectors
│   ├── CollectorConfig      # Configuration properties
│   ├── CollectorMetadata    # Collector metadata
│   └── CollectedData        # Data container
├── parser/                  # Parser framework
│   ├── DataParser           # Parser interface (Strategy)
│   ├── ParserRegistry       # Registry for parsers
│   ├── AISParser            # AIS data parser
│   ├── WeatherParser        # Weather data parser
│   └── PortDataParser       # Port data parser
├── impl/                    # Concrete collectors
│   ├── AISCollector         # AIS data collector
│   ├── WeatherCollector     # Weather data collector
│   └── PortDataCollector    # Port data collector
├── model/                   # Data models
│   ├── AISMessage           # AIS message model
│   ├── WeatherData          # Weather data model
│   └── PortData             # Port data model
├── service/                 # Services
│   ├── KafkaProducerService        # Kafka publishing
│   └── CollectorOrchestrationService  # Scheduled execution
└── config/                  # Configuration
    ├── VirtualThreadConfig  # Java 21 Virtual Threads
    ├── WebClientConfig      # HTTP client config
    └── CollectorBootstrap   # Startup initialization
```

## 🚀 Java 21 Virtual Threads

This service leverages **Java 21 Virtual Threads** for high-concurrency operations:

### Benefits
- **100,000+ concurrent Virtual Threads** vs ~few thousand platform threads
- **Low memory footprint**: ~1 KB per Virtual Thread vs ~1 MB per platform thread
- **Simplified async code**: No callbacks or reactive complexity
- **Perfect for I/O-bound operations**: HTTP calls, database queries, Kafka

### Configuration

```java
@Configuration
public class VirtualThreadConfig {
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
```

### Usage in Collectors

```java
public CompletableFuture<List<CollectedData>> collectAsync() {
    return CompletableFuture.supplyAsync(() -> {
        // Collection logic runs on Virtual Thread
        return doCollect();
    }, virtualThreadExecutor());
}
```

## 📦 Building and Running

### Prerequisites
- **Java 21** or later
- **Maven 3.8+**
- **Kafka** (for message streaming)
- API Keys for:
  - AIS data provider (e.g., MarineTraffic)
  - Weather API (e.g., OpenWeatherMap)
  - Port data API

### Build

```bash
# From root directory
mvn clean package

# From service directory
cd apps/data-collector-service
mvn clean package

# Skip tests
mvn clean package -DskipTests
```

### Run

```bash
# Run with default configuration
mvn spring-boot:run

# Run with custom profile
mvn spring-boot:run -Dspring-boot.run.profiles=production

# Run JAR directly
java -jar target/data-collector-service-1.0.0-SNAPSHOT.jar
```

### Run with Docker

```bash
# Build Docker image
mvn spring-boot:build-image

# Run container
docker run -p 8080:8080 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e AIS_API_KEY=your-key \
  -e WEATHER_API_KEY=your-key \
  -e PORT_API_KEY=your-key \
  vms/data-collector-service:latest
```

## ⚙️ Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | localhost:9092 |
| `AIS_API_URL` | AIS API base URL | https://api.marinetraffic.com |
| `AIS_API_KEY` | AIS API key | - |
| `WEATHER_API_URL` | Weather API base URL | https://api.openweathermap.org |
| `WEATHER_API_KEY` | Weather API key | - |
| `PORT_API_URL` | Port data API base URL | https://api.portdata.com |
| `PORT_API_KEY` | Port data API key | - |
| `SERVER_PORT` | HTTP server port | 8080 |

### application.yml

```yaml
vms:
  collector:
    enabled: true
    max-concurrent-collectors: 10
    default-timeout: 30s
    
    ais:
      enabled: true
      poll-interval: 10s
      batch-size: 100
    
    weather:
      enabled: true
      poll-interval: 5m
    
    port-data:
      enabled: true
      poll-interval: 10m
```

## 🔌 Adding a New Collector

### Step 1: Create Data Model

```java
@Data
@Builder
public class MyData {
    private String id;
    private Instant timestamp;
    // ... other fields
}
```

### Step 2: Create Parser

```java
@Component
public class MyDataParser implements DataParser<MyData> {
    
    @Override
    public MyData parse(String rawData) throws ParsingException {
        // Parse logic
        return objectMapper.readValue(rawData, MyData.class);
    }
    
    @Override
    public boolean canParse(String rawData) {
        return rawData.contains("my-data-marker");
    }
    
    @Override
    public String getDataType() {
        return "MY_DATA";
    }
}
```

### Step 3: Create Collector

```java
@Component
public class MyDataCollector extends DataCollector {
    
    public MyDataCollector(
            WebClient.Builder webClientBuilder,
            MeterRegistry meterRegistry,
            CollectorConfig config,
            MyDataParser parser) {
        super(meterRegistry);
        // Initialize
    }
    
    @Override
    public CollectorMetadata getMetadata() {
        return CollectorMetadata.builder()
                .id("my-data-collector")
                .name("My Data Collector")
                .dataType(CollectorMetadata.CollectorDataType.CUSTOM)
                .kafkaTopic("my-data-topic")
                .build();
    }
    
    @Override
    protected List<CollectedData> doCollect() throws Exception {
        // Collect data from API
        String response = webClient.get().uri("/endpoint").retrieve()
                .bodyToMono(String.class).block();
        
        // Parse and return
        return parseResponse(response);
    }
}
```

### Step 4: Register in Bootstrap

```java
@Component
public class CollectorBootstrap {
    
    private final MyDataCollector myDataCollector;
    private final MyDataParser myDataParser;
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        parserRegistry.registerParser(myDataParser);
        collectorRegistry.register(myDataCollector);
    }
}
```

## 🧪 Testing

### Unit Tests

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=AISCollectorTest

# Run with coverage
mvn test jacoco:report
```

### Integration Tests

```bash
# Run integration tests (uses TestContainers for Kafka)
mvn verify

# Run specific integration test
mvn verify -Dit.test=AISCollectorIntegrationTest
```

### Test Coverage

```bash
# Generate coverage report
mvn clean test jacoco:report

# View report
open target/site/jacoco/index.html
```

## 📊 Monitoring & Metrics

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

### Prometheus Metrics

```bash
curl http://localhost:8080/actuator/prometheus
```

### Custom Metrics

- `collector.success{collector="ais-collector"}` - Successful collections
- `collector.failure{collector="ais-collector"}` - Failed collections
- `collector.duration{collector="ais-collector"}` - Collection duration
- `kafka.publish.success` - Successful Kafka publishes
- `kafka.publish.failure` - Failed Kafka publishes

## 🔒 Security

### API Key Management

Store API keys securely using:
- Environment variables (recommended for production)
- Spring Cloud Config Server
- AWS Secrets Manager / Azure Key Vault
- HashiCorp Vault

### Best Practices

1. Never commit API keys to version control
2. Use different keys for dev/staging/production
3. Rotate keys regularly
4. Monitor API usage and rate limits

## 🚢 Deployment

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: data-collector-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: data-collector-service
  template:
    metadata:
      labels:
        app: data-collector-service
    spec:
      containers:
      - name: data-collector
        image: vms/data-collector-service:latest
        ports:
        - containerPort: 8080
        env:
        - name: KAFKA_BOOTSTRAP_SERVERS
          value: "kafka:9092"
        resources:
          requests:
            cpu: 1
            memory: 1Gi
          limits:
            cpu: 2
            memory: 2Gi
```

## 📝 License

Copyright © 2024 KreasiPositif. All rights reserved.

## 👥 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/new-collector`)
3. Commit your changes (`git commit -am 'Add new collector'`)
4. Push to the branch (`git push origin feature/new-collector`)
5. Create a Pull Request

## 📞 Support

For issues, questions, or contributions, please contact the VMS team.
