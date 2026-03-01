# Vessel Monitoring System - Data Flow Documentation

## Table of Contents
1. [Overview](#overview)
2. [End-to-End Data Flow](#end-to-end-data-flow)
3. [Phase 1: Data Ingestion](#phase-1-data-ingestion)
4. [Phase 2: Message Streaming with Kafka](#phase-2-message-streaming-with-kafka)
5. [Phase 3: Stream Processing with Apache Flink/Spark](#phase-3-stream-processing-with-apache-flinkspark)
6. [Phase 4: Data Storage Strategy](#phase-4-data-storage-strategy)
7. [Phase 5: Backend Services](#phase-5-backend-services)
8. [Phase 6: Real-time Updates via WebSocket](#phase-6-real-time-updates-via-websocket)
9. [Phase 7: Frontend Rendering](#phase-7-frontend-rendering)
10. [Performance Characteristics](#performance-characteristics)
11. [Data Flow Diagrams](#data-flow-diagrams)

---

## Overview

The Vessel Monitoring System (VMS) implements a **real-time streaming architecture** designed to handle high-velocity maritime data. The system processes vessel positions, weather data, and port information through a multi-stage pipeline, from raw data ingestion to interactive web visualization.

### Key Architecture Principles
- **Event-Driven**: Kafka-based message streaming for decoupled services
- **Real-time Processing**: Apache Flink/Spark for sub-second stream processing
- **Scalable**: Horizontal scaling at every layer
- **Resilient**: Fault-tolerant with message replay capabilities
- **Observable**: Full monitoring and tracing

---

## End-to-End Data Flow

```
┌─────────────────┐
│  Data Sources   │  AIS Providers, Weather APIs, Port APIs
│  (External)     │
└────────┬────────┘
         │ Raw Data (AIS NMEA, JSON)
         ▼
┌─────────────────┐
│ Data Collectors │  Java 21 + Virtual Threads
│   (Ingestion)   │  - AIS Stream Collector
└────────┬────────┘  - Weather Collector
         │           - Port Data Collector
         │ Publish Messages
         ▼
┌─────────────────┐
│  Apache Kafka   │  Message Broker
│  (Streaming)    │  Topics: ais-raw-data, weather-data, port-data
└────────┬────────┘
         │ Subscribe & Consume
         ▼
┌─────────────────┐
│ Apache Flink/   │  Stream Processing Engine
│ Spark Streaming │  - Validation, Filtering, Enrichment
└────────┬────────┘  - Geofencing, Anomaly Detection
         │           - Aggregations, Windowing
         │
         ├─────────┬─────────┬─────────┬─────────┐
         ▼         ▼         ▼         ▼         ▼
    ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
    │PostGIS │ │Click   │ │ Redis  │ │Elastic │ │ Kafka  │
    │(Current│ │House   │ │(Cache) │ │(Search)│ │(Alerts)│
    │ State) │ │(History│ │        │ │        │ │        │
    └────┬───┘ └────┬───┘ └────┬───┘ └────┬───┘ └────┬───┘
         │          │          │          │          │
         └──────────┴──────────┴──────────┴──────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │ Backend Services│  Spring Boot + Virtual Threads
                   │   (REST APIs)   │  - Vessel Service
                   └────────┬────────┘  - Alert Service, etc.
                            │
                            ├──────────┐
                            │          │
                            ▼          ▼
                   ┌─────────────┐ ┌──────────────┐
                   │  WebSocket  │ │  API Gateway │
                   │   Server    │ │              │
                   └──────┬──────┘ └──────┬───────┘
                          │               │
                          │ Push Updates  │ HTTP/REST
                          │               │
                          └───────┬───────┘
                                  │
                                  ▼
                          ┌─────────────────┐
                          │  React Frontend │
                          │  OpenLayers Map │
                          └─────────────────┘
                          
                        🌐 User Browser
```

---

## Phase 1: Data Ingestion

### 1.1 Data Collectors (Java 21 + Virtual Threads)

The **Data Collector Service** is the entry point for all external data sources.

#### Components
```
data-collector-service/
├── AISStreamCollector.java      → Real AIS data from AIS Stream API
├── MockAISCollector.java         → Mock AIS data (16 Indonesian vessels)
├── WeatherCollector.java         → OpenWeatherMap API
├── MockPortDataCollector.java    → Mock port operations data
└── CollectorOrchestrationService → Scheduling & coordination
```

#### Data Sources

| Collector | Source | Data Type | Frequency | Format |
|-----------|--------|-----------|-----------|--------|
| **AIS Stream** | AIS Stream WebSocket | Vessel positions, speed, course | Real-time (1-5 sec) | JSON |
| **Mock AIS** | Generated | 16 Indonesian vessels | Every 10 sec | JSON |
| **Weather** | OpenWeatherMap API | Temperature, wind, waves | Every 10 sec | JSON |
| **Port Data** | Generated | Port operations (7 ports) | Every 10 sec | JSON |

#### Example: Raw AIS Data Structure
```json
{
  "MessageType": "PositionReport",
  "MetaData": {
    "MMSI": 525012345,
    "ShipName": "MV MERATUS JAKARTA",
    "time_utc": "2026-02-28T10:30:15Z"
  },
  "Message": {
    "PositionReport": {
      "Latitude": -6.1055,
      "Longitude": 106.8833,
      "Sog": 12.5,  // Speed Over Ground (knots)
      "Cog": 135.0, // Course Over Ground (degrees)
      "TrueHeading": 137,
      "NavigationalStatus": 0 // Under way using engine
    }
  }
}
```

#### Java 21 Virtual Threads Benefits
- **High Concurrency**: Handle 100K+ AIS messages/sec with low memory
- **Efficient I/O**: Non-blocking operations without callback hell
- **Simple Code**: Synchronous-style code with async performance
- **Resource Efficient**: 1 million virtual threads ≈ 1GB RAM

```java
// Virtual Thread executor configuration
@Bean
public Executor taskExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

### 1.2 Data Validation & Normalization

Before publishing to Kafka, collectors perform:

1. **Schema Validation**: Ensure required fields exist
2. **Data Normalization**: Convert to standard format
3. **Coordinate Validation**: Lat/Lon within valid ranges
4. **Timestamp Enrichment**: Add collection timestamp
5. **Metadata Addition**: Source, collector ID, version

---

## Phase 2: Message Streaming with Kafka

### 2.1 Apache Kafka Architecture

Kafka acts as the **central nervous system** of VMS, decoupling data producers from consumers.

#### Kafka Topics

| Topic | Purpose | Retention | Partitions | Replication |
|-------|---------|-----------|------------|-------------|
| **ais-raw-data** | Raw vessel positions | 7 days | 12 | 3 |
| **vessel-positions** | Validated & enriched positions | 3 days | 12 | 3 |
| **vessel-alerts** | Geofence violations, anomalies | 30 days | 6 | 3 |
| **weather-data** | Weather conditions | 7 days | 4 | 2 |
| **port-data** | Port operations | 7 days | 4 | 2 |

#### Message Format Example (ais-raw-data topic)
```json
{
  "key": "525012345",  // MMSI as partition key
  "timestamp": 1709114415000,
  "headers": {
    "source": "mock-ais-collector",
    "collector_id": "collector-001",
    "schema_version": "1.0"
  },
  "value": {
    "mmsi": 525012345,
    "vessel_name": "MV MERATUS JAKARTA",
    "latitude": -6.1055,
    "longitude": 106.8833,
    "speed": 12.5,
    "course": 135.0,
    "heading": 137,
    "status": "Under way using engine",
    "timestamp": "2026-02-28T10:30:15Z",
    "collection_time": "2026-02-28T10:30:15.123Z"
  }
}
```

#### Partitioning Strategy

- **Key**: MMSI (Maritime Mobile Service Identity)
- **Purpose**: Ensure all messages for same vessel go to same partition
- **Benefit**: Maintains message order per vessel for accurate tracking

### 2.2 Kafka Configuration

```yaml
kafka:
  bootstrap-servers: localhost:9092
  producer:
    key-serializer: org.apache.kafka.common.serialization.StringSerializer
    value-serializer: org.apache.kafka.common.serialization.StringSerializer
    acks: all  # Wait for all replicas
    retries: 3
    compression-type: snappy  # 2-5x compression
    batch-size: 16384
    linger-ms: 10  # Small batching for low latency
  
  consumer:
    group-id: flink-processors
    auto-offset-reset: earliest
    enable-auto-commit: false  # Manual commit for exactly-once
    max-poll-records: 500
```

### 2.3 Benefits of Kafka in VMS

1. **Decoupling**: Data collectors don't know about stream processors
2. **Replay**: Reprocess historical data after fixing bugs
3. **Multi-Consumer**: Flink, monitoring tools, audit systems all consume
4. **Buffering**: Handles traffic spikes without data loss
5. **Durability**: Replicated across brokers for fault tolerance

---

## Phase 3: Stream Processing with Apache Flink/Spark

This is the **heart of the system** where raw data becomes actionable intelligence.

### 3.1 Why Apache Flink?

| Aspect | Apache Flink | Apache Spark Streaming |
|--------|--------------|------------------------|
| **Processing Model** | True streaming | Micro-batching |
| **Latency** | <100ms | 500ms-2sec |
| **Stateful Operations** | Native, efficient | Possible but heavier |
| **Exactly-Once** | Native support | Requires tuning |
| **Use Case Fit** | ✅ **Best for VMS** | Good for analytics |

**Recommendation**: Use **Apache Flink** for VMS due to real-time requirements.

### 3.2 Flink Processing Pipeline

#### Stream Processing Jobs

```
┌─────────────────────────────────────────────────────────────┐
│                  Apache Flink Job: VMS Processor             │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐│
│  │  Kafka       │────▶│  Validate &  │────▶│   Enrich     ││
│  │  Source      │     │  Filter      │     │  (Vessel DB) ││
│  │(ais-raw-data)│     └──────────────┘     └──────────────┘│
│  └──────────────┘                                           │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────────────────────────────────────┐          │
│  │  Parallel Processing (12 partitions)         │          │
│  ├──────────────────────────────────────────────┤          │
│  │ • Position Validation                        │          │
│  │ • Geofencing Check                           │          │
│  │ • Speed Anomaly Detection                    │          │
│  │ • Collision Detection (spatial join)         │          │
│  │ • Track Aggregation (session windows)        │          │
│  └──────────────────────────────────────────────┘          │
│         │                                                    │
│         ├─────────┬─────────┬─────────┬─────────┐          │
│         ▼         ▼         ▼         ▼         ▼          │
│   ┌─────────┐┌─────────┐┌────────┐┌────────┐┌────────┐   │
│   │PostGIS  ││ClickHouse││ Redis  ││Elastic ││ Kafka  │   │
│   │ Sink    ││  Sink   ││  Sink  ││  Sink  ││  Sink  │   │
│   └─────────┘└─────────┘└────────┘└────────┘└────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 Processing Steps Detailed

#### Step 1: Position Validation
```java
// Pseudo Flink code
DataStream<AISMessage> validatedStream = rawStream
    .filter(msg -> {
        // Validate coordinates
        return msg.latitude >= -90 && msg.latitude <= 90 &&
               msg.longitude >= -180 && msg.longitude <= 180;
    })
    .filter(msg -> msg.speed >= 0 && msg.speed <= 102.3) // Max vessel speed
    .filter(msg -> msg.mmsi > 0); // Valid MMSI
```

**Filters Applied**:
- Latitude: -90 to 90
- Longitude: -180 to 180
- Speed: 0-102.3 knots (realistic maritime range)
- MMSI: Valid 9-digit identifier
- Timestamp: Not in future, not too old (>1 hour)

#### Step 2: Vessel Data Enrichment
```java
// Enrich with vessel master data from PostgreSQL
DataStream<EnrichedPosition> enrichedStream = validatedStream
    .keyBy(msg -> msg.mmsi)
    .map(new AsyncDataStreamFunction<AISMessage, EnrichedPosition>() {
        @Override
        public void asyncInvoke(AISMessage msg, ResultFuture<EnrichedPosition> resultFuture) {
            // Async lookup from PostgreSQL or Redis cache
            vesselService.getVesselInfo(msg.mmsi).thenAccept(vessel -> {
                EnrichedPosition enriched = new EnrichedPosition(
                    msg.mmsi,
                    msg.latitude,
                    msg.longitude,
                    msg.speed,
                    msg.course,
                    vessel.vesselType,      // Added
                    vessel.flag,            // Added
                    vessel.destination,     // Added
                    vessel.eta,             // Added
                    vessel.draught          // Added
                );
                resultFuture.complete(Collections.singleton(enriched));
            });
        }
    });
```

**Enrichment Data Added**:
- Vessel Type (Cargo, Tanker, Passenger, etc.)
- Flag Country
- Destination Port
- ETA (Estimated Time of Arrival)
- Draught (vessel depth in water)
- IMO Number
- Call Sign

#### Step 3: Geofencing Engine
```java
// Check if vessel entered/exited defined geofences
DataStream<GeofenceEvent> geofenceAlerts = enrichedStream
    .keyBy(msg -> msg.mmsi)
    .process(new GeofenceCheckFunction());

class GeofenceCheckFunction extends KeyedProcessFunction<Long, EnrichedPosition, GeofenceEvent> {
    
    private ValueState<String> lastGeofenceState;
    private ListState<Geofence> activeGeofences;
    
    @Override
    public void processElement(EnrichedPosition position, Context ctx, Collector<GeofenceEvent> out) {
        // Load geofences from state (cached from PostgreSQL)
        List<Geofence> geofences = Lists.newArrayList(activeGeofences.get());
        
        // Check point-in-polygon for each geofence
        for (Geofence fence : geofences) {
            boolean inside = isPointInPolygon(position.latitude, position.longitude, fence.polygon);
            String lastState = lastGeofenceState.value();
            
            // Detect entry/exit events
            if (inside && !"INSIDE".equals(lastState)) {
                out.collect(new GeofenceEvent(
                    position.mmsi,
                    fence.id,
                    "ENTRY",
                    position.timestamp
                ));
                lastGeofenceState.update("INSIDE");
            } else if (!inside && "INSIDE".equals(lastState)) {
                out.collect(new GeofenceEvent(
                    position.mmsi,
                    fence.id,
                    "EXIT",
                    position.timestamp
                ));
                lastGeofenceState.update("OUTSIDE");
            }
        }
    }
}
```

**Geofence Types**:
- **Port Areas**: Detect vessel arrival/departure
- **Restricted Zones**: Military areas, protected waters
- **Fishing Zones**: Monitor fishing vessel compliance
- **Territorial Waters**: Track border crossings
- **Custom Areas**: User-defined monitoring zones

#### Step 4: Speed Anomaly Detection
```java
// Detect unusual speed patterns
DataStream<Alert> speedAlerts = enrichedStream
    .keyBy(msg -> msg.mmsi)
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new SpeedAnomalyAggregator());

class SpeedAnomalyAggregator implements AggregateFunction<EnrichedPosition, SpeedStats, Alert> {
    
    @Override
    public Alert getResult(SpeedStats acc) {
        // Detect anomalies
        if (acc.maxSpeed > acc.expectedMaxSpeed * 1.5) {
            return new Alert("EXCESSIVE_SPEED", acc.mmsi, acc.maxSpeed);
        }
        if (acc.speedVariance > acc.threshold) {
            return new Alert("ERRATIC_SPEED", acc.mmsi, acc.speedVariance);
        }
        // Sudden stop
        if (acc.prevSpeed > 10 && acc.currentSpeed < 1) {
            return new Alert("SUDDEN_STOP", acc.mmsi, acc.currentSpeed);
        }
        return null;
    }
}
```

**Anomaly Detection Rules**:
- **Excessive Speed**: >1.5x normal for vessel type
- **Erratic Speed**: High variance in 5-min window
- **Sudden Stop**: Speed drops >10 knots instantly
- **Drift**: Movement without engine (speed vs. course mismatch)
- **Impossible Speed**: Acceleration beyond physical limits

#### Step 5: Collision Detection
```java
// Spatial join to detect vessels in proximity
DataStream<Alert> collisionAlerts = enrichedStream
    .keyBy(msg -> gridCell(msg.latitude, msg.longitude)) // Spatial partitioning
    .window(SlidingEventTimeWindows.of(Time.seconds(30), Time.seconds(10)))
    .process(new ProximityCheckFunction());

class ProximityCheckFunction extends ProcessWindowFunction<EnrichedPosition, Alert, String, TimeWindow> {
    
    @Override
    public void process(String gridCell, Context ctx, Iterable<EnrichedPosition> positions, Collector<Alert> out) {
        List<EnrichedPosition> vessels = Lists.newArrayList(positions);
        
        // Check all pairs in this grid cell
        for (int i = 0; i < vessels.size(); i++) {
            for (int j = i + 1; j < vessels.size(); j++) {
                EnrichedPosition v1 = vessels.get(i);
                EnrichedPosition v2 = vessels.get(j);
                
                double distance = haversineDistance(v1, v2); // nautical miles
                
                if (distance < 0.5) { // Within 0.5 nautical miles (926 meters)
                    // Calculate CPA (Closest Point of Approach)
                    double timeToCollision = calculateCPA(v1, v2);
                    
                    if (timeToCollision < 600) { // Less than 10 minutes
                        out.collect(new Alert(
                            "COLLISION_RISK",
                            Arrays.asList(v1.mmsi, v2.mmsi),
                            distance,
                            timeToCollision
                        ));
                    }
                }
            }
        }
    }
}
```

**Collision Detection Features**:
- **Spatial Indexing**: Grid-based partitioning for efficiency
- **Distance Calculation**: Haversine formula for accuracy
- **CPA Calculation**: Predict closest approach time/distance
- **Risk Levels**: High (<5 min), Medium (5-10 min), Low (10-30 min)
- **Alert Throttling**: Avoid spam for same vessel pair

#### Step 6: Track Aggregation & Smoothing
```java
// Aggregate positions into tracks (session windows)
DataStream<VesselTrack> tracks = enrichedStream
    .keyBy(msg -> msg.mmsi)
    .window(SessionWindows.withGap(Time.minutes(15))) // New session if gap >15 min
    .aggregate(new TrackAggregator());

class TrackAggregator implements AggregateFunction<EnrichedPosition, TrackAccumulator, VesselTrack> {
    
    @Override
    public VesselTrack getResult(TrackAccumulator acc) {
        // Smooth track using Kalman filter or moving average
        List<Position> smoothed = smoothTrack(acc.positions);
        
        return new VesselTrack(
            acc.mmsi,
            smoothed,
            calculateTotalDistance(smoothed),
            calculateAverageSpeed(smoothed),
            acc.startTime,
            acc.endTime
        );
    }
}
```

**Track Processing**:
- **Session Windows**: Group positions into journeys
- **Smoothing**: Kalman filter to reduce GPS noise
- **Gap Handling**: Fill gaps <5 min with interpolation
- **Compression**: Douglas-Peucker for storage efficiency
- **Statistics**: Distance, duration, avg/max speed per track

### 3.4 Stateful Processing & Checkpointing

Flink maintains state for each vessel to enable:
- **Event Correlation**: Track vessel history (last 24h positions)
- **Pattern Detection**: Identify behavioral patterns over time
- **Geofence Tracking**: Remember which zones vessel is in
- **Alert Deduplication**: Avoid repeated alerts for same event

```java
// Checkpoint configuration
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
env.enableCheckpointing(60000); // Checkpoint every 60 seconds
env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000);
env.getCheckpointConfig().setCheckpointTimeout(600000);
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

// State backend (RocksDB for large state)
env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
env.getCheckpointConfig().setCheckpointStorage("hdfs:///flink/checkpoints");
```

---

## Phase 4: Data Storage Strategy

### 4.1 Multi-Database Architecture

Different databases for different access patterns:

#### PostgreSQL + PostGIS (Operational Data)

**Purpose**: Current vessel state, geofences, user data

**Schema Example**:
```sql
-- Current vessel positions (updated by Flink)
CREATE TABLE vessel_positions (
    mmsi BIGINT PRIMARY KEY,
    vessel_name VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    location GEOMETRY(Point, 4326), -- PostGIS spatial column
    speed DOUBLE PRECISION,
    course DOUBLE PRECISION,
    heading SMALLINT,
    status VARCHAR(50),
    vessel_type VARCHAR(50),
    flag VARCHAR(3),
    destination VARCHAR(100),
    eta TIMESTAMP,
    last_updated TIMESTAMP DEFAULT NOW(),
    
    -- Spatial index
    CONSTRAINT valid_coords CHECK (
        latitude >= -90 AND latitude <= 90 AND
        longitude >= -180 AND longitude <= 180
    )
);

CREATE INDEX idx_vessel_location ON vessel_positions USING GIST (location);
CREATE INDEX idx_vessel_updated ON vessel_positions (last_updated);

-- Geofences
CREATE TABLE geofences (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    polygon GEOMETRY(Polygon, 4326), -- PostGIS polygon
    type VARCHAR(50), -- PORT, RESTRICTED, FISHING, CUSTOM
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_geofence_polygon ON geofences USING GIST (polygon);
```

**Flink Write Pattern**:
```java
// Upsert into PostgreSQL (last position per vessel)
JdbcSink.sink(
    "INSERT INTO vessel_positions (mmsi, latitude, longitude, location, speed, course, ...) " +
    "VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ...) " +
    "ON CONFLICT (mmsi) DO UPDATE SET " +
    "latitude = EXCLUDED.latitude, " +
    "longitude = EXCLUDED.longitude, " +
    "location = EXCLUDED.location, " +
    "speed = EXCLUDED.speed, " +
    "last_updated = NOW()",
    (ps, position) -> {
        ps.setLong(1, position.mmsi);
        ps.setDouble(2, position.latitude);
        ps.setDouble(3, position.longitude);
        ps.setDouble(4, position.longitude); // For ST_MakePoint
        ps.setDouble(5, position.latitude);
        ps.setDouble(6, position.speed);
        // ... more fields
    },
    JdbcExecutionOptions.builder()
        .withBatchSize(1000)
        .withBatchIntervalMs(5000)
        .build(),
    new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
        .withUrl("jdbc:postgresql://localhost:5432/vms")
        .withDriverName("org.postgresql.Driver")
        .build()
);
```

#### ClickHouse (Time-Series Analytics)

**Purpose**: Historical positions, long-term analytics, reporting

**Schema Example**:
```sql
-- Historical positions (billions of records)
CREATE TABLE vessel_position_history (
    mmsi UInt64,
    vessel_name String,
    latitude Float64,
    longitude Float64,
    speed Float32,
    course Float32,
    heading UInt16,
    status String,
    vessel_type String,
    flag String,
    timestamp DateTime,
    date Date DEFAULT toDate(timestamp), -- Partition key
    
    INDEX idx_mmsi mmsi TYPE bloom_filter GRANULARITY 1,
    INDEX idx_vessel_name vessel_name TYPE tokenbf_v1(30000, 2, 0) GRANULARITY 5
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date) -- Monthly partitions
ORDER BY (mmsi, timestamp)
SETTINGS index_granularity = 8192;

-- Materialized view for aggregations
CREATE MATERIALIZED VIEW vessel_daily_stats
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (mmsi, date)
AS SELECT
    mmsi,
    toDate(timestamp) as date,
    count() as position_count,
    avg(speed) as avg_speed,
    max(speed) as max_speed,
    uniqExact(toString(latitude) || ',' || toString(longitude)) as unique_positions
FROM vessel_position_history
GROUP BY mmsi, date;
```

**Why ClickHouse?**
- **Compression**: 100:1 ratio (1TB data → 10GB storage)
- **Speed**: Query billions of records in 1-2 seconds
- **Columnar**: Only read columns needed for query
- **Partitioning**: Prune old data easily (drop partition vs. DELETE)

**Flink Write Pattern**:
```java
// Append-only writes to ClickHouse
ClickHouseSink.addSink(enrichedStream)
    .withFlushInterval(5000) // Batch writes every 5 seconds
    .withMaxRetries(3)
    .withDatabase("vms")
    .withTable("vessel_position_history");
```

#### Redis (Cache & Real-Time)

**Purpose**: Latest positions cache, rate limiting, session management

**Data Structures**:
```redis
# Latest position per vessel (Hash)
HSET vessel:525012345 
    latitude -6.1055 
    longitude 106.8833 
    speed 12.5 
    course 135.0 
    timestamp 1709114415000

EXPIRE vessel:525012345 3600  # TTL 1 hour

# Geofence membership (Set)
SADD geofence:port-jakarta:vessels 525012345 525067890

# Real-time statistics (String)
SET stats:total_vessels 1547
SET stats:vessels_in_port 234
SET stats:vessels_under_way 1313

# Pub/Sub for real-time updates
PUBLISH vessel:positions '{"mmsi":525012345, "lat":-6.1055, "lon":106.8833}'
```

**Flink Write Pattern**:
```java
// Write to Redis for low-latency access
enrichedStream.addSink(new RedisSink<>(
    new FlinkJedisPoolConfig.Builder()
        .setHost("localhost")
        .setPort(6379)
        .setMaxTotal(100)
        .build(),
    new RedisMapper<EnrichedPosition>() {
        @Override
        public String getKeyFromData(EnrichedPosition position) {
            return "vessel:" + position.mmsi;
        }
        
        @Override
        public String getValueFromData(EnrichedPosition position) {
            return toJson(position);
        }
    }
));
```

#### Elasticsearch (Search & Logs)

**Purpose**: Full-text search for vessels, application logs

**Index Mapping**:
```json
PUT /vessels
{
  "mappings": {
    "properties": {
      "mmsi": { "type": "long" },
      "vessel_name": { 
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "imo": { "type": "keyword" },
      "call_sign": { "type": "keyword" },
      "vessel_type": { "type": "keyword" },
      "flag": { "type": "keyword" },
      "location": { "type": "geo_point" },
      "last_updated": { "type": "date" }
    }
  }
}
```

**Search Examples**:
```json
# Search by vessel name
GET /vessels/_search
{
  "query": {
    "match": {
      "vessel_name": "meratus"
    }
  }
}

# Geospatial search (vessels within 50km of Jakarta)
GET /vessels/_search
{
  "query": {
    "geo_distance": {
      "distance": "50km",
      "location": {
        "lat": -6.1055,
        "lon": 106.8833
      }
    }
  }
}
```

---

## Phase 5: Backend Services

### 5.1 Spring Boot Microservices (Java 21)

After Flink processes data and stores it, **backend services** provide APIs for frontend consumption.

#### Service Architecture

```
┌─────────────────────────────────────────────────────────┐
│              Spring Cloud Gateway                        │
│         (API Gateway + Load Balancer)                    │
└────────────────┬────────────────────────────────────────┘
                 │
     ┌───────────┼───────────┬───────────┬────────────┐
     ▼           ▼           ▼           ▼            ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│ Vessel  │ │ Alert   │ │Analytics│ │ History │ │  User   │
│ Service │ │ Service │ │ Service │ │ Service │ │ Service │
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
     │           │           │           │           │
     └───────────┴───────────┴───────────┴───────────┘
                          │
                    ┌─────┴─────┐
                    │           │
                    ▼           ▼
              ┌──────────┐ ┌──────────┐
              │PostgreSQL│ │ClickHouse│
              │  Redis   │ │  Elastic │
              └──────────┘ └──────────┘
```

#### Example: Vessel Service API

```java
@RestController
@RequestMapping("/api/v1/vessels")
@RequiredArgsConstructor
public class VesselController {
    
    private final VesselService vesselService;
    
    // Get current positions of all vessels
    @GetMapping("/positions")
    public ResponseEntity<List<VesselPosition>> getCurrentPositions(
        @RequestParam(required = false) BoundingBox bounds,
        @RequestParam(defaultValue = "1000") int limit
    ) {
        // Read from Redis cache (fast) or PostgreSQL (fallback)
        List<VesselPosition> positions = vesselService.getCurrentPositions(bounds, limit);
        return ResponseEntity.ok(positions);
    }
    
    // Get vessel details by MMSI
    @GetMapping("/{mmsi}")
    public ResponseEntity<VesselDetails> getVessel(@PathVariable Long mmsi) {
        return vesselService.getVesselByMMSI(mmsi)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    // Get vessel track history
    @GetMapping("/{mmsi}/track")
    public ResponseEntity<VesselTrack> getTrack(
        @PathVariable Long mmsi,
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant from,
        @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant to
    ) {
        // Query ClickHouse for historical positions
        VesselTrack track = vesselService.getTrack(mmsi, from, to);
        return ResponseEntity.ok(track);
    }
    
    // Search vessels
    @GetMapping("/search")
    public ResponseEntity<List<VesselSearchResult>> searchVessels(
        @RequestParam String query
    ) {
        // Query Elasticsearch for full-text search
        List<VesselSearchResult> results = vesselService.searchVessels(query);
        return ResponseEntity.ok(results);
    }
}
```

#### Virtual Threads Configuration

```java
@Configuration
public class VirtualThreadConfig {
    
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
    
    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        TaskExecutorAdapter adapter = new TaskExecutorAdapter(
            Executors.newVirtualThreadPerTaskExecutor()
        );
        adapter.setTaskDecorator(new ContextCopyingDecorator());
        return adapter;
    }
}
```

**Benefits**:
- Handle 10K+ concurrent requests without traditional thread pool limits
- Simplified async code (no CompletableFuture complexity)
- Lower memory footprint vs. traditional threads

---

## Phase 6: Real-time Updates via WebSocket

### 6.1 WebSocket Server (Spring WebFlux)

For **real-time position updates** to frontend without polling.

```java
@Component
public class VesselPositionWebSocketHandler implements WebSocketHandler {
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final RedisMessageListenerContainer listenerContainer;
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        // Subscribe to Redis Pub/Sub for vessel position updates
        MessageListener listener = (message, pattern) -> {
            String positionJson = new String(message.getBody());
            session.send(Mono.just(session.textMessage(positionJson)))
                .subscribe();
        };
        
        listenerContainer.addMessageListener(listener, 
            new PatternTopic("vessel:positions"));
        
        sessions.put(session.getId(), session);
        
        return session.receive()
            .doOnTerminate(() -> {
                sessions.remove(session.getId());
                listenerContainer.removeMessageListener(listener);
            })
            .then();
    }
}
```

### 6.2 Data Flow: Flink → Redis → WebSocket → Browser

```
Flink Process
    │
    ├─ Write to PostgreSQL (current state)
    ├─ Write to ClickHouse (history)
    └─ Write to Redis + PUBLISH
              │
              ▼
       Redis Pub/Sub: PUBLISH vessel:positions {...}
              │
              ▼
       WebSocket Server subscribes to channel
              │
              ▼
       Broadcast to all connected clients
              │
              ▼
       Browser receives update (1-2 sec latency)
              │
              ▼
       React updates state → Map re-renders
```

### 6.3 WebSocket Message Format

```json
{
  "type": "position_update",
  "timestamp": "2026-02-28T10:30:15.123Z",
  "vessels": [
    {
      "mmsi": 525012345,
      "vessel_name": "MV MERATUS JAKARTA",
      "latitude": -6.1055,
      "longitude": 106.8833,
      "speed": 12.5,
      "course": 135.0,
      "heading": 137,
      "status": "Under way using engine"
    },
    {
      "mmsi": 525067890,
      "vessel_name": "MS PELNI EXPRESS",
      "latitude": -6.89,
      "longitude": 107.61,
      "speed": 18.3,
      "course": 225.0,
      "heading": 223,
      "status": "Under way using engine"
    }
  ]
}
```

### 6.4 Alert Broadcasting

```json
{
  "type": "alert",
  "severity": "HIGH",
  "alert_type": "GEOFENCE_VIOLATION",
  "timestamp": "2026-02-28T10:35:00Z",
  "vessel": {
    "mmsi": 525012345,
    "vessel_name": "MV MERATUS JAKARTA"
  },
  "details": {
    "geofence_name": "Restricted Military Zone",
    "geofence_id": 42,
    "event_type": "ENTRY",
    "location": {
      "latitude": -5.123,
      "longitude": 105.456
    }
  },
  "action_required": true
}
```

---

## Phase 7: Frontend Rendering

### 7.1 React Application Architecture

```
┌────────────────────────────────────────────────────┐
│                 React Application                  │
├────────────────────────────────────────────────────┤
│                                                     │
│  ┌──────────────┐           ┌─────────────────┐   │
│  │  Dashboard   │           │   Map Component │   │
│  │  Component   │◄─────────►│  (OpenLayers)   │   │
│  └──────────────┘           └────────┬────────┘   │
│         │                             │            │
│         │                             │            │
│  ┌──────┴───────────┐                │            │
│  │  Redux Store     │                │            │
│  │  - vessels       │◄───────────────┘            │
│  │  - alerts        │                             │
│  │  - filters       │                             │
│  └──────┬───────────┘                             │
│         │                                          │
│  ┌──────┴───────────────────────────────────┐    │
│  │         React Query / SWR                 │    │
│  │  - API calls with caching                │    │
│  │  - Optimistic updates                    │    │
│  └──────┬───────────────────────────────────┘    │
│         │                                          │
│  ┌──────┴───────────┐                             │
│  │  WebSocket       │                             │
│  │  Client          │                             │
│  │  - Real-time     │                             │
│  │  - Reconnection  │                             │
│  └──────────────────┘                             │
└────────────────────────────────────────────────────┘
         │                    │
         │ HTTP/REST          │ WebSocket
         ▼                    ▼
    ┌─────────────────────────────────┐
    │     API Gateway / Backend       │
    └─────────────────────────────────┘
```

### 7.2 Map Rendering with OpenLayers

```typescript
// Map component with real-time vessel rendering
import { Map, View } from 'ol';
import { fromLonLat } from 'ol/proj';
import { Vector as VectorLayer } from 'ol/layer';
import { Vector as VectorSource } from 'ol/source';
import { Point } from 'ol/geom';
import { Feature } from 'ol';
import { Style, Icon } from 'ol/style';

const VesselMap: React.FC = () => {
  const mapRef = useRef<HTMLDivElement>(null);
  const [map, setMap] = useState<Map | null>(null);
  const [vesselLayer, setVesselLayer] = useState<VectorLayer | null>(null);
  
  // Initialize map
  useEffect(() => {
    if (!mapRef.current) return;
    
    const vectorSource = new VectorSource();
    const vectorLayer = new VectorLayer({
      source: vectorSource,
      style: (feature) => {
        const vessel = feature.get('vessel');
        return new Style({
          image: new Icon({
            src: getVesselIcon(vessel.type, vessel.status),
            rotation: (vessel.heading * Math.PI) / 180, // Rotate icon
            scale: 0.8
          })
        });
      }
    });
    
    const map = new Map({
      target: mapRef.current,
      layers: [
        new TileLayer({
          source: new OSM() // OpenStreetMap tiles
        }),
        vectorLayer
      ],
      view: new View({
        center: fromLonLat([106.8833, -6.1055]), // Jakarta
        zoom: 10
      })
    });
    
    setMap(map);
    setVesselLayer(vectorLayer);
    
    return () => map.setTarget(undefined);
  }, []);
  
  // WebSocket connection for real-time updates
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:8080/ws/vessels');
    
    ws.onmessage = (event) => {
      const update = JSON.parse(event.data);
      
      if (update.type === 'position_update') {
        updateVessels(update.vessels);
      }
    };
    
    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      // Implement reconnection logic
    };
    
    return () => ws.close();
  }, []);
  
  // Update vessel positions on map
  const updateVessels = (vessels: VesselPosition[]) => {
    if (!vesselLayer) return;
    
    const source = vesselLayer.getSource();
    if (!source) return;
    
    vessels.forEach(vessel => {
      // Find existing feature or create new
      let feature = source.getFeatureById(vessel.mmsi);
      
      if (!feature) {
        feature = new Feature({
          geometry: new Point(fromLonLat([vessel.longitude, vessel.latitude])),
          vessel: vessel
        });
        feature.setId(vessel.mmsi);
        source.addFeature(feature);
      } else {
        // Animate position change
        const geometry = feature.getGeometry() as Point;
        const oldCoords = geometry.getCoordinates();
        const newCoords = fromLonLat([vessel.longitude, vessel.latitude]);
        
        animateVessel(feature, oldCoords, newCoords, 1000); // 1 sec animation
        feature.set('vessel', vessel);
      }
    });
  };
  
  // Smooth animation between positions
  const animateVessel = (feature: Feature, from: number[], to: number[], duration: number) => {
    const start = Date.now();
    
    const animate = () => {
      const elapsed = Date.now() - start;
      const progress = Math.min(elapsed / duration, 1);
      
      const x = from[0] + (to[0] - from[0]) * progress;
      const y = from[1] + (to[1] - from[1]) * progress;
      
      const geometry = feature.getGeometry() as Point;
      geometry.setCoordinates([x, y]);
      
      if (progress < 1) {
        requestAnimationFrame(animate);
      }
    };
    
    animate();
  };
  
  return <div ref={mapRef} style={{ width: '100%', height: '100vh' }} />;
};
```

### 7.3 Performance Optimizations

#### Marker Clustering (1000+ vessels)
```typescript
import { Cluster } from 'ol/source';

const clusterSource = new Cluster({
  distance: 40, // pixels
  source: vectorSource
});

const clusterLayer = new VectorLayer({
  source: clusterSource,
  style: (feature) => {
    const size = feature.get('features').length;
    
    if (size > 1) {
      // Show cluster icon with count
      return new Style({
        image: new CircleStyle({
          radius: 15,
          fill: new Fill({ color: 'rgba(0, 229, 255, 0.6)' }),
          stroke: new Stroke({ color: '#00E5FF', width: 2 })
        }),
        text: new Text({
          text: size.toString(),
          fill: new Fill({ color: '#fff' })
        })
      });
    } else {
      // Show single vessel icon
      return vesselStyle(feature.get('features')[0]);
    }
  }
});
```

#### Viewport-based Loading
```typescript
// Only fetch vessels visible in current viewport
const loadVisibleVessels = () => {
  const extent = map.getView().calculateExtent();
  const [minX, minY, maxX, maxY] = extent;
  
  // Convert to lon/lat
  const bottomLeft = toLonLat([minX, minY]);
  const topRight = toLonLat([maxX, maxY]);
  
  const bounds = {
    minLon: bottomLeft[0],
    minLat: bottomLeft[1],
    maxLon: topRight[0],
    maxLat: topRight[1]
  };
  
  // Fetch only vessels in viewport
  fetch(`/api/v1/vessels/positions?bounds=${JSON.stringify(bounds)}`)
    .then(res => res.json())
    .then(vessels => updateVessels(vessels));
};

// Load on map move/zoom
map.on('moveend', debounce(loadVisibleVessels, 500));
```

#### Virtual Scrolling for Vessel List
```typescript
import { FixedSizeList } from 'react-window';

const VesselList: React.FC<{ vessels: Vessel[] }> = ({ vessels }) => {
  return (
    <FixedSizeList
      height={600}
      itemCount={vessels.length}
      itemSize={60}
      width="100%"
    >
      {({ index, style }) => (
        <div style={style}>
          <VesselListItem vessel={vessels[index]} />
        </div>
      )}
    </FixedSizeList>
  );
};
```

---

## Phase 8: Data Flow Summary

### 8.1 Complete Flow Timeline

| Time | Phase | Component | Action |
|------|-------|-----------|--------|
| T+0ms | **Ingestion** | Data Collector | Receive AIS message from provider |
| T+10ms | **Ingestion** | Data Collector | Validate & normalize message |
| T+20ms | **Streaming** | Kafka Producer | Publish to `ais-raw-data` topic |
| T+30ms | **Streaming** | Kafka | Store message (replicated to 3 brokers) |
| T+40ms | **Processing** | Flink Consumer | Consume message from Kafka |
| T+50ms | **Processing** | Flink | Validate position, enrich with vessel data |
| T+70ms | **Processing** | Flink | Check geofences (spatial query) |
| T+80ms | **Processing** | Flink | Detect anomalies (speed, collision) |
| T+100ms | **Storage** | PostgreSQL | Upsert current position |
| T+110ms | **Storage** | ClickHouse | Append to history table |
| T+120ms | **Storage** | Redis | Update cache + PUBLISH to Pub/Sub |
| T+130ms | **Backend** | WebSocket Server | Receive Redis Pub/Sub message |
| T+140ms | **Backend** | WebSocket Server | Broadcast to connected clients |
| T+150ms | **Frontend** | Browser | Receive WebSocket message |
| T+160ms | **Frontend** | React | Update Redux store |
| T+170ms | **Frontend** | OpenLayers | Re-render vessel on map |
| **Total** | **~170ms** | **End-to-End** | From data source to user screen |

### 8.2 Throughput Capacity

| Component | Throughput | Notes |
|-----------|------------|-------|
| **Data Collectors** | 100K msg/sec | Java 21 Virtual Threads |
| **Kafka** | 1M msg/sec | 12 partitions, 3 brokers |
| **Flink** | 500K msg/sec | 48 task slots (12 partitions × 4 operators) |
| **PostgreSQL** | 10K writes/sec | Batch upserts (1000 records/batch) |
| **ClickHouse** | 100K writes/sec | Batch inserts, async |
| **Redis** | 100K writes/sec | In-memory, very fast |
| **WebSocket** | 10K updates/sec | Broadcast to 10K concurrent users |

---

## Performance Characteristics

### Latency Breakdown

```
┌─────────────────────────────────────────────────────────────┐
│              End-to-End Latency: ~170ms                      │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  Data Source → Collector:     10ms  ████                     │
│  Collector → Kafka:           10ms  ████                     │
│  Kafka Storage:               10ms  ████                     │
│  Kafka → Flink:               10ms  ████                     │
│  Flink Processing:            60ms  ████████████████████     │
│  Flink → Databases:           20ms  ████████                 │
│  Database → WebSocket:        10ms  ████                     │
│  WebSocket → Browser:         10ms  ████                     │
│  Browser Rendering:           30ms  ████████████             │
│                                                               │
│  Total: 170ms                                                 │
└─────────────────────────────────────────────────────────────┘
```

### Scalability Metrics

| Metric | Current Demo | Production Target |
|--------|--------------|-------------------|
| **Vessels Tracked** | 16 (mock) | 100,000+ |
| **Position Updates/sec** | 16 / 10 = 1.6 | 10,000+ |
| **Historical Records** | <1M | 10 billion+ |
| **Concurrent Web Users** | N/A | 10,000+ |
| **End-to-End Latency** | ~200ms | <500ms (p95) |
| **WebSocket Update Rate** | 1-5 sec | 1-2 sec |
| **Map Render Time** | <50ms | <100ms |
| **Query Response Time** | <100ms | <500ms (p95) |

---

## Data Flow Diagrams

### Diagram 1: Ingestion to Storage
```
┌─────────────┐
│ AIS Stream  │
│   Source    │
└──────┬──────┘
       │ JSON (AIS messages)
       ▼
┌─────────────────────┐
│  Data Collector     │
│  (Java 21 Virtual   │
│   Threads)          │
│                     │
│  • Validate         │
│  • Normalize        │
│  • Add metadata     │
└──────┬──────────────┘
       │ Kafka message
       ▼
┌─────────────────────┐
│   Apache Kafka      │
│                     │
│  Topic:             │
│  ais-raw-data       │
│                     │
│  Partitions: 12     │
│  Replication: 3     │
└──────┬──────────────┘
       │ Flink consumes
       ▼
┌─────────────────────────────────────────────┐
│         Apache Flink Stream Processor        │
│                                              │
│  ┌──────────────┐  ┌──────────────┐         │
│  │  Validate    │→ │   Enrich     │         │
│  │  Position    │  │(Vessel Master)│         │
│  └──────────────┘  └──────────────┘         │
│         ↓                                    │
│  ┌──────────────┐  ┌──────────────┐         │
│  │  Geofence    │  │   Anomaly    │         │
│  │   Check      │  │  Detection   │         │
│  └──────────────┘  └──────────────┘         │
└──────┬────────────────┬─────────────────────┘
       │                │
       │ Write          │ Write
       ▼                ▼
┌─────────────┐   ┌─────────────┐
│ PostgreSQL  │   │ ClickHouse  │
│  (Current   │   │  (History)  │
│   State)    │   │             │
└─────────────┘   └─────────────┘
```

### Diagram 2: Real-Time Updates Flow
```
┌─────────────┐
│   Flink     │
│ Processor   │
└──────┬──────┘
       │ Write + PUBLISH
       ▼
┌─────────────────────┐
│       Redis         │
│                     │
│  HSET vessel:MMSI   │←─ Cache latest position
│  PUBLISH channel    │←─ Pub/Sub notification
└──────┬──────────────┘
       │ Subscribe
       ▼
┌─────────────────────┐
│  WebSocket Server   │
│  (Spring WebFlux)   │
│                     │
│  • Redis Listener   │
│  • Session Manager  │
│  • Broadcaster      │
└──────┬──────────────┘
       │ WebSocket push
       ▼
┌─────────────────────┐
│  Browser (React)    │
│                     │
│  • WebSocket Client │
│  • Redux Store      │
│  • OpenLayers Map   │
└─────────────────────┘
```

### Diagram 3: Query Flow (Historical Data)
```
┌─────────────┐
│   Browser   │
│   (User)    │
└──────┬──────┘
       │ HTTP GET /api/vessels/123/track?from=...&to=...
       ▼
┌─────────────────────┐
│   API Gateway       │
│ (Spring Cloud)      │
└──────┬──────────────┘
       │ Route to service
       ▼
┌─────────────────────┐
│  History Service    │
│  (Spring Boot)      │
│                     │
│  • Parse request    │
│  • Query ClickHouse │
│  • Format response  │
└──────┬──────────────┘
       │ SQL Query
       ▼
┌─────────────────────┐
│    ClickHouse       │
│                     │
│  SELECT * FROM      │
│  vessel_position_   │
│  history            │
│  WHERE mmsi=123     │
│  AND timestamp      │
│  BETWEEN ...        │
└──────┬──────────────┘
       │ Result (compressed)
       ▼
┌─────────────────────┐
│  History Service    │
│  • Decompress       │
│  • Apply filters    │
│  • Compress track   │←─ Douglas-Peucker algorithm
└──────┬──────────────┘
       │ JSON response
       ▼
┌─────────────────────┐
│   Browser (React)   │
│  • Render track     │
│  • Animate playback │
└─────────────────────┘
```

---

## Conclusion

The Vessel Monitoring System implements a **modern, cloud-native streaming architecture** optimized for real-time maritime data processing:

1. **Java 21 Virtual Threads** enable high-concurrency data collection
2. **Apache Kafka** decouples components and enables fault-tolerant streaming
3. **Apache Flink** provides low-latency stream processing (<100ms)
4. **Multi-Database Strategy** optimizes for different access patterns
5. **WebSocket** delivers real-time updates to users (<2 sec latency)
6. **React + OpenLayers** provides smooth, interactive map visualization

This architecture can scale from **16 demo vessels** to **100,000+ production vessels** by adding more Kafka partitions, Flink task slots, and database shards.

---

**Last Updated**: February 28, 2026  
**Author**: VMS Development Team  
**Version**: 1.0
