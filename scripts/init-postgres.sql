-- PostgreSQL Initialization Script for Vessel Monitoring System
-- This script creates the database schema with PostGIS extension

-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;

-- Create vessel_positions table (current state)
CREATE TABLE IF NOT EXISTS vessel_positions (
    mmsi INTEGER PRIMARY KEY,
    vessel_name VARCHAR(255),
    vessel_type VARCHAR(100),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOMETRY(Point, 4326),
    speed DOUBLE PRECISION,
    course DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    navigational_status VARCHAR(100),
    timestamp TIMESTAMP NOT NULL,
    country VARCHAR(100),
    flag_state VARCHAR(100),
    callsign VARCHAR(50),
    imo_number VARCHAR(50),
    destination VARCHAR(255),
    eta TIMESTAMP,
    draught DOUBLE PRECISION,
    cargo_type VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create spatial index on location
CREATE INDEX IF NOT EXISTS idx_vessel_positions_location ON vessel_positions USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_vessel_positions_timestamp ON vessel_positions(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_vessel_positions_vessel_type ON vessel_positions(vessel_type);

-- Create vessel_alerts table (active alerts)
CREATE TABLE IF NOT EXISTS vessel_alerts (
    alert_id VARCHAR(255) PRIMARY KEY,
    mmsi INTEGER NOT NULL,
    vessel_name VARCHAR(255),
    alert_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    speed DOUBLE PRECISION,
    timestamp TIMESTAMP NOT NULL,
    description TEXT,
    metadata JSONB,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vessel_alerts_mmsi ON vessel_alerts(mmsi);
CREATE INDEX IF NOT EXISTS idx_vessel_alerts_timestamp ON vessel_alerts(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_vessel_alerts_severity ON vessel_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_vessel_alerts_resolved ON vessel_alerts(resolved);
CREATE INDEX IF NOT EXISTS idx_vessel_alerts_metadata ON vessel_alerts USING GIN(metadata);

-- Create weather_data table (current weather per grid)
CREATE TABLE IF NOT EXISTS weather_data (
    grid_id VARCHAR(100) PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOMETRY(Point, 4326),
    temperature DOUBLE PRECISION,
    wind_speed DOUBLE PRECISION,
    wind_direction DOUBLE PRECISION,
    wave_height DOUBLE PRECISION,
    visibility DOUBLE PRECISION,
    pressure DOUBLE PRECISION,
    humidity DOUBLE PRECISION,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_weather_data_location ON weather_data USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_weather_data_timestamp ON weather_data(timestamp DESC);

-- Create port_operations table (current berth assignments)
CREATE TABLE IF NOT EXISTS port_operations (
    operation_id VARCHAR(255) PRIMARY KEY,
    port_id VARCHAR(100) NOT NULL,
    port_name VARCHAR(255),
    mmsi INTEGER,
    vessel_name VARCHAR(255),
    operation_type VARCHAR(100) NOT NULL,
    berth_number VARCHAR(50),
    arrival_time TIMESTAMP,
    departure_time TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_port_operations_port_id ON port_operations(port_id);
CREATE INDEX IF NOT EXISTS idx_port_operations_mmsi ON port_operations(mmsi);
CREATE INDEX IF NOT EXISTS idx_port_operations_status ON port_operations(status);
CREATE INDEX IF NOT EXISTS idx_port_operations_timestamp ON port_operations(timestamp DESC);

-- Create ports table (master data)
CREATE TABLE IF NOT EXISTS ports (
    port_id VARCHAR(100) PRIMARY KEY,
    port_name VARCHAR(255) NOT NULL,
    country VARCHAR(100),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOMETRY(Point, 4326),
    unlocode VARCHAR(10),
    port_type VARCHAR(100),
    max_vessel_size VARCHAR(50),
    total_berths INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ports_location ON ports USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_ports_country ON ports(country);

-- Create vessels table (master data)
CREATE TABLE IF NOT EXISTS vessels (
    mmsi INTEGER PRIMARY KEY,
    vessel_name VARCHAR(255),
    imo_number VARCHAR(50),
    callsign VARCHAR(50),
    vessel_type VARCHAR(100),
    flag_state VARCHAR(100),
    length DOUBLE PRECISION,
    width DOUBLE PRECISION,
    gross_tonnage DOUBLE PRECISION,
    built_year INTEGER,
    owner VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vessels_vessel_type ON vessels(vessel_type);
CREATE INDEX IF NOT EXISTS idx_vessels_flag_state ON vessels(flag_state);
CREATE INDEX IF NOT EXISTS idx_vessels_imo_number ON vessels(imo_number);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_vessel_positions_updated_at BEFORE UPDATE ON vessel_positions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_weather_data_updated_at BEFORE UPDATE ON weather_data
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_port_operations_updated_at BEFORE UPDATE ON port_operations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_ports_updated_at BEFORE UPDATE ON ports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_vessels_updated_at BEFORE UPDATE ON vessels
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Create view for active alerts summary
CREATE OR REPLACE VIEW active_alerts_summary AS
SELECT 
    alert_type,
    severity,
    COUNT(*) as alert_count,
    MIN(timestamp) as oldest_alert,
    MAX(timestamp) as latest_alert
FROM vessel_alerts
WHERE resolved = FALSE
GROUP BY alert_type, severity
ORDER BY severity DESC, alert_count DESC;

-- Create view for vessel status summary
CREATE OR REPLACE VIEW vessel_status_summary AS
SELECT 
    COUNT(*) as total_vessels,
    COUNT(CASE WHEN speed > 0 THEN 1 END) as moving_vessels,
    COUNT(CASE WHEN speed = 0 THEN 1 END) as stationary_vessels,
    AVG(speed) as avg_speed,
    MAX(timestamp) as last_update
FROM vessel_positions;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;

-- Insert sample ports data
INSERT INTO ports (port_id, port_name, country, latitude, longitude, location, unlocode, port_type, total_berths)
VALUES 
    ('SGSIN', 'Port of Singapore', 'Singapore', 1.2644, 103.8542, ST_SetSRID(ST_MakePoint(103.8542, 1.2644), 4326), 'SGSIN', 'Container Port', 50),
    ('IDTPP', 'Tanjung Priok', 'Indonesia', -6.1042, 106.8829, ST_SetSRID(ST_MakePoint(106.8829, -6.1042), 4326), 'IDTPP', 'Container Port', 30),
    ('MYPKG', 'Port Klang', 'Malaysia', 3.0039, 101.3944, ST_SetSRID(ST_MakePoint(101.3944, 3.0039), 4326), 'MYPKG', 'Container Port', 40)
ON CONFLICT (port_id) DO NOTHING;

ANALYZE vessel_positions;
ANALYZE vessel_alerts;
ANALYZE weather_data;
ANALYZE port_operations;
ANALYZE ports;
ANALYZE vessels;
