-- ClickHouse Initialization Script for Vessel Monitoring System
-- This script creates time-series tables for historical analytics

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS vessel_monitoring;

USE vessel_monitoring;

-- Vessel Positions History Table
CREATE TABLE IF NOT EXISTS vessel_positions_history (
    mmsi UInt32,
    vessel_name String,
    vessel_type LowCardinality(String),
    latitude Float64,
    longitude Float64,
    speed Float64,
    course Float64,
    heading Nullable(Float64),
    navigational_status LowCardinality(String),
    timestamp DateTime,
    country LowCardinality(String),
    flag_state LowCardinality(String),
    callsign String,
    imo_number String,
    destination String,
    eta Nullable(DateTime),
    draught Nullable(Float64),
    cargo_type LowCardinality(String),
    date Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (mmsi, timestamp)
TTL date + INTERVAL 2 YEAR
SETTINGS index_granularity = 8192;

-- Create indexes
ALTER TABLE vessel_positions_history ADD INDEX idx_vessel_type vessel_type TYPE minmax GRANULARITY 4;
ALTER TABLE vessel_positions_history ADD INDEX idx_country country TYPE set(100) GRANULARITY 4;

-- Vessel Alerts History Table
CREATE TABLE IF NOT EXISTS vessel_alerts_history (
    alert_id String,
    mmsi UInt32,
    vessel_name String,
    alert_type LowCardinality(String),
    severity LowCardinality(String),
    latitude Float64,
    longitude Float64,
    speed Float64,
    timestamp DateTime,
    description String,
    date Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (timestamp, mmsi, alert_type)
TTL date + INTERVAL 3 YEAR
SETTINGS index_granularity = 8192;

-- Create indexes
ALTER TABLE vessel_alerts_history ADD INDEX idx_alert_type alert_type TYPE set(50) GRANULARITY 4;
ALTER TABLE vessel_alerts_history ADD INDEX idx_severity severity TYPE set(10) GRANULARITY 4;

-- Weather Data History Table
CREATE TABLE IF NOT EXISTS weather_data_history (
    grid_id String,
    latitude Float64,
    longitude Float64,
    temperature Nullable(Float64),
    wind_speed Nullable(Float64),
    wind_direction Nullable(Float64),
    wave_height Nullable(Float64),
    visibility Nullable(Float64),
    pressure Nullable(Float64),
    humidity Nullable(Float64),
    timestamp DateTime,
    date Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (grid_id, timestamp)
TTL date + INTERVAL 1 YEAR
SETTINGS index_granularity = 8192;

-- Port Operations History Table
CREATE TABLE IF NOT EXISTS port_operations_history (
    operation_id String,
    port_id LowCardinality(String),
    port_name String,
    mmsi Nullable(UInt32),
    vessel_name String,
    operation_type LowCardinality(String),
    berth_number String,
    arrival_time Nullable(DateTime),
    departure_time Nullable(DateTime),
    status LowCardinality(String),
    timestamp DateTime,
    date Date DEFAULT toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (port_id, timestamp)
TTL date + INTERVAL 2 YEAR
SETTINGS index_granularity = 8192;

-- Create indexes
ALTER TABLE port_operations_history ADD INDEX idx_port_id port_id TYPE set(100) GRANULARITY 4;
ALTER TABLE port_operations_history ADD INDEX idx_operation_type operation_type TYPE set(20) GRANULARITY 4;

-- Materialized View: Vessel Movement Statistics (Hourly Aggregation)
CREATE MATERIALIZED VIEW IF NOT EXISTS vessel_movement_stats_hourly
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (mmsi, hour, date)
AS SELECT
    mmsi,
    vessel_name,
    vessel_type,
    toStartOfHour(timestamp) as hour,
    toDate(timestamp) as date,
    count() as position_count,
    avg(speed) as avg_speed,
    max(speed) as max_speed,
    min(speed) as min_speed,
    avg(latitude) as avg_latitude,
    avg(longitude) as avg_longitude
FROM vessel_positions_history
GROUP BY mmsi, vessel_name, vessel_type, hour, date;

-- Materialized View: Alert Statistics (Daily Aggregation)
CREATE MATERIALIZED VIEW IF NOT EXISTS alert_stats_daily
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (alert_type, severity, date)
AS SELECT
    alert_type,
    severity,
    toDate(timestamp) as date,
    count() as alert_count,
    uniq(mmsi) as affected_vessels
FROM vessel_alerts_history
GROUP BY alert_type, severity, date;

-- Materialized View: Port Traffic Statistics (Daily Aggregation)
CREATE MATERIALIZED VIEW IF NOT EXISTS port_traffic_stats_daily
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (port_id, date)
AS SELECT
    port_id,
    port_name,
    toDate(timestamp) as date,
    count() as operation_count,
    uniq(mmsi) as unique_vessels,
    countIf(operation_type = 'ARRIVAL') as arrivals,
    countIf(operation_type = 'DEPARTURE') as departures,
    countIf(status = 'COMPLETED') as completed_operations
FROM port_operations_history
GROUP BY port_id, port_name, date;

-- Materialized View: Weather Conditions (Hourly Average)
CREATE MATERIALIZED VIEW IF NOT EXISTS weather_stats_hourly
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (grid_id, hour, date)
AS SELECT
    grid_id,
    toStartOfHour(timestamp) as hour,
    toDate(timestamp) as date,
    avgState(temperature) as avg_temperature,
    avgState(wind_speed) as avg_wind_speed,
    avgState(wave_height) as avg_wave_height,
    avgState(visibility) as avg_visibility,
    avgState(pressure) as avg_pressure,
    avgState(humidity) as avg_humidity
FROM weather_data_history
GROUP BY grid_id, hour, date;

-- Create dictionary for vessel types (for faster lookups)
CREATE DICTIONARY IF NOT EXISTS vessel_type_dict
(
    code String,
    description String,
    category String
)
PRIMARY KEY code
SOURCE(CLICKHOUSE(
    HOST 'localhost'
    PORT 9000
    USER 'default'
    TABLE 'vessel_types'
    DB 'vessel_monitoring'
))
LIFETIME(MIN 3600 MAX 7200)
LAYOUT(FLAT());

-- Optimization: Create projection for faster geospatial queries
ALTER TABLE vessel_positions_history ADD PROJECTION vessel_positions_by_location
(
    SELECT
        mmsi,
        vessel_name,
        latitude,
        longitude,
        timestamp
    ORDER BY (latitude, longitude, timestamp)
);

-- Optimize tables
OPTIMIZE TABLE vessel_positions_history FINAL;
OPTIMIZE TABLE vessel_alerts_history FINAL;
OPTIMIZE TABLE weather_data_history FINAL;
OPTIMIZE TABLE port_operations_history FINAL;

-- Note: GRANT statements are not needed for default user with XML-based auth
-- GRANT ALL ON vessel_monitoring.* TO default;
