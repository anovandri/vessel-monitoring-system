'use client';

import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import L from 'leaflet';
import { WeatherData } from '@/lib/api/weather';

interface WeatherOverlayProps {
  weatherData: WeatherData[];
  enabled: boolean;
  showRadius?: boolean;
  radiusKm?: number;
}

const WEATHER_ICONS = {
  clear: '☀️',
  cloudy: '⛅',
  rainy: '🌧️',
  stormy: '⛈️',
  foggy: '�', // Changed from �🌫️ to avoid rectangle
};

const WEATHER_LABELS = {
  clear: 'Clear',
  cloudy: 'Cloudy',
  rainy: 'Rainy',
  stormy: 'Stormy',
  foggy: 'Foggy',
};

function getWeatherCondition(weather: WeatherData): keyof typeof WEATHER_ICONS {
  if (weather.wave_height > 4 || weather.wind_speed > 20) return 'stormy';
  if (weather.visibility < 2) return 'foggy';
  if (weather.humidity > 80 && weather.wind_speed > 5) return 'rainy';
  if (weather.humidity > 60) return 'cloudy';
  return 'clear';
}

function calculateDestination(start: L.LatLng, distanceKm: number, bearing: number): L.LatLng {
  const R = 6371;
  const d = distanceKm / R;
  const brng = (bearing * Math.PI) / 180;
  const lat1 = (start.lat * Math.PI) / 180;
  const lon1 = (start.lng * Math.PI) / 180;

  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(brng)
  );
  const lon2 =
    lon1 +
    Math.atan2(
      Math.sin(brng) * Math.sin(d) * Math.cos(lat1),
      Math.cos(d) - Math.sin(lat1) * Math.sin(lat2)
    );

  return L.latLng((lat2 * 180) / Math.PI, (lon2 * 180) / Math.PI);
}

function getConditionColor(condition: keyof typeof WEATHER_ICONS): string {
  const colors = {
    clear: '#FFD700',
    cloudy: '#87CEEB',
    rainy: '#4682B4',
    stormy: '#FF6B6B',
    foggy: '#B0C4DE',
  };
  return colors[condition];
}

function createDotMatrix(center: L.LatLng, radiusKm: number, condition: keyof typeof WEATHER_ICONS): L.LayerGroup {
  const dotLayer = L.layerGroup();
  const dotSpacingKm = 8;
  const maxDots = 40;

  let dotsCreated = 0;

  for (let angle = 0; angle < 360; angle += 30) {
    for (let distance = dotSpacingKm; distance < radiusKm; distance += dotSpacingKm) {
      if (dotsCreated >= maxDots) break;

      const dotLatLng = calculateDestination(center, distance, angle);

      const dot = L.circleMarker(dotLatLng, {
        radius: 2,
        fillColor: getConditionColor(condition),
        color: 'transparent',
        fillOpacity: 0.4,
        interactive: false,
        className: 'weather-dot',
      });

      dotLayer.addLayer(dot);
      dotsCreated++;
    }
    if (dotsCreated >= maxDots) break;
  }

  return dotLayer;
}

export default function WeatherOverlay({ 
  weatherData, 
  enabled, 
  showRadius = true, 
  radiusKm = 50 
}: WeatherOverlayProps) {
  const map = useMap();
  const iconsRef = useRef<L.Marker[]>([]);
  const circlesRef = useRef<L.Circle[]>([]);
  const dotLayersRef = useRef<L.LayerGroup[]>([]);

  useEffect(() => {
    iconsRef.current.forEach(marker => marker.remove());
    circlesRef.current.forEach(circle => circle.remove());
    dotLayersRef.current.forEach(layer => layer.remove());
    iconsRef.current = [];
    circlesRef.current = [];
    dotLayersRef.current = [];

    if (!enabled || weatherData.length === 0) return;

    weatherData.forEach((weather) => {
      const condition = getWeatherCondition(weather);
      const centerLatLng = L.latLng(weather.latitude, weather.longitude);

      if (showRadius) {
        const circle = L.circle(centerLatLng, {
          radius: radiusKm * 1000,
          color: 'rgba(0, 255, 255, 0.15)',
          fillColor: 'rgba(0, 255, 255, 0.03)',
          fillOpacity: 0.3,
          weight: 1,
          interactive: false,
          dashArray: '5, 5',
        }).addTo(map);
        circlesRef.current.push(circle);

        const dotLayer = createDotMatrix(centerLatLng, radiusKm, condition);
        dotLayer.addTo(map);
        dotLayersRef.current.push(dotLayer);
      }

      const icon = L.divIcon({
        html: `
          <div class="weather-icon-wrapper">
            <div class="weather-icon">${WEATHER_ICONS[condition]}</div>
            ${showRadius ? `
              <div class="weather-info-label">
                <div class="weather-condition">${WEATHER_LABELS[condition]}</div>
                <div class="weather-details">
                  <span>💨 ${weather.wind_speed.toFixed(1)}m/s</span>
                  <span>🌡️ ${weather.temperature.toFixed(1)}°C</span>
                </div>
                <div class="weather-radius">${radiusKm}km radius</div>
              </div>
            ` : ''}
          </div>
        `,
        className: 'weather-marker',
        iconSize: [40, 40],
        iconAnchor: [20, 20],
      });

      const marker = L.marker(centerLatLng, {
        icon,
        interactive: false,
        zIndexOffset: -500,
      }).addTo(map);

      iconsRef.current.push(marker);
    });

    return () => {
      iconsRef.current.forEach(marker => marker.remove());
      circlesRef.current.forEach(circle => circle.remove());
      dotLayersRef.current.forEach(layer => layer.remove());
      iconsRef.current = [];
      circlesRef.current = [];
      dotLayersRef.current = [];
    };
  }, [weatherData, enabled, showRadius, radiusKm, map]);

  return (
    <style jsx global>{`
      .weather-marker {
        background: transparent !important;
        border: none !important;
      }

      .weather-icon-wrapper {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: auto;
        animation: weatherFloat 3s ease-in-out infinite;
      }

      .weather-icon {
        font-size: 28px;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.3));
        animation: weatherPulse 2s ease-in-out infinite;
      }

      .weather-info-label {
        margin-top: 6px;
        background: rgba(0, 0, 0, 0.75);
        border: 1px solid rgba(0, 255, 255, 0.3);
        border-radius: 8px;
        padding: 6px 10px;
        min-width: 140px;
        text-align: center;
        backdrop-filter: blur(4px);
      }

      .weather-condition {
        font-size: 11px;
        color: rgba(0, 255, 255, 0.95);
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        margin-bottom: 4px;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
      }

      .weather-details {
        display: flex;
        justify-content: space-around;
        gap: 8px;
        font-size: 9px;
        color: rgba(255, 255, 255, 0.85);
        margin-bottom: 3px;
      }

      .weather-details span {
        white-space: nowrap;
      }

      .weather-radius {
        font-size: 8px;
        color: rgba(0, 255, 255, 0.6);
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.3px;
      }

      .weather-radius-label {
        font-size: 9px;
        color: rgba(0, 255, 255, 0.8);
        background: rgba(0, 0, 0, 0.6);
        padding: 2px 6px;
        border-radius: 8px;
        margin-top: 4px;
        font-weight: 600;
        text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
        white-space: nowrap;
      }

      .weather-dot {
        animation: dotPulse 3s ease-in-out infinite;
      }

      @keyframes weatherFloat {
        0%, 100% {
          transform: translateY(0px);
        }
        50% {
          transform: translateY(-5px);
        }
      }

      @keyframes weatherPulse {
        0%, 100% {
          transform: scale(1);
          opacity: 0.9;
        }
        50% {
          transform: scale(1.1);
          opacity: 1;
        }
      }

      @keyframes dotPulse {
        0%, 100% {
          opacity: 0.3;
        }
        50% {
          opacity: 0.6;
        }
      }
    `}</style>
  );
}
