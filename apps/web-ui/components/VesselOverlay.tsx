'use client';

import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import L from 'leaflet';
import { VesselPosition } from '@/lib/types/vessel';

interface VesselOverlayProps {
  vessels: VesselPosition[];
  enabled: boolean;
  onVesselClick?: (vessel: VesselPosition) => void;
}

// Vessel type to color mapping (MarineTraffic style)
const VESSEL_TYPE_COLORS: Record<string, string> = {
  cargo: '#FF6B00',        // Orange - Cargo ships
  tanker: '#FF0000',       // Red - Tankers
  passenger: '#00FF00',    // Green - Passenger vessels
  fishing: '#00FFFF',      // Cyan - Fishing vessels
  tug: '#FFFF00',          // Yellow - Tugs
  pleasure: '#FF00FF',     // Magenta - Pleasure craft
  sailing: '#00BFFF',      // Light blue - Sailing vessels
  military: '#808080',     // Gray - Military/Law enforcement
  unknown: '#FFFFFF',      // White - Unknown
};

// Status to color mapping
const STATUS_COLORS: Record<string, string> = {
  underway: '#00FF00',
  'underway using engine': '#00FF00',
  'underway sailing': '#00FF00',
  at_anchor: '#FFD700',
  'at anchor': '#FFD700',
  moored: '#FFA500',
  not_under_command: '#FF0000',
  restricted_manoeuvrability: '#FF6B6B',
  constrained_by_draught: '#FFA500',
  aground: '#FF0000',
  engaged_in_fishing: '#00CED1',
  default: '#00A0E3',
};

function getVesselColor(vessel: VesselPosition): string {
  const type = vessel.vesselType?.toLowerCase() || 'unknown';
  
  if (type.includes('cargo')) return VESSEL_TYPE_COLORS.cargo;
  if (type.includes('tanker')) return VESSEL_TYPE_COLORS.tanker;
  if (type.includes('passenger')) return VESSEL_TYPE_COLORS.passenger;
  if (type.includes('fishing')) return VESSEL_TYPE_COLORS.fishing;
  if (type.includes('tug')) return VESSEL_TYPE_COLORS.tug;
  if (type.includes('pleasure') || type.includes('yacht')) return VESSEL_TYPE_COLORS.pleasure;
  if (type.includes('sailing') || type.includes('sail')) return VESSEL_TYPE_COLORS.sailing;
  if (type.includes('military')) return VESSEL_TYPE_COLORS.military;
  
  return VESSEL_TYPE_COLORS.unknown;
}

/**
 * Create SVG arrow icon for vessel (MarineTraffic style)
 * Points in direction of vessel's heading
 */
function createVesselArrowSVG(color: string, rotation: number): string {
  return `
    <svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg" style="transform: rotate(${rotation}deg);">
      <path d="M12 2 L22 22 L12 18 L2 22 Z" fill="${color}" stroke="#000" stroke-width="1" opacity="0.9"/>
    </svg>
  `;
}

function getStatusColor(status: string | null | undefined): string {
  if (!status) return STATUS_COLORS.default;
  const normalizedStatus = status.toLowerCase().replace(/_/g, ' ');
  return STATUS_COLORS[normalizedStatus] || STATUS_COLORS.default;
}

function formatSpeed(speed: number | null | undefined): string {
  if (speed === null || speed === undefined) return 'N/A';
  return speed.toFixed(1);
}

function formatCourse(course: number | null | undefined): string {
  if (course === null || course === undefined) return 'N/A';
  const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
  const index = Math.round(course / 45) % 8;
  return `${course.toFixed(0)}° ${directions[index]}`;
}

function formatCoordinates(lat: number | null | undefined, lon: number | null | undefined): string {
  if (lat === null || lat === undefined || lon === null || lon === undefined) {
    return 'N/A';
  }
  return `${lat.toFixed(6)}, ${lon.toFixed(6)}`;
}

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSecs = Math.floor(diffMs / 1000);
  const diffMins = Math.floor(diffSecs / 60);
  
  if (diffSecs < 60) return `${diffSecs}s ago`;
  if (diffMins < 60) return `${diffMins}m ago`;
  return date.toLocaleTimeString();
}

export default function VesselOverlay({ 
  vessels, 
  enabled,
  onVesselClick 
}: VesselOverlayProps) {
  const map = useMap();
  const markersRef = useRef<Map<number, L.Marker>>(new Map());
  const courseArrowsRef = useRef<Map<number, L.Polyline>>(new Map());

  useEffect(() => {
    const markers = markersRef.current;
    const arrows = courseArrowsRef.current;

    console.log(`VesselOverlay: Rendering ${vessels.length} vessels`, vessels.length > 0 ? vessels[0] : 'none');

    // Clear all markers if disabled
    if (!enabled) {
      markers.forEach(marker => marker.remove());
      arrows.forEach(arrow => arrow.remove());
      markers.clear();
      arrows.clear();
      return;
    }

    // Track existing vessel IDs
    const currentVesselIds = new Set(vessels.map(v => v.mmsi));
    const existingVesselIds = new Set(markers.keys());

    // Remove markers for vessels that are no longer present
    existingVesselIds.forEach(mmsi => {
      if (!currentVesselIds.has(mmsi)) {
        markers.get(mmsi)?.remove();
        markers.delete(mmsi);
        arrows.get(mmsi)?.remove();
        arrows.delete(mmsi);
      }
    });

    // Update or create markers for each vessel
    vessels.forEach((vessel) => {
      // Skip vessels with invalid positions
      if (typeof vessel.latitude !== 'number' || typeof vessel.longitude !== 'number') {
        return;
      }
      
      const position = L.latLng(vessel.latitude, vessel.longitude);
      
      const statusColor = getStatusColor(vessel.status);
      const vesselColor = getVesselColor(vessel);
      // Use course for rotation, default to 0 if no course
      const rotation = vessel.course !== null && vessel.course !== undefined ? vessel.course : 0;
      const vesselArrowSVG = createVesselArrowSVG(vesselColor, rotation);
      const existingMarker = markers.get(vessel.mmsi);

      // Create course arrow (if vessel is moving)
      if (vessel.speed !== null && vessel.speed > 0.5 && vessel.course !== null) {
        const arrowLength = 0.01; // degrees (roughly 1km)
        const courseRad = (vessel.course * Math.PI) / 180;
        const endLat = vessel.latitude + arrowLength * Math.cos(courseRad);
        const endLon = vessel.longitude + arrowLength * Math.sin(courseRad);
        
        const existingArrow = arrows.get(vessel.mmsi);
        if (existingArrow) {
          existingArrow.setLatLngs([position, L.latLng(endLat, endLon)]);
        } else {
          const arrow = L.polyline([position, L.latLng(endLat, endLon)], {
            color: statusColor,
            weight: 2,
            opacity: 0.7,
            interactive: false,
          }).addTo(map);
          arrows.set(vessel.mmsi, arrow);
        }
      } else {
        // Remove arrow if vessel is not moving
        arrows.get(vessel.mmsi)?.remove();
        arrows.delete(vessel.mmsi);
      }

      // Update existing marker or create new one
      if (existingMarker) {
        // Update position
        existingMarker.setLatLng(position);
        
        // Update icon HTML with new rotation
        const iconDiv = existingMarker.getElement();
        if (iconDiv) {
          const iconElement = iconDiv.querySelector('.vessel-arrow');
          if (iconElement) {
            iconElement.innerHTML = vesselArrowSVG;
          }
        }
        
        // Update popup content
        existingMarker.setPopupContent(createPopupContent(vessel));
        
        // Update tooltip
        existingMarker.setTooltipContent(`
          <div class="vessel-tooltip-content">
            <strong>${vessel.vesselName || 'Unknown'}</strong><br/>
            <span class="datasource">Data: Mock AIS</span>
          </div>
        `);
      } else {
        // Create new marker
        
        const icon = L.divIcon({
          html: `
            <div class="vessel-marker-wrapper">
              <div class="vessel-arrow">${vesselArrowSVG}</div>
            </div>
          `,
          className: 'vessel-marker',
          iconSize: [24, 24],
          iconAnchor: [12, 12],
          popupAnchor: [0, -12],
        });

        const marker = L.marker(position, { icon })
          .bindPopup(createPopupContent(vessel), {
            maxWidth: 300,
            className: 'vessel-popup',
          })
          .bindTooltip(`
            <div class="vessel-tooltip-content">
              <strong>${vessel.vesselName || 'Unknown'}</strong><br/>
              <span class="datasource">Data: Mock AIS</span>
            </div>
          `, {
            permanent: false,
            direction: 'top',
            offset: [0, -12],
            className: 'vessel-tooltip',
          })
          .addTo(map);
        
        if (onVesselClick) {
          marker.on('click', () => onVesselClick(vessel));
        }

        markers.set(vessel.mmsi, marker);
      }
    });

    return () => {
      // Cleanup on unmount
      markers.forEach(marker => marker.remove());
      arrows.forEach(arrow => arrow.remove());
      markers.clear();
      arrows.clear();
    };
  }, [vessels, enabled, map, onVesselClick]);

  return (
    <style jsx global>{`
      .vessel-marker {
        background: transparent !important;
        border: none !important;
      }

      .vessel-marker-wrapper {
        position: relative;
        width: 24px;
        height: 24px;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .vessel-arrow {
        width: 24px;
        height: 24px;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.5));
      }

      .vessel-arrow svg {
        width: 100%;
        height: 100%;
      }

      .vessel-tooltip {
        background: rgba(0, 0, 0, 0.85) !important;
        border: 1px solid rgba(0, 255, 255, 0.3) !important;
        border-radius: 4px !important;
        padding: 4px 8px !important;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5) !important;
      }

      .vessel-tooltip-content {
        color: #ffffff;
        font-family: 'Inter', sans-serif;
        font-size: 12px;
        line-height: 1.4;
        text-align: center;
      }

      .vessel-tooltip-content strong {
        color: rgba(0, 255, 255, 0.95);
        font-weight: 600;
      }

      .vessel-tooltip-content .datasource {
        color: rgba(255, 255, 255, 0.6);
        font-size: 10px;
      }

      .vessel-tooltip .leaflet-tooltip-left::before,
      .vessel-tooltip .leaflet-tooltip-right::before {
        border-left-color: rgba(0, 0, 0, 0.85) !important;
        border-right-color: rgba(0, 0, 0, 0.85) !important;
      }

      .vessel-tooltip .leaflet-tooltip-top::before,
      .vessel-tooltip .leaflet-tooltip-bottom::before {
        border-top-color: rgba(0, 0, 0, 0.85) !important;
        border-bottom-color: rgba(0, 0, 0, 0.85) !important;
      }

      .vessel-popup .leaflet-popup-content-wrapper {
        background: rgba(0, 0, 0, 0.9);
        border: 1px solid rgba(0, 255, 255, 0.3);
        border-radius: 8px;
        padding: 0;
        backdrop-filter: blur(8px);
      }

      .vessel-popup .leaflet-popup-content {
        margin: 0;
        padding: 12px;
        color: #ffffff;
        font-family: 'Inter', sans-serif;
        font-size: 13px;
        line-height: 1.5;
      }

      .vessel-popup .leaflet-popup-tip {
        background: rgba(0, 0, 0, 0.9);
        border: 1px solid rgba(0, 255, 255, 0.3);
      }

      .vessel-popup-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;
        padding-bottom: 8px;
        border-bottom: 1px solid rgba(0, 255, 255, 0.2);
      }

      .vessel-popup-icon {
        font-size: 24px;
      }

      .vessel-popup-title {
        flex: 1;
      }

      .vessel-popup-name {
        font-size: 14px;
        font-weight: 700;
        color: rgba(0, 255, 255, 0.95);
        margin-bottom: 2px;
      }

      .vessel-popup-mmsi {
        font-size: 11px;
        color: rgba(255, 255, 255, 0.6);
        font-family: 'Roboto Mono', monospace;
      }

      .vessel-popup-info {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: 6px 12px;
        font-size: 12px;
      }

      .vessel-popup-label {
        color: rgba(255, 255, 255, 0.6);
        font-weight: 500;
      }

      .vessel-popup-value {
        color: rgba(255, 255, 255, 0.95);
        font-family: 'Roboto Mono', monospace;
        font-size: 11px;
      }

      .vessel-popup-status {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 10px;
        font-weight: 600;
        text-transform: uppercase;
      }

      .vessel-popup-footer {
        margin-top: 8px;
        padding-top: 8px;
        border-top: 1px solid rgba(0, 255, 255, 0.2);
        font-size: 10px;
        color: rgba(255, 255, 255, 0.5);
        text-align: center;
      }
    `}</style>
  );
}

function createPopupContent(vessel: VesselPosition): string {
  const statusColor = getStatusColor(vessel.status);
  const vesselColor = getVesselColor(vessel);
  const rotation = vessel.course !== null && vessel.course !== undefined ? vessel.course : 0;
  const vesselArrowSVG = createVesselArrowSVG(vesselColor, rotation);
  
  return `
    <div class="vessel-popup-header">
      <div class="vessel-popup-icon">${vesselArrowSVG}</div>
      <div class="vessel-popup-title">
        <div class="vessel-popup-name">${vessel.vesselName || 'Unknown Vessel'}</div>
        <div class="vessel-popup-mmsi">MMSI: ${vessel.mmsi}</div>
      </div>
    </div>
    <div class="vessel-popup-info">
      <span class="vessel-popup-label">Status:</span>
      <span class="vessel-popup-value">
        <span class="vessel-popup-status" style="background: ${statusColor}; color: #000;">
          ${vessel.status || 'Unknown'}
        </span>
      </span>
      
      <span class="vessel-popup-label">Speed:</span>
      <span class="vessel-popup-value">${formatSpeed(vessel.speed)} knots</span>
      
      <span class="vessel-popup-label">Course:</span>
      <span class="vessel-popup-value">${formatCourse(vessel.course)}</span>
      
      ${vessel.vesselType ? `
        <span class="vessel-popup-label">Type:</span>
        <span class="vessel-popup-value">${vessel.vesselType}</span>
      ` : ''}
      
      ${vessel.destination ? `
        <span class="vessel-popup-label">Destination:</span>
        <span class="vessel-popup-value">${vessel.destination}</span>
      ` : ''}
      
      ${vessel.callSign ? `
        <span class="vessel-popup-label">Call Sign:</span>
        <span class="vessel-popup-value">${vessel.callSign}</span>
      ` : ''}
      
      <span class="vessel-popup-label">Position:</span>
      <span class="vessel-popup-value">${formatCoordinates(vessel.latitude, vessel.longitude)}</span>
    </div>
    <div class="vessel-popup-footer">
      Last update: ${formatTimestamp(vessel.timestamp)} | Data: Mock AIS
    </div>
  `;
}
