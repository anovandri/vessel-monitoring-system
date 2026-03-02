import { WeatherData } from './weather';

/**
 * Generate mock weather data for testing
 * This is used when the backend service is not available
 */
export function generateMockWeatherData(
  bounds: { north: number; south: number; east: number; west: number }
): WeatherData[] {
  const weatherData: WeatherData[] = [];
  
  // Generate only 5-8 weather points randomly within the visible bounds
  // This simulates sparse weather station data
  const numPoints = 5 + Math.floor(Math.random() * 4); // 5-8 points
  
  for (let i = 0; i < numPoints; i++) {
    // Random position within bounds
    const lat = bounds.south + Math.random() * (bounds.north - bounds.south);
    const lon = bounds.west + Math.random() * (bounds.east - bounds.west);
    const gridId = `grid_${i}_${Date.now()}`;
    
    // Random weather conditions
    const temperature = 20 + Math.random() * 15; // 20-35°C
    const windSpeed = Math.random() * 25; // 0-25 m/s
    const windDirection = Math.random() * 360; // 0-360 degrees
    const waveHeight = Math.random() * 5; // 0-5 meters
    const visibility = Math.random() * 10; // 0-10 km
    const pressure = 990 + Math.random() * 30; // 990-1020 hPa
    const humidity = 40 + Math.random() * 60; // 40-100%
    
    weatherData.push({
      grid_id: gridId,
      latitude: lat,
      longitude: lon,
      temperature,
      wind_speed: windSpeed,
      wind_direction: windDirection,
      wave_height: waveHeight,
      visibility,
      pressure,
      humidity,
      timestamp: new Date().toISOString()
    });
  }
  
  return weatherData;
}
