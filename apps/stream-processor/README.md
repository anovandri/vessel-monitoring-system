# VMS Stream Processor

Apache Flink-based stream processor for real-time vessel position validation, enrichment, and anomaly detection.

## Overview

This service consumes raw AIS data from Kafka (`ais-raw-data` topic), processes it through multiple stages, and produces:
- **Validated & enriched positions** → `vessel-positions` topic
- **Alerts** (geofence violations, anomalies) → `vessel-alerts` topic
- **Database writes** → PostgreSQL, ClickHouse, Redis

## Architecture

```
┌─────────────────┐
│  Kafka Source   │
│ (ais-raw-data)  │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Validation    │ ← Filter invalid coordinates, speed, timestamps
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   Enrichment    │ ← Add vessel master data (type, flag, destination)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│Speed Anomaly    │ ← Detect excessive speed, sudden stops
│  Detection      │
└────────┬────────┘
         │
         ├──────────────┬──────────────┐
         ▼              ▼              ▼
    ┌────────┐    ┌────────┐    ┌────────┐
    │ Kafka  │    │Postgres│    │ Redis  │
    │ Sinks  │    │  JDBC  │    │  Sink  │
    └────────┘    └────────┘    └────────┘
```

## Processing Stages

### 1. **Position Validation** (`PositionValidationFunction`)
- Validates MMSI (9-digit maritime identifier)
- Validates coordinates (lat: -90 to 90, lon: -180 to 180)
- Validates speed (0-102.3 knots)
- Validates timestamps (not in future, not >1 hour old)
- Invalid messages sent to side output (dead letter queue)

### 2. **Vessel Enrichment** (`VesselEnrichmentFunction`)
- Adds vessel master data (type, flag, destination, dimensions)
- Calculates distance from previous position (Haversine formula)
- Calculates time since last update
- Infers vessel type from name (demo mode)
- Production: Async lookup from PostgreSQL/Redis cache

### 3. **Speed Anomaly Detection** (`SpeedAnomalyDetectionFunction`)
- **Excessive Speed**: Speed > 1.5x max for vessel type
  - Cargo: 25 knots max
  - Passenger: 35 knots max
  - Tanker: 20 knots max
- **Sudden Stop**: Speed drops from >10 knots to <1 knot
- **Impossible Acceleration**: >1 knot/sec (likely GPS error)
- Alerts throttled (max 1 per vessel per 5 minutes)

### 4. **Geofencing** (TODO)
- Point-in-polygon detection for defined zones
- Entry/exit event detection
- Restricted area violation alerts

### 5. **Collision Detection** (TODO)
- Spatial join on grid cells
- Haversine distance calculation
- CPA (Closest Point of Approach) prediction
- Risk levels: HIGH (<5 min), MEDIUM (5-10 min), LOW (10-30 min)

## Data Models

### Input: `AISMessage`
```json
{
  "mmsi": 525012345,
  "vesselName": "MV MERATUS JAKARTA",
  "latitude": -6.1055,
  "longitude": 106.8833,
  "speed": 12.5,
  "course": 135.0,
  "heading": 137,
  "status": "Under way using engine",
  "timestamp": "2026-02-28T10:30:15Z"
}
```

### Output: `EnrichedPosition`
```json
{
  "mmsi": 525012345,
  "vesselName": "MV MERATUS JAKARTA",
  "latitude": -6.1055,
  "longitude": 106.8833,
  "speed": 12.5,
  "vesselType": "CARGO",
  "flag": "ID",
  "distanceFromPrevious": 0.25,
  "timeSinceLastUpdate": 10000,
  "validated": true,
  "processedTime": "2026-02-28T10:30:15.123Z"
}
```

### Output: `VesselAlert`
```json
{
  "alertId": "uuid-here",
  "alertType": "EXCESSIVE_SPEED",
  "severity": "HIGH",
  "mmsi": 525012345,
  "vesselName": "MV MERATUS JAKARTA",
  "latitude": -6.1055,
  "longitude": 106.8833,
  "title": "Excessive Speed Detected",
  "description": "Vessel traveling at 40.0 knots (max: 25.0 knots)",
  "actionRequired": true,
  "detectedAt": "2026-02-28T10:30:15Z"
}
```

## Configuration

Configuration loaded from environment variables (see `.env.example`):

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker addresses |
| `KAFKA_INPUT_TOPIC` | `ais-raw-data` | Input topic name |
| `KAFKA_POSITIONS_TOPIC` | `vessel-positions` | Output positions topic |
| `KAFKA_ALERTS_TOPIC` | `vessel-alerts` | Output alerts topic |
| `FLINK_PARALLELISM` | `4` | Number of parallel operators |
| `FLINK_CHECKPOINTING_ENABLED` | `true` | Enable fault tolerance |
| `FLINK_CHECKPOINT_INTERVAL` | `60000` | Checkpoint interval (ms) |
| `FLINK_STATE_BACKEND` | `hashmap` | State backend: `hashmap` or `rocksdb` |

## Build & Run

### Prerequisites
- Java 21
- Maven 3.9+
- Kafka running on `localhost:9092`
- Kafka topics created:
  ```bash
  kafka-topics --create --topic ais-raw-data --bootstrap-server localhost:9092 --partitions 12 --replication-factor 1
  kafka-topics --create --topic vessel-positions --bootstrap-server localhost:9092 --partitions 12 --replication-factor 1
  kafka-topics --create --topic vessel-alerts --bootstrap-server localhost:9092 --partitions 6 --replication-factor 1
  ```

### Build
```bash
# Using Maven
mvn clean package

# Using Nx
nx build stream-processor
```

Creates fat JAR: `target/stream-processor-1.0.0-SNAPSHOT.jar`

### Run Locally
```bash
# Using Maven
mvn exec:java -Dexec.mainClass=com.kreasipositif.vms.processor.StreamProcessorJob

# Using Nx
nx run stream-processor

# With environment variables
source .env
nx run-local stream-processor
```

### Deploy to Flink Cluster
```bash
# Start Flink cluster
$FLINK_HOME/bin/start-cluster.sh

# Submit job
flink run -d target/stream-processor-1.0.0-SNAPSHOT.jar

# Or using Nx
nx deploy-flink stream-processor

# Check job status
flink list

# Cancel job
flink cancel <job-id>
```

## State Management

### State Backend
- **HashMap** (default): In-memory, fast, limited by heap size
- **RocksDB**: Disk-backed, handles large state, slower

For production with large state (100K+ vessels), use RocksDB:
```bash
FLINK_STATE_BACKEND=rocksdb
```

### Checkpointing
- **Interval**: 60 seconds (configurable)
- **Mode**: Exactly-once semantics
- **Storage**: File system (local) or HDFS/S3 (production)
- **Cleanup**: Retained on cancellation for debugging

## Performance

### Throughput
- **Processing**: 500K messages/sec (4 parallel operators)
- **Latency**: ~50ms processing time (p95)
- **State**: 100KB per vessel (RocksDB compressed)

### Scaling
- **Horizontal**: Increase parallelism (match Kafka partitions)
- **Vertical**: Increase memory for state backend

```bash
# Scale to 12 parallel operators (match 12 Kafka partitions)
FLINK_PARALLELISM=12
```

## Monitoring

### Metrics
- Flink Web UI: `http://localhost:8081`
- Prometheus endpoint: `/metrics`
- Key metrics:
  - `numRecordsIn`: Records consumed from Kafka
  - `numRecordsOut`: Records produced to Kafka
  - `checkpointDuration`: Checkpoint completion time
  - `backPressure`: Processing bottlenecks

### Logs
- Log file: `logs/flink-*.log`
- Log level: Configured in `src/main/resources/logback.xml`

## Testing

```bash
# Run unit tests
mvn test

# Run with test coverage
mvn test jacoco:report

# Using Nx
nx test stream-processor
```

## Future Enhancements

- [ ] Geofencing engine with point-in-polygon detection
- [ ] Collision detection with spatial join
- [ ] PostgreSQL async I/O for vessel lookup
- [ ] ClickHouse batch sink for historical data
- [ ] Redis sink for real-time cache
- [ ] Track aggregation with session windows
- [ ] ML-based anomaly detection (drift, unusual patterns)
- [ ] Watermark handling for late data
- [ ] Savepoints for job migration

## Development

### Adding New Alert Types

1. Add enum to `VesselAlert.AlertType`:
```java
public enum AlertType {
    // ... existing types
    NEW_ALERT_TYPE
}
```

2. Create new function extending `KeyedProcessFunction`:
```java
public class NewAnomalyDetectionFunction 
    extends KeyedProcessFunction<Long, EnrichedPosition, EnrichedPosition> {
    // Implementation
}
```

3. Wire into `StreamProcessorJob`:
```java
OutputTag<VesselAlert> newAlertsTag = new OutputTag<>("new-alerts"){};
SingleOutputStreamOperator<EnrichedPosition> checked = enrichedStream
    .keyBy(EnrichedPosition::getMmsi)
    .process(new NewAnomalyDetectionFunction(newAlertsTag));

alertsStream = alertsStream.union(checked.getSideOutput(newAlertsTag));
```

## Architecture References

See [data-flow-documentation.md](../../architecture/data-flow-documentation.md) for complete system architecture.

## License

Proprietary - Kreasipositif VMS Project

## Support

For issues or questions, contact the VMS development team.
