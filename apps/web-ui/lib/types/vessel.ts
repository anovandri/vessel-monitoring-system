// Vessel Position Data Types
// Based on EnrichedPosition model from backend (apps/stream-processor/src/main/java/.../EnrichedPosition.java)

export interface VesselPosition {
  // Vessel Identification
  mmsi: number;
  vesselName: string | null;
  imo: string | null;
  callSign: string | null;
  
  // Position Data 
  // NOTE: Backend sends these as nullable Double, but valid vessel positions should always have lat/lon
  // Frontend should validate and skip vessels with missing coordinates
  latitude: number | null;
  longitude: number | null;
  speed: number | null; // knots
  course: number | null; // degrees (0-360)
  heading: number | null; // degrees (0-360)
  status: string | null; // "underway", "at_anchor", "moored", etc.
  
  // Enriched Vessel Data
  vesselType: string | null; // CARGO, TANKER, PASSENGER, FISHING, etc.
  flag: string | null; // Country code
  destination: string | null;
  eta: string | null; // ISO 8601 timestamp
  draught: number | null; // meters
  length: number | null; // meters
  width: number | null; // meters
  
  // Calculated Fields
  distanceFromPrevious: number | null; // nautical miles
  timeSinceLastUpdate: number | null; // milliseconds
  
  // Timestamps
  timestamp: string; // ISO 8601
  processedTime: string | null; // ISO 8601
  
  // Metadata
  source: string | null;
  validated: boolean | null;
  validationStatus: string | null;
}

export interface VesselAlert {
  id: string;
  mmsi: number;
  vesselName: string | null;
  alertType: 'SPEED_ANOMALY' | 'GEOFENCE_VIOLATION' | 'COLLISION_RISK' | 'COURSE_CHANGE';
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  message: string;
  latitude: number;
  longitude: number;
  timestamp: string;
  details: Record<string, unknown>;
}
