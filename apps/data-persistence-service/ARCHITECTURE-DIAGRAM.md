# WebSocket Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VESSEL MONITORING SYSTEM                                 │
│                    Real-time Data Flow with WebSocket                       │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│   AIS Sources    │  Marine traffic AIS data feeds
│   (External)     │
└────────┬─────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                     DATA COLLECTOR SERVICE                                  │
│  - Collects AIS messages from multiple sources                              │
│  - Validates and normalizes data                                            │
│  - Publishes to Kafka: ais-raw-data                                         │
└────────┬────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  APACHE FLINK STREAM PROCESSOR                              │
│  ┌──────────────────────────────────────────────────────────────┐          │
│  │  1. Parse & Validate → 2. Enrich → 3. Detect Anomalies       │          │
│  │     (AIS decoder)      (Vessel DB)   (Speed, Geofence)        │          │
│  └──────────────────────────────────────────────────────────────┘          │
│                                                                              │
│  Outputs to Kafka Topics:                                                   │
│  • vessel-positions  • vessel-alerts  • weather-data  • port-data           │
└────────┬────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DATA PERSISTENCE SERVICE ⭐                              │
│                    (WITH WEBSOCKET SUPPORT)                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    KAFKA CONSUMERS (Virtual Threads)                │   │
│  │  ┌──────────────┬──────────────┬──────────────┬──────────────┐      │   │
│  │  │vessel-pos    │vessel-alerts │weather-data  │port-data     │      │   │
│  │  │Consumer      │Consumer      │Consumer      │Consumer      │      │   │
│  │  └──────┬───────┴──────┬───────┴──────┬───────┴──────┬───────┘      │   │
│  └─────────┼──────────────┼──────────────┼──────────────┼──────────────┘   │
│            │              │              │              │                   │
│  ┌─────────▼──────────────▼──────────────▼──────────────▼──────────────┐   │
│  │           PERSISTENCE SERVICES (Parallel Async)                      │   │
│  │  • VesselPersistenceService   • AlertPersistenceService              │   │
│  │  • WeatherPersistenceService  • PortPersistenceService               │   │
│  └─────────┬──────────────┬──────────────┬──────────────┬──────────────┘   │
│            │              │              │              │                   │
│    ┌───────▼───┐  ┌───────▼───┐  ┌───────▼───┐  ┌───────▼───┐             │
│    │PostgreSQL │  │ClickHouse │  │  Redis    │  │ Redis     │             │
│    │(Current)  │  │(History)  │  │  (Cache)  │  │ (Pub/Sub) │ ⭐ NEW      │
│    └───────────┘  └───────────┘  └───────────┘  └─────┬─────┘             │
│                                                         │                   │
│  ┌──────────────────────────────────────────────────────▼──────────────┐   │
│  │              REDIS PUB/SUB TO WEBSOCKET BRIDGE                       │   │
│  │  ┌────────────────────────────────────────────────────────────────┐ │   │
│  │  │ • vessel-positions-stream  → /topic/vessel-positions           │ │   │
│  │  │ • weather-data-stream      → /topic/weather-data               │ │   │
│  │  │ • port-data-stream         → /topic/port-data                  │ │   │
│  │  │ • vessel-alerts-stream     → /topic/vessel-alerts              │ │   │
│  │  └────────────────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────┬───────────────────────────────┘   │
│                                         │                                   │
│  ┌──────────────────────────────────────▼───────────────────────────────┐   │
│  │           STOMP MESSAGE BROKER (WebSocket Server)                    │   │
│  │  • Endpoint: ws://localhost:8083/ws (with SockJS fallback)           │   │
│  │  • Protocol: STOMP over WebSocket                                    │   │
│  │  • CORS: Configurable (currently allows all origins)                 │   │
│  └──────────────────────────────────────┬───────────────────────────────┘   │
└─────────────────────────────────────────┼───────────────────────────────────┘
                                          │
                                          ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          BROWSER CLIENTS                                    │
│  ┌──────────────────────────────────────────────────────────────────────┐  │
│  │  JavaScript Client (SockJS + STOMP.js)                               │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │  │
│  │  │ Vessel Map  │  │Weather Layer│  │Port Monitor │  │Alert Panel  │ │  │
│  │  │             │  │             │  │             │  │             │ │  │
│  │  │ Real-time   │  │ Real-time   │  │ Real-time   │  │ Real-time   │ │  │
│  │  │ positions   │  │ weather     │  │ operations  │  │ alerts      │ │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘ │  │
│  │                                                                       │  │
│  │  Update Frequency:                                                    │  │
│  │  • Vessel positions: 1-2 seconds                                      │  │
│  │  • Weather data: 5-10 minutes                                         │  │
│  │  • Port operations: as they occur                                     │  │
│  │  • Vessel alerts: immediately                                         │  │
│  └──────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                         LATENCY BREAKDOWN                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  Flink Processing:           ~50-100ms                                      │
│  Kafka Transport:            ~20-50ms                                       │
│  Database Persistence:       ~30-100ms (parallel)                           │
│  Redis Pub/Sub:              ~1-5ms                                         │
│  WebSocket Broadcast:        ~5-10ms                                        │
│  ────────────────────────────────────────                                  │
│  Total End-to-End Latency:   ~100-300ms (p95 < 500ms) ✅                   │
└─────────────────────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                      KEY DESIGN DECISIONS                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ✅ EXTEND vs CREATE NEW SERVICE                                            │
│     • Extended existing data-persistence-service                            │
│     • Reason: Data already flowing through service, reduces latency         │
│     • Benefit: Fewer services to deploy and maintain                        │
│                                                                              │
│  ✅ REDIS PUB/SUB vs DIRECT WEBSOCKET                                       │
│     • Used Redis Pub/Sub as message broker                                  │
│     • Reason: Enables horizontal scaling of WebSocket servers               │
│     • Benefit: Multiple service instances can share pub/sub                 │
│                                                                              │
│  ✅ STOMP vs RAW WEBSOCKET                                                  │
│     • Used STOMP protocol over WebSocket                                    │
│     • Reason: Topic-based subscriptions, standard protocol                  │
│     • Benefit: Client libraries available for all platforms                 │
│                                                                              │
│  ✅ SOCKJS FALLBACK                                                         │
│     • Enabled SockJS for WebSocket fallback                                 │
│     • Reason: Browser compatibility, graceful degradation                   │
│     • Benefit: Works even when WebSocket is blocked                         │
│                                                                              │
│  ✅ VIRTUAL THREADS FOR CONCURRENCY                                         │
│     • Java 21 Virtual Threads for WebSocket connections                     │
│     • Reason: Thousands of concurrent connections with low overhead         │
│     • Benefit: Scales better than traditional thread-per-connection         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                      SCALING CONSIDERATIONS                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  HORIZONTAL SCALING                                                          │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐                │
│  │ Persistence-1  │  │ Persistence-2  │  │ Persistence-3  │                │
│  │ + WebSocket    │  │ + WebSocket    │  │ + WebSocket    │                │
│  └───────┬────────┘  └───────┬────────┘  └───────┬────────┘                │
│          └──────────────┬─────────────────────────┘                         │
│                         │                                                    │
│               ┌─────────▼─────────┐                                         │
│               │   Redis Pub/Sub   │  ← Shared message broker                │
│               │   (Cluster Mode)  │                                         │
│               └───────────────────┘                                         │
│                                                                              │
│  LOAD BALANCING                                                              │
│  • Use sticky sessions (IP hash) for WebSocket connections                  │
│  • NGINX upstream with ip_hash directive                                    │
│  • ALB with sticky session support                                          │
│                                                                              │
│  PERFORMANCE TARGETS                                                         │
│  • Concurrent WebSocket connections: 10,000+ per instance                   │
│  • Message throughput: 10,000 msg/sec per topic                             │
│  • Memory per connection: ~100KB (Virtual Threads)                          │
│  • CPU utilization: <70% under full load                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Component Details

### 1. Data Persistence Service (Extended)
- **Original Function**: Consumes Kafka, writes to databases
- **New Function**: Also publishes to Redis Pub/Sub channels
- **Implementation**: Added 1 line per persistence service to publish

### 2. Redis Pub/Sub Bridge
- **Function**: Listens to Redis channels, forwards to WebSocket
- **Implementation**: 4 MessageListenerAdapter beans
- **Channels**: vessel-positions-stream, weather-data-stream, port-data-stream, vessel-alerts-stream

### 3. STOMP Message Broker
- **Function**: Manages WebSocket subscriptions and broadcasting
- **Implementation**: Spring WebSocket with SimpleBroker
- **Topics**: /topic/vessel-positions, /topic/weather-data, /topic/port-data, /topic/vessel-alerts

### 4. WebSocket Endpoint
- **URL**: ws://localhost:8083/ws
- **Protocol**: STOMP over WebSocket
- **Fallback**: SockJS (long-polling, etc.)
- **CORS**: Configurable (currently allows all)

## Message Flow Example

```
1. AIS message arrives → Data Collector
2. Raw message published → Kafka (ais-raw-data)
3. Flink processes message → Enrichment, validation, anomaly detection
4. Enriched position published → Kafka (vessel-positions)
5. Persistence service consumes → PostgreSQL + ClickHouse + Redis
6. Service publishes to Redis Pub/Sub → vessel-positions-stream
7. Redis listener receives message → RedisWebSocketBridge
8. Bridge forwards to WebSocket → /topic/vessel-positions
9. All subscribed clients receive → Real-time map update

Total time: ~100-300ms from AIS source to browser
```

## Client Connection Flow

```
1. Browser loads page
2. JavaScript creates SockJS connection → ws://localhost:8083/ws
3. STOMP client connects → Authentication (future enhancement)
4. Client subscribes to topics → /topic/vessel-positions
5. Server acknowledges subscription
6. Messages start flowing → Client callback invoked for each message
7. Client updates UI → Map markers, weather layer, alert panel
8. Connection maintained → Automatic reconnection on failure
```
