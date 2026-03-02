/**
 * Weather API Client
 * Connects to data-persistence-service weather endpoints
 */

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8082';

export interface WeatherData {
  grid_id: string;
  latitude: number;
  longitude: number;
  temperature: number;
  wind_speed: number;
  wind_direction: number;
  wave_height: number;
  visibility: number;
  pressure: number;
  humidity: number;
  timestamp: string;
  distance_km?: number;
}

export interface WeatherCondition {
  type: 'clear' | 'cloudy' | 'rainy' | 'stormy' | 'foggy';
  intensity: number; // 0-1
}

/**
 * Determine weather condition from weather data
 */
export function getWeatherCondition(weather: WeatherData): WeatherCondition {
  const { visibility, wind_speed, wave_height, humidity } = weather;
  
  // Stormy conditions: high waves + high wind
  if (wave_height > 4 || wind_speed > 20) {
    return { type: 'stormy', intensity: Math.min(1, (wave_height + wind_speed / 10) / 8) };
  }
  
  // Foggy conditions: low visibility
  if (visibility < 2) {
    return { type: 'foggy', intensity: Math.min(1, (5 - visibility) / 5) };
  }
  
  // Rainy conditions: high humidity + moderate wind
  if (humidity > 80 && wind_speed > 5) {
    return { type: 'rainy', intensity: Math.min(1, humidity / 100) };
  }
  
  // Cloudy conditions: moderate humidity
  if (humidity > 60) {
    return { type: 'cloudy', intensity: Math.min(1, humidity / 80) };
  }
  
  // Clear weather
  return { type: 'clear', intensity: 0.2 };
}

/**
 * Get weather data for a specific grid
 */
export async function getWeatherByGrid(gridId: string): Promise<WeatherData | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/weather/grid/${gridId}`);
    if (!response.ok) {
      return null;
    }
    return await response.json();
  } catch (error) {
    console.error('Error fetching weather by grid:', error);
    return null;
  }
}

/**
 * Get weather data near a location
 */
export async function getWeatherNearby(
  lat: number,
  lon: number,
  radius: number = 50
): Promise<WeatherData[]> {
  try {
    const response = await fetch(
      `${API_BASE_URL}/api/weather/nearby?lat=${lat}&lon=${lon}&radius=${radius}`
    );
    if (!response.ok) {
      return [];
    }
    return await response.json();
  } catch (error) {
    console.error('Error fetching nearby weather:', error);
    return [];
  }
}

/**
 * Get all weather grid IDs
 */
export async function getAllWeatherGrids(): Promise<string[]> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/weather/grids`);
    if (!response.ok) {
      return [];
    }
    return await response.json();
  } catch (error) {
    console.error('Error fetching weather grids:', error);
    return [];
  }
}

/**
 * Get weather data for current map bounds
 */
export async function getWeatherForBounds(
  bounds: { north: number; south: number; east: number; west: number }
): Promise<WeatherData[]> {
  const centerLat = (bounds.north + bounds.south) / 2;
  const centerLon = (bounds.east + bounds.west) / 2;
  
  // Calculate approximate radius from bounds
  const latDiff = Math.abs(bounds.north - bounds.south);
  const lonDiff = Math.abs(bounds.east - bounds.west);
  const radius = Math.max(latDiff, lonDiff) * 111 / 2; // Convert degrees to km (approximate)
  
  try {
    const data = await getWeatherNearby(centerLat, centerLon, Math.min(radius, 500)); // Cap at 500km
    console.log(`Weather API returned ${data.length} points for bounds`, bounds);
    return data;
  } catch (error) {
    console.error('Error fetching weather data from backend:', error);
    return [];
  }
}
