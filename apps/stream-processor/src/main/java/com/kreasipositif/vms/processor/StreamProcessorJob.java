package com.kreasipositif.vms.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kreasipositif.vms.processor.config.FlinkConfig;
import com.kreasipositif.vms.processor.functions.*;
import com.kreasipositif.vms.processor.model.AISMessage;
import com.kreasipositif.vms.processor.model.EnrichedPosition;
import com.kreasipositif.vms.processor.model.VesselAlert;
import com.kreasipositif.vms.processor.serialization.AISMessageDeserializationSchema;
import com.kreasipositif.vms.processor.serialization.EnrichedPositionSerializationSchema;
import com.kreasipositif.vms.processor.serialization.VesselAlertSerializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Main Flink job for VMS stream processing.
 * 
 * Pipeline:
 * 1. Consume from Kafka (ais-raw-data)
 * 2. Validate positions
 * 3. Enrich with vessel master data
 * 4. Detect anomalies (speed, geofence, collision)
 * 5. Publish to Kafka (vessel-positions, vessel-alerts)
 * 6. Write to databases (PostgreSQL, ClickHouse, Redis)
 */
public class StreamProcessorJob {
    
    private static final Logger LOG = LoggerFactory.getLogger(StreamProcessorJob.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    
    public static void main(String[] args) throws Exception {
        
        // Load configuration
        FlinkConfig config = FlinkConfig.fromEnvironment();
        
        LOG.info("🚀 Starting VMS Stream Processor...");
        LOG.info("📍 Kafka Bootstrap Servers: {}", config.getKafkaBootstrapServers());
        LOG.info("📥 Input Topic: {}", config.getInputTopic());
        LOG.info("📤 Output Topics: {}, {}", config.getPositionsTopic(), config.getAlertsTopic());
        
        // Setup execution environment
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // Configure checkpointing for fault tolerance
        configureCheckpointing(env, config);
        
        // Configure parallelism
        env.setParallelism(config.getParallelism());
        
        // Configure restart strategy
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
            3,  // Number of restart attempts
            org.apache.flink.api.common.time.Time.seconds(10) // Delay between restarts
        ));
        
        // ===== STEP 1: Kafka Source (ais-raw-data) =====
        LOG.info("📊 Setting up Kafka source...");
        KafkaSource<AISMessage> kafkaSource = KafkaSource.<AISMessage>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setTopics(config.getInputTopic())
            .setGroupId("flink-vms-processor")
            .setStartingOffsets(OffsetsInitializer.earliest())
            .setValueOnlyDeserializer(new AISMessageDeserializationSchema())
            .build();
        
        DataStream<AISMessage> rawStream = env
            .fromSource(kafkaSource, WatermarkStrategy
                .<AISMessage>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                .withTimestampAssigner((msg, timestamp) -> 
                    msg.getTimestamp() != null ? msg.getTimestamp().toEpochMilli() : System.currentTimeMillis()
                ),
                "Kafka Source (ais-raw-data)"
            );
        
        // ===== STEP 2: Validation =====
        LOG.info("✅ Setting up validation operator...");
        OutputTag<AISMessage> invalidTag = new OutputTag<AISMessage>("invalid-messages"){};
        
        SingleOutputStreamOperator<AISMessage> validatedStream = rawStream
            .process(new PositionValidationFunction(invalidTag))
            .name("Position Validation")
            .uid("position-validation");
        
        // Handle invalid messages (log or send to dead letter queue)
        validatedStream.getSideOutput(invalidTag)
            .map(msg -> {
                LOG.warn("❌ Invalid message: MMSI={}, Reason=Invalid coordinates or speed", msg.getMmsi());
                return msg;
            })
            .name("Invalid Message Handler");
        
        // ===== STEP 3: Enrichment =====
        LOG.info("🔍 Setting up enrichment operator...");
        DataStream<EnrichedPosition> enrichedStream = validatedStream
            .keyBy(AISMessage::getMmsi)
            .process(new VesselEnrichmentFunction(config))
            .name("Vessel Data Enrichment")
            .uid("vessel-enrichment");
        
        // ===== STEP 4: Anomaly Detection =====
        LOG.info("🚨 Setting up anomaly detection...");
        
        // 4a. Speed Anomaly Detection
        OutputTag<VesselAlert> speedAlertsTag = new OutputTag<VesselAlert>("speed-alerts"){};
        SingleOutputStreamOperator<EnrichedPosition> speedChecked = enrichedStream
            .keyBy(EnrichedPosition::getMmsi)
            .process(new SpeedAnomalyDetectionFunction(speedAlertsTag))
            .name("Speed Anomaly Detection")
            .uid("speed-anomaly-detection");
        
        // 4b. Geofencing (to be implemented)
        // OutputTag<VesselAlert> geofenceAlertsTag = new OutputTag<VesselAlert>("geofence-alerts"){};
        // ...
        
        // Collect all alerts
        DataStream<VesselAlert> alertsStream = speedChecked.getSideOutput(speedAlertsTag);
        // .union(speedChecked.getSideOutput(geofenceAlertsTag))  // Add more alert types
        
        // ===== STEP 5: Kafka Sinks =====
        LOG.info("📤 Setting up Kafka sinks...");
        
        // Sink: vessel-positions
        KafkaSink<EnrichedPosition> positionsSink = KafkaSink.<EnrichedPosition>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic(config.getPositionsTopic())
                .setValueSerializationSchema(new EnrichedPositionSerializationSchema())
                .build()
            )
            .build();
        
        speedChecked.sinkTo(positionsSink)
            .name("Kafka Sink (vessel-positions)")
            .uid("kafka-sink-positions");
        
        // Sink: vessel-alerts
        KafkaSink<VesselAlert> alertsSink = KafkaSink.<VesselAlert>builder()
            .setBootstrapServers(config.getKafkaBootstrapServers())
            .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                .setTopic(config.getAlertsTopic())
                .setValueSerializationSchema(new VesselAlertSerializationSchema())
                .build()
            )
            .build();
        
        alertsStream.sinkTo(alertsSink)
            .name("Kafka Sink (vessel-alerts)")
            .uid("kafka-sink-alerts");
        
        // ===== STEP 6: Database Sinks =====
        // TODO: Add PostgreSQL, ClickHouse, Redis sinks
        
        // ===== Execute =====
        LOG.info("▶️  Executing Flink job...");
        env.execute("VMS Stream Processor");
    }
    
    /**
     * Configure checkpointing for fault tolerance.
     */
    private static void configureCheckpointing(StreamExecutionEnvironment env, FlinkConfig config) {
        if (config.isCheckpointingEnabled()) {
            env.enableCheckpointing(config.getCheckpointInterval());
            
            CheckpointConfig checkpointConfig = env.getCheckpointConfig();
            checkpointConfig.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
            checkpointConfig.setMinPauseBetweenCheckpoints(30000);
            checkpointConfig.setCheckpointTimeout(600000);
            checkpointConfig.setMaxConcurrentCheckpoints(1);
            checkpointConfig.setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
            );
            
            // State backend
            if ("rocksdb".equalsIgnoreCase(config.getStateBackend())) {
                env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
                LOG.info("💾 Using RocksDB state backend");
            } else {
                env.setStateBackend(new HashMapStateBackend());
                LOG.info("💾 Using HashMap state backend");
            }
            
            LOG.info("⏱️  Checkpointing enabled: interval={}ms", config.getCheckpointInterval());
        } else {
            LOG.warn("⚠️  Checkpointing disabled - not recommended for production!");
        }
    }
}
