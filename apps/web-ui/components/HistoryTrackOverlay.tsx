'use client';

import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

interface VesselHistoryPoint {
  mmsi: number;
  vessel_name: string;
  latitude: number;
  longitude: number;
  speed: number;
  course: number;
  heading: number;
  navigational_status: number;
  timestamp: number;
}

interface HistoryTrackOverlayProps {
  map: L.Map | null;
  historyData: VesselHistoryPoint[];
  currentIndex: number;
  enabled: boolean;
}

export default function HistoryTrackOverlay({
  map,
  historyData,
  currentIndex,
  enabled
}: HistoryTrackOverlayProps) {
  const trackLineRef = useRef<L.Polyline | null>(null);
  const vesselMarkerRef = useRef<L.Marker | null>(null);
  const trailMarkersRef = useRef<L.CircleMarker[]>([]);

  useEffect(() => {
    if (!map || !enabled || historyData.length === 0) {
      // Clean up
      if (trackLineRef.current) {
        map?.removeLayer(trackLineRef.current);
        trackLineRef.current = null;
      }
      if (vesselMarkerRef.current) {
        map?.removeLayer(vesselMarkerRef.current);
        vesselMarkerRef.current = null;
      }
      trailMarkersRef.current.forEach(marker => map?.removeLayer(marker));
      trailMarkersRef.current = [];
      return;
    }

    // Get all positions up to current index
    const visiblePath = historyData.slice(0, currentIndex + 1);
    const currentPosition = historyData[currentIndex];

    // Create or update track line
    const pathCoords: [number, number][] = visiblePath.map(p => [p.latitude, p.longitude]);
    
    if (trackLineRef.current) {
      trackLineRef.current.setLatLngs(pathCoords);
    } else {
      trackLineRef.current = L.polyline(pathCoords, {
        color: '#22D3EE',
        weight: 3,
        opacity: 0.8,
        smoothFactor: 1
      }).addTo(map);
    }

    // Update trail markers (show every 10th position)
    trailMarkersRef.current.forEach(marker => map.removeLayer(marker));
    trailMarkersRef.current = [];

    visiblePath.forEach((point, index) => {
      if (index % 10 === 0 && index !== currentIndex) {
        const marker = L.circleMarker([point.latitude, point.longitude], {
          radius: 3,
          color: '#22D3EE',
          fillColor: '#0A0E1A',
          fillOpacity: 0.8,
          weight: 1
        }).addTo(map);
        
        trailMarkersRef.current.push(marker);
      }
    });

    // Create arrow SVG for vessel marker
    const createArrowIcon = (course: number) => {
      const svgIcon = `
        <svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
          <g transform="rotate(${course} 12 12)">
            <path d="M12 2 L4 20 L12 16 L20 20 Z" fill="#22D3EE" stroke="#0A0E1A" stroke-width="1.5"/>
          </g>
        </svg>
      `;
      
      return L.divIcon({
        className: 'vessel-history-marker',
        html: svgIcon,
        iconSize: [24, 24],
        iconAnchor: [12, 12]
      });
    };

    // Create or update vessel marker
    if (vesselMarkerRef.current) {
      vesselMarkerRef.current
        .setLatLng([currentPosition.latitude, currentPosition.longitude])
        .setIcon(createArrowIcon(currentPosition.course));
    } else {
      vesselMarkerRef.current = L.marker(
        [currentPosition.latitude, currentPosition.longitude],
        { icon: createArrowIcon(currentPosition.course) }
      ).addTo(map);

      // Add popup
      vesselMarkerRef.current.bindPopup(`
        <div class="p-2">
          <div class="font-bold text-sm mb-1">${currentPosition.vessel_name}</div>
          <div class="text-xs text-gray-600 space-y-1">
            <div>MMSI: ${currentPosition.mmsi}</div>
            <div>Speed: ${currentPosition.speed.toFixed(1)} kn</div>
            <div>Course: ${currentPosition.course.toFixed(0)}°</div>
            <div>Time: ${new Date(currentPosition.timestamp).toLocaleString()}</div>
          </div>
        </div>
      `);
    }

    // Fit map to show the vessel if this is the first render or user isn't panning
    if (currentIndex === 0) {
      map.setView([currentPosition.latitude, currentPosition.longitude], Math.max(map.getZoom(), 10));
    } else {
      // Smoothly pan to follow the vessel
      map.panTo([currentPosition.latitude, currentPosition.longitude], {
        animate: true,
        duration: 0.5,
        noMoveStart: true
      });
    }

    return () => {
      // Cleanup on unmount
      if (trackLineRef.current) {
        map.removeLayer(trackLineRef.current);
      }
      if (vesselMarkerRef.current) {
        map.removeLayer(vesselMarkerRef.current);
      }
      trailMarkersRef.current.forEach(marker => map.removeLayer(marker));
    };
  }, [map, historyData, currentIndex, enabled]);

  return null; // This is a non-visual component
}
