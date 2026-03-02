'use client';

import { useEffect, useRef } from 'react';
import { useMap } from 'react-leaflet';
import L from 'leaflet';
import { VesselPosition } from '@/lib/types/vessel';

interface CanvasVesselOverlayProps {
  vessels: VesselPosition[];
  enabled: boolean;
  onVesselClick?: (vessel: VesselPosition) => void;
}

// Vessel type to color mapping (MarineTraffic style)
const VESSEL_TYPE_COLORS: Record<string, string> = {
  cargo: '#FF6B00',
  tanker: '#FF0000',
  passenger: '#00FF00',
  fishing: '#00FFFF',
  tug: '#FFFF00',
  pleasure: '#FF00FF',
  sailing: '#00BFFF',
  military: '#808080',
  unknown: '#FFFFFF',
};

function getVesselColor(vessel: VesselPosition): string {
  const type = vessel.vesselType?.toLowerCase() || 'unknown';
  
  if (type.includes('cargo')) return VESSEL_TYPE_COLORS.cargo;
  if (type.includes('tanker')) return VESSEL_TYPE_COLORS.tanker;
  if (type.includes('passenger')) return VESSEL_TYPE_COLORS.passenger;
  if (type.includes('fishing')) return VESSEL_TYPE_COLORS.fishing;
  if (type.includes('tug')) return VESSEL_TYPE_COLORS.tug;
  
  return VESSEL_TYPE_COLORS.unknown;
}

/**
 * High-performance Canvas-based vessel overlay
 * Uses Leaflet Canvas renderer for optimal performance with hundreds of vessels
 */
export default function CanvasVesselOverlay({ 
  vessels, 
  enabled,
  onVesselClick 
}: CanvasVesselOverlayProps) {
  const map = useMap();
  const canvasLayerRef = useRef<L.Canvas | null>(null);
  const vesselsMapRef = useRef<Map<number, { marker: L.Marker; vessel: VesselPosition }>>(new Map());
  const animationFrameRef = useRef<number | null>(null);
  const needsUpdateRef = useRef(false);

  // Initialize canvas renderer
  useEffect(() => {
    if (!canvasLayerRef.current) {
      canvasLayerRef.current = L.canvas({ padding: 0.5 });
    }
  }, []);

  // Batch update function using requestAnimationFrame
  useEffect(() => {
    const updateMarkers = () => {
      if (!enabled || !needsUpdateRef.current) {
        animationFrameRef.current = null;
        return;
      }

      const vesselsMap = vesselsMapRef.current;
      const currentVesselIds = new Set(vessels.map(v => v.mmsi));
      const existingVesselIds = new Set(vesselsMap.keys());

      // Remove vessels that are no longer present
      existingVesselIds.forEach(mmsi => {
        if (!currentVesselIds.has(mmsi)) {
          const entry = vesselsMap.get(mmsi);
          if (entry) {
            entry.marker.remove();
            vesselsMap.delete(mmsi);
          }
        }
      });

      // Update or create markers
      vessels.forEach((vessel) => {
        if (typeof vessel.latitude !== 'number' || typeof vessel.longitude !== 'number') {
          return;
        }

        const position = L.latLng(vessel.latitude, vessel.longitude);
        const color = getVesselColor(vessel);
        const rotation = vessel.course !== null && vessel.course !== undefined ? vessel.course : 0;
        const existingEntry = vesselsMap.get(vessel.mmsi);

        if (existingEntry) {
          // Update existing marker position and rotation without recreating
          existingEntry.marker.setLatLng(position);
          existingEntry.vessel = vessel;
          // Update the icon rotation
          const iconElement = existingEntry.marker.getElement();
          if (iconElement) {
            const arrow = iconElement.querySelector('.vessel-arrow-svg');
            if (arrow) {
              (arrow as HTMLElement).style.transform = `rotate(${rotation}deg)`;
            }
          }
        } else {
          // Create new arrow marker using DivIcon for better control
          const icon = L.divIcon({
            html: `
              <div class="vessel-arrow-svg" style="transform: rotate(${rotation}deg); width: 16px; height: 16px;">
                <svg width="16" height="16" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
                  <path d="M8 1 L14 14 L8 12 L2 14 Z" fill="${color}" stroke="#000" stroke-width="0.8" opacity="0.9"/>
                </svg>
              </div>
            `,
            className: 'vessel-arrow-marker',
            iconSize: [16, 16],
            iconAnchor: [8, 8],
            popupAnchor: [0, -8],
          });

          const marker = L.marker(position, {
            icon: icon,
            interactive: true,
          });

          // Add tooltip
          marker.bindTooltip(
            `<div style="font-family: Inter, sans-serif; font-size: 12px;">
              <strong style="color: rgba(0, 255, 255, 0.95);">${vessel.vesselName || 'Unknown'}</strong><br/>
              <span style="color: rgba(255, 255, 255, 0.6); font-size: 10px;">Data: Mock AIS</span>
            </div>`,
            {
              className: 'vessel-canvas-tooltip',
              direction: 'top',
              offset: [0, -8],
            }
          );

          // Add popup with detailed info
          marker.bindPopup(createPopupContent(vessel), {
            maxWidth: 300,
            className: 'vessel-popup',
          });

          // Add click handler
          if (onVesselClick) {
            marker.on('click', () => onVesselClick(vessel));
          }

          marker.addTo(map);
          vesselsMap.set(vessel.mmsi, { marker, vessel });
        }
      });

      needsUpdateRef.current = false;
      animationFrameRef.current = null;
    };

    // Schedule update if needed
    if (enabled && vessels.length > 0 && !animationFrameRef.current) {
      needsUpdateRef.current = true;
      animationFrameRef.current = requestAnimationFrame(updateMarkers);
    }

    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, [vessels, enabled, map, onVesselClick]);

  // Cleanup on unmount
  useEffect(() => {
    const vesselsMap = vesselsMapRef.current;
    return () => {
      vesselsMap.forEach(entry => entry.marker.remove());
      vesselsMap.clear();
    };
  }, []);

  return (
    <style jsx global>{`
      .vessel-arrow-marker {
        background: transparent !important;
        border: none !important;
      }

      .vessel-arrow-svg {
        transition: transform 0.3s ease-out;
        filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.5));
      }

      .vessel-canvas-tooltip {
        background: rgba(0, 0, 0, 0.85) !important;
        border: 1px solid rgba(0, 255, 255, 0.3) !important;
        border-radius: 4px !important;
        padding: 4px 8px !important;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.5) !important;
        color: #ffffff;
      }

      .vessel-canvas-tooltip .leaflet-tooltip-left::before,
      .vessel-canvas-tooltip .leaflet-tooltip-right::before {
        border-left-color: rgba(0, 0, 0, 0.85) !important;
        border-right-color: rgba(0, 0, 0, 0.85) !important;
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
    `}</style>
  );
}

function createPopupContent(vessel: VesselPosition): string {
  const speed = vessel.speed !== null && vessel.speed !== undefined 
    ? vessel.speed.toFixed(1) 
    : 'N/A';
  const course = vessel.course !== null && vessel.course !== undefined 
    ? vessel.course.toFixed(0) + '°' 
    : 'N/A';
  const coords = vessel.latitude !== null && vessel.longitude !== null
    ? `${vessel.latitude.toFixed(6)}, ${vessel.longitude.toFixed(6)}`
    : 'N/A';

  return `
    <div style="min-width: 250px;">
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px; padding-bottom: 8px; border-bottom: 1px solid rgba(0, 255, 255, 0.2);">
        <div style="font-size: 24px;">🚢</div>
        <div>
          <div style="font-weight: 600; font-size: 14px; color: rgba(0, 255, 255, 0.95);">${vessel.vesselName || 'Unknown Vessel'}</div>
          <div style="font-size: 11px; color: rgba(255, 255, 255, 0.6);">MMSI: ${vessel.mmsi}</div>
        </div>
      </div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-size: 12px;">
        <div>
          <div style="color: rgba(255, 255, 255, 0.6); font-size: 10px; margin-bottom: 2px;">Speed</div>
          <div style="font-weight: 500;">${speed} knots</div>
        </div>
        <div>
          <div style="color: rgba(255, 255, 255, 0.6); font-size: 10px; margin-bottom: 2px;">Course</div>
          <div style="font-weight: 500;">${course}</div>
        </div>
        <div style="grid-column: 1 / -1;">
          <div style="color: rgba(255, 255, 255, 0.6); font-size: 10px; margin-bottom: 2px;">Position</div>
          <div style="font-family: 'Roboto Mono', monospace; font-size: 11px;">${coords}</div>
        </div>
      </div>
    </div>
  `;
}
