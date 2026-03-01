# Data Persistence Strategy - VMS

## Architecture Decision: Option B (Separate Consumer Services)

### Overview

The VMS uses a **separation of concerns** approach where:
1. **Flink stream-processor**: Processes data and publishes to Kafka
2. **Database Writer Services**: Consume from Kafka and persist to databases

```
┌─────────────────────────────────────────────┐
│        Flink Stream Processor               │
│                                             │
│  ais-raw-data → Processing → Kafka Topics   │
│                                             │
└──────────────┬──────────────┬───────────────┘
               │              │
               ▼              ▼
        vessel-positions  vessel-alerts
               │              │
    ┌──────────┼──────────────┼──────────────┐
    │          │              │              │
    ▼          ▼              ▼              ▼
┌────────┐ ┌────────┐ ┌──────────┐ ┌──────────┐
│Database│ │Backend │ │WebSocket │ │Notification│
│Writer  │ │APIs    │ │Server    │ │Service    │
│Service │ │        │ │          │ │           │
└────────┘ └────────┘ └──────────┘ └──────────┘
    │
    ├─→ PostgreSQL
    ├─→ ClickHouse
    ├─→ Redis
    └─→ Elasticsearch
```

---

## Why We Need Databases

### Problem Without Databases (Kafka Only)
```
❌ Kafka retains messages for only 7-30 days → Cannot query old data
❌ Cannot do complex queries: "Show all vessels in port X last year"
❌ Frontend must consume entire Kafka topic to show current state
❌ No persistent storage → Data loss if Kafka fails
❌ Poor performance for point queries: "Get vessel 525012345 position"
```

### Solution: Multi-Database Architecture

#### 1. PostgreSQL + PostGIS (Current State)
**Retention**: Current data only (one record per vessel)

**What's Stored**:
- Latest vessel position per MMSI (upsert)
- Geofences (ports, restricted zones)
- Vessel master data (IMO, name, type, flag)
- Port information
- Active alerts (unresolved)

**Why**:
- ✅ ACID transactions for critical data
- ✅ Spatial queries (PostGIS): "Vessels within 50km of Jakarta"
- ✅ Point queries: "Current position of vessel X" → 1-5ms
- ✅ Reliable backend for API queries

**Query Examples**:
```sql
-- Get current position
SELECT * FROM vessel_positions WHERE mmsi = 525012345;

-- Spatial query
SELECT * FROM vessel_positions 
WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(106.8833, -6.1055), 4326)::geography, 50000);

-- Geofencing
SELECT mmsi FROM vessel_positions 
WHERE ST_Contains((SELECT polygon FROM geofences WHERE id = 42), location);
```

---

#### 2. ClickHouse (Historical Analytics)
**Retention**: Years (partitioned by month)

**What's Stored**:
- All historical vessel positions (append-only)
- Historical weather data
- Historical port operations
- Historical alerts
- Aggregated statistics (materialized views)

**Why**:
- ⚡ Query billions of records in seconds
- 💾 100:1 compression ratio (1TB → 10GB)
- 📊 Columnar storage (read only needed columns)
- 📅 Time-based partitioning (drop old partitions instantly)
- 🔄 Materialized views for pre-aggregated stats

**Query Examples**:
```sql
-- Vessel track for last 30 days
SELECT latitude, longitude, speed, timestamp 
FROM vessel_position_history 
WHERE mmsi = 525012345 
  AND timestamp >= now() - INTERVAL 30 DAY
ORDER BY timestamp;

-- Daily statistics
SELECT 
    toDate(timestamp) as date,
    count() as total_positions,
    avg(speed) as avg_speed,
    max(speed) as max_speed
FROM vessel_position_history
WHERE mmsi = 525012345
  AND timestamp >= now() - INTERVAL 1 YEAR
GROUP BY date;

-- Port traffic analytics
SELECT 
    port_id,
    count(DISTINCT mmsi) as unique_vessels,
    countIf(event_type = 'ARRIVAL') as arrivals,
    countIf(event_type = 'DEPARTURE') as departures
FROM port_events
WHERE date >= '2025-01-01'
GROUP BY port_id;
```

---

#### 3. Redis (Real-Time Cache)
**Retention**: 1 hour (TTL)

**What's Stored**:
- Latest vessel positions (cached)
- Geofence membership (Set)
- Live statistics (counters)
- Session data
- Rate limiting data
- Pub/Sub for WebSocket broadcasts

**Why**:
- ⚡ In-memory: <1ms access time
- 🚀 100K+ operations/sec
- 📡 Pub/Sub for real-time updates
- ⏳ TTL for automatic cleanup
- 💪 Reduces load on PostgreSQL

**Data Examples**:
```redis
# Latest position (Hash)
HSET vessel:525012345 
    latitude -6.1055 
    longitude 106.8833 
    speed 12.5 
    timestamp 1709114415000

EXPIRE vessel:525012345 3600  # 1 hour TTL

# Geofence membership (Set)
SADD geofence:port-jakarta:vessels 525012345 525067890

# Live statistics
SET stats:total_vessels 1547
SET stats:vessels_in_port 234

# Pub/Sub
PUBLISH vessel:positions '{"mmsi":525012345, "lat":-6.1055, "lon":106.8833}'
```

---

#### 4. Elasticsearch (Search & Logs)
**Retention**: 30-90 days

**What's Stored**:
- Vessel metadata (searchable)
- Application logs (Flink, collectors, APIs)
- Alert history (searchable)
- Audit trails

**Why**:
- 🔍 Full-text search: "Find vessels named 'MERATUS'"
- 🌐 Geospatial queries: "Vessels near Jakarta"
- 📊 Log analysis with Kibana
- 📈 Aggregations and analytics

**Search Examples**:
```json
// Full-text search
GET /vessels/_search
{
  "query": {
    "match": {
      "vessel_name": "meratus jakarta"
    }
  }
}

// Geospatial search
GET /vessels/_search
{
  "query": {
    "geo_distance": {
      "distance": "50km",
      "location": { "lat": -6.1055, "lon": 106.8833 }
    }
  }
}

// Alert search
GET /alerts/_search
{
  "query": {
    "bool": {
      "must": [
        { "match": { "alert_type": "GEOFENCE_VIOLATION" } },
        { "range": { "timestamp": { "gte": "now-7d" } } }
      ]
    }
  }
}
```

---

## Data Flow: Kafka to Databases

### Current Architecture (stream-processor)

**Flink ONLY writes to Kafka**:
```java
// StreamProcessorJob.java
enrichedPositions.sinkTo(positionKafkaSink);  // vessel-positions topic
alerts.sinkTo(alertKafkaSink);                // vessel-alerts topic

// NO database sinks in Flink (by design)
```

### Database Writer Service (To Be Created)

**Spring Boot service that consumes from Kafka**:
```java
@Service
public class DatabaseWriterService {
    
    @KafkaListener(topics = "vessel-positions", groupId = "db-writer")
    public void consumePosition(EnrichedPosition position) {
        // Write to all databases
        postgisRepository.upsert(position);           // Current state
        clickhouseRepository.insert(position);        // History
        redisTemplate.set("vessel:" + position.getMmsi(), position);  // Cache
        elasticsearchRepository.save(position);       // Search
    }
    
    @KafkaListener(topics = "vessel-alerts", groupId = "db-writer")
    public void consumeAlert(VesselAlert alert) {
        postgisAlertRepository.insert(alert);
        clickhouseAlertRepository.insert(alert);
        elasticsearchAlertRepository.save(alert);
    }
}
```

---

## Benefits of Separation (Option B)

### ✅ Advantages

1. **Separation of Concerns**
   - Flink: Stream processing only
   - Database Writer: Persistence only
   - Each service has single responsibility

2. **Independent Scaling**
   - Scale Flink for processing throughput
   - Scale Database Writer for write throughput
   - Scale independently without affecting each other

3. **Flexibility**
   - Add new consumers without modifying Flink
   - Add new databases without redeploying Flink
   - Multiple consumers can read same topic

4. **Kafka Buffering**
   - If database is slow, Kafka buffers messages
   - Flink not blocked by database write speed
   - Can replay messages if write fails

5. **Fault Isolation**
   - Database failure doesn't crash Flink
   - Can restart Database Writer without affecting Flink
   - Better error handling per consumer

6. **Development**
   - Different teams can work on different services
   - Easier testing (mock Kafka topics)
   - Simpler deployment and rollback

### ⚠️ Trade-offs

1. **Slightly Higher Latency**
   - Kafka round-trip adds ~50-100ms
   - Acceptable for most use cases
   - Mitigated by Redis caching

2. **More Services**
   - Need to manage additional service
   - More monitoring and logging
   - Worth it for benefits gained

3. **Eventual Consistency**
   - Database slightly behind Kafka
   - Acceptable for VMS use case
   - Redis provides "fast enough" reads

---

## Next Steps

1. ✅ **stream-processor**: Already clean, only writes to Kafka
2. ⏳ **Create Database Writer Service** (Spring Boot):
   - Consume `vessel-positions` topic
   - Consume `vessel-alerts` topic
   - Write to PostgreSQL, ClickHouse, Redis, Elasticsearch
3. ⏳ **Create Backend API Service** (Spring Boot):
   - Consume Kafka for WebSocket broadcasting
   - Query databases for API endpoints
4. ⏳ **Create Notification Service** (Spring Boot):
   - Consume `vessel-alerts` topic
   - Send SMS/Email/Push notifications

---

## Summary

| Database | Purpose | Data Type | Retention | Query Type |
|----------|---------|-----------|-----------|------------|
| **PostgreSQL** | Current state | Latest positions, geofences | Current only | OLTP, spatial |
| **ClickHouse** | Analytics | Historical positions, stats | Years | OLAP, time-series |
| **Redis** | Cache | Latest positions, live stats | 1 hour TTL | Key-value, Pub/Sub |
| **Elasticsearch** | Search | Vessel metadata, logs | 30-90 days | Full-text, geospatial |
| **Kafka** | Streaming | All messages | 7-30 days | Stream processing |

**Architecture Pattern**: **Option B (Separate Consumers)** ✅
- Flink → Kafka only
- Database Writer Service → Kafka to Databases
- Backend Services → Databases + WebSocket
