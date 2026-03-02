# Data Persistence Service

A high-performance Spring Boot service that consumes data from Kafka topics and persists to multiple databases using Java 21 Virtual Threads.

## Overview

This service implements **Option B Architecture** (Separate Consumer Services) where Flink outputs to Kafka topics, and this dedicated service handles all database persistence operations.

### Data Flow

```
Kafka Topics → Data Persistence Service → Multiple Databases
    ↓                     ↓                      ↓
vessel-positions   Virtual Threads      PostgreSQL
vessel-alerts      Async Processing     ClickHouse
weather-data       Batch Operations     Redis
port-data                               Elasticsearch
```

## Features

- **Multi-Database Persistence**: PostgreSQL, ClickHouse, Redis, Elasticsearch
- **Virtual Threads (Java 21)**: High concurrency with low overhead
- **Kafka Consumer**: Manual acknowledgment, batch processing
- **Async Operations**: Parallel writes to multiple databases
- **Spatial Support**: PostGIS for geospatial queries
- **Time-Series Analytics**: ClickHouse for historical data
- **Real-time Cache**: Redis with TTL-based expiration
- **Full-Text Search**: Elasticsearch for logs and alerts
- **🆕 WebSocket Real-time Updates**: STOMP over SockJS for browser clients
- **🆕 Redis Pub/Sub Bridge**: Low-latency message broadcasting

## Architecture

### Consumer Groups

| Topic              | Consumer Group             | Concurrency | Batch Size |
|--------------------|----------------------------|-------------|------------|
| vessel-positions   | data-persistence-service   | 3           | 500        |
| vessel-alerts      | data-persistence-service   | 3           | 500        |
| weather-data       | data-persistence-service   | 3           | 500        |
| port-data          | data-persistence-service   | 3           | 500        |

### Database Strategy

#### PostgreSQL (Current State)
- **Vessel Positions**: Upsert by MMSI (current location)
- **Vessel Alerts**: Active alerts table
- **Weather Data**: Current weather per grid cell
- **Port Operations**: Current berth assignments

#### ClickHouse (Historical Analytics)
- **Vessel Positions**: Append-only time-series
- **Vessel Alerts**: Alert history for analysis
- **Weather Data**: Historical weather trends
- **Port Operations**: Port event history

#### Redis (Real-time Cache)
- **Vessel Positions**: 5-minute TTL
- **Vessel Alerts**: 1-hour TTL
- **Weather Data**: 30-minute TTL
- **Port Operations**: 10-minute TTL

#### Elasticsearch (Search & Logs)
- **Vessel Alerts**: Searchable alert logs
- **Future**: Vessel search, operational events

## Technology Stack

- **Java 21** with Virtual Threads
- **Spring Boot 3.2**
- **Apache Kafka** for streaming
- **PostgreSQL + PostGIS** for spatial queries
- **ClickHouse** for time-series analytics
- **Redis** with Lettuce client
- **Elasticsearch 8.12** for search
- **HikariCP** for connection pooling
- **Lombok** for boilerplate reduction

## Prerequisites

- Java 21 or higher
- Maven 3.9+
- Kafka cluster (topics: vessel-positions, vessel-alerts, weather-data, port-data)
- PostgreSQL 15+ with PostGIS extension
- ClickHouse 23+
- Redis 7+
- Elasticsearch 8+

## Configuration

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

### Key Configurations

```yaml
# application.yml
spring.kafka.consumer.max-poll-records: 500
spring.datasource.hikari.maximum-pool-size: 20
clickhouse.pool-size: 10
```

## Database Schema

### PostgreSQL Tables

```sql
-- Vessel Positions (Current State)
CREATE TABLE vessel_positions (
    mmsi INTEGER PRIMARY KEY,
    vessel_name VARCHAR(255),
    vessel_type VARCHAR(100),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    location GEOMETRY(Point, 4326),
    speed DOUBLE PRECISION,
    course DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    timestamp TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Vessel Alerts (Active)
CREATE TABLE vessel_alerts (
    alert_id VARCHAR(255) PRIMARY KEY,
    mmsi INTEGER,
    vessel_name VARCHAR(255),
    alert_type VARCHAR(100),
    severity VARCHAR(50),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    timestamp TIMESTAMP,
    description TEXT,
    metadata JSONB
);

-- Weather Data (Current)
CREATE TABLE weather_data (
    grid_id VARCHAR(100) PRIMARY KEY,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    location GEOMETRY(Point, 4326),
    temperature DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    wind_direction DOUBLE PRECISION,
    wave_height DOUBLE PRECISION,
    timestamp TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Port Operations (Current)
CREATE TABLE port_operations (
    operation_id VARCHAR(255) PRIMARY KEY,
    port_id VARCHAR(100),
    port_name VARCHAR(255),
    mmsi INTEGER,
    vessel_name VARCHAR(255),
    operation_type VARCHAR(100),
    berth_number VARCHAR(50),
    arrival_time TIMESTAMP,
    departure_time TIMESTAMP,
    status VARCHAR(50),
    timestamp TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### ClickHouse Tables

```sql
-- Vessel Positions History
CREATE TABLE vessel_positions_history (
    mmsi UInt32,
    vessel_name String,
    latitude Float64,
    longitude Float64,
    speed Float64,
    course Float64,
    timestamp DateTime,
    date Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (mmsi, timestamp);

-- Vessel Alerts History
CREATE TABLE vessel_alerts_history (
    alert_id String,
    mmsi UInt32,
    alert_type String,
    severity String,
    timestamp DateTime,
    date Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (timestamp, mmsi);
```

## Building and Running

### Using Maven

```bash
# Build
mvn clean package

# Run
java -jar target/data-persistence-service-1.0.0-SNAPSHOT.jar
```

### Using Nx

```bash
# Build
nx build data-persistence-service

# Run
nx serve data-persistence-service

# Test
nx test data-persistence-service
```

### Using Docker

```bash
# Build image
docker build -t data-persistence-service .

# Run container
docker run -d \
  --name data-persistence-service \
  -p 8082:8082 \
  --env-file .env \
  data-persistence-service
```

## Monitoring

### Health Check

```bash
curl http://localhost:8082/actuator/health
```

### Metrics (Prometheus)

```bash
curl http://localhost:8082/actuator/prometheus
```

### Key Metrics

- `kafka_consumer_records_consumed_total`: Total records consumed
- `kafka_consumer_lag`: Consumer lag per partition
- `hikari_connections_active`: Active database connections
- `redis_commands_completed_total`: Redis operations
- `jvm_threads_states_threads{state="virtual"}`: Virtual thread count

## Performance Tuning

### Virtual Threads

```java
@Bean
public AsyncTaskExecutor asyncTaskExecutor() {
    return new TaskExecutorAdapter(
        Executors.newVirtualThreadPerTaskExecutor()
    );
}
```

### Kafka Batch Processing

```yaml
spring:
  kafka:
    consumer:
      max-poll-records: 500  # Adjust based on message size
      fetch-max-wait: 500ms
```

### Database Connection Pools

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Virtual threads handle concurrency
      minimum-idle: 5
```

## Error Handling

- **Kafka Errors**: No acknowledgment → automatic retry
- **Database Errors**: Logged and propagated → retry via Kafka
- **Redis Errors**: Logged but don't block processing
- **Elasticsearch Errors**: Logged but don't block processing

## Future Enhancements

- [x] ✅ **WebSocket Real-time Updates** - COMPLETED
- [x] ✅ **Redis Pub/Sub Bridge** - COMPLETED  
- [ ] Elasticsearch integration for vessel search
- [ ] Batch inserts optimization for ClickHouse
- [ ] Dead Letter Queue (DLQ) for failed messages
- [ ] Circuit breaker for database failures
- [ ] Distributed tracing with OpenTelemetry

## WebSocket Real-time Updates

This service now includes WebSocket support for real-time browser updates!

### Quick Start
1. **Test Page**: Open `http://localhost:8083/websocket-test.html`
2. **Health Check**: `curl http://localhost:8083/api/websocket/health`
3. **Documentation**: See [WEBSOCKET.md](./WEBSOCKET.md) for complete guide

### WebSocket Topics
- `/topic/vessel-positions` - Real-time vessel positions (1-2s updates)
- `/topic/weather-data` - Weather grid updates
- `/topic/port-data` - Port operation changes
- `/topic/vessel-alerts` - Critical vessel alerts

### Architecture
```
Kafka → Persistence Service → PostgreSQL/ClickHouse/Redis
                           ↓
                      Redis Pub/Sub
                           ↓
                    WebSocket Bridge
                           ↓
                    Browser Clients
```

**📚 Complete Guide**: [WEBSOCKET-IMPLEMENTATION-SUMMARY.md](./WEBSOCKET-IMPLEMENTATION-SUMMARY.md)

## Testing

```bash
# Unit tests
mvn test

# Integration tests (requires Testcontainers)
mvn verify

# Kafka integration test
mvn test -Dtest=VesselPositionConsumerTest
```

## Contributing

1. Follow Spring Boot best practices
2. Use Lombok for boilerplate reduction
3. Write tests for all persistence operations
4. Document database schema changes
5. Update README for configuration changes

## License

MIT License
