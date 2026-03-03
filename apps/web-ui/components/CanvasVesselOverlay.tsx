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

function toNum(v: unknown): number {
  if (typeof v === 'number') return v;
  if (typeof v === 'string') return parseFloat(v);
  return NaN;
}

function createPopupContent(vessel: VesselPosition): string {
  const lat = toNum(vessel.latitude);
  const lon = toNum(vessel.longitude);
  const speed = !isNaN(toNum(vessel.speed)) ? toNum(vessel.speed).toFixed(1) : 'N/A';
  const course = !isNaN(toNum(vessel.course)) ? toNum(vessel.course).toFixed(0) + '°' : 'N/A';
  const coords = !isNaN(lat) && !isNaN(lon) ? `${lat.toFixed(6)}, ${lon.toFixed(6)}` : 'N/A';

  return `
    <div style="min-width:250px;font-family:Inter,sans-serif;">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;padding-bottom:8px;border-bottom:1px solid rgba(0,255,255,0.2);">
        <div style="font-size:22px;">🚢</div>
        <div>
          <div style="font-weight:600;font-size:14px;color:rgba(0,255,255,0.95);">${vessel.vesselName || 'Unknown Vessel'}</div>
          <div style="font-size:11px;color:rgba(255,255,255,0.6);">MMSI: ${vessel.mmsi}</div>
        </div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;font-size:12px;">
        <div>
          <div style="color:rgba(255,255,255,0.6);font-size:10px;margin-bottom:2px;">Speed</div>
          <div style="font-weight:500;">${speed} kn</div>
        </div>
        <div>
          <div style="color:rgba(255,255,255,0.6);font-size:10px;margin-bottom:2px;">Course</div>
          <div style="font-weight:500;">${course}</div>
        </div>
        <div style="grid-column:1/-1;">
          <div style="color:rgba(255,255,255,0.6);font-size:10px;margin-bottom:2px;">Position</div>
          <div style="font-size:11px;font-family:monospace;">${coords}</div>
        </div>
      </div>
    </div>`;
}

/**
 * Vessel overlay – synchronous Leaflet marker management (no requestAnimationFrame).
 *
 * WHY no RAF:  React's useEffect cleanup runs cancelAnimationFrame() every time the
 * `vessels` array reference changes (i.e. every WebSocket tick), so a RAF-based
 * approach always gets cancelled before it can execute.  Direct, synchronous updates
 * inside the effect body are the correct pattern here.
 */
export default function CanvasVesselOverlay({
  vessels,
  enabled,
  onVesselClick,
}: CanvasVesselOverlayProps) {
  const map = useMap();

  // Persistent marker store: mmsi → Leaflet Marker
  const markersRef = useRef<Map<number, L.Marker>>(new Map());

  // Keep click-handler ref fresh on every render without adding it to effect deps
  const onClickRef = useRef(onVesselClick);
  useEffect(() => { onClickRef.current = onVesselClick; });

  // ─── Main sync effect ────────────────────────────────────────────────────
  useEffect(() => {
    const markers = markersRef.current;

    // Disabled → remove everything immediately
    if (!enabled) {
      markers.forEach((m) => m.remove());
      markers.clear();
      return;
      // No cleanup needed here – we already removed everything
    }

    // Build set of incoming MMSIs
    const incomingIds = new Set<number>();

    vessels.forEach((vessel) => {
      const lat = toNum(vessel.latitude);
      const lon = toNum(vessel.longitude);
      if (isNaN(lat) || isNaN(lon)) return;

      incomingIds.add(vessel.mmsi);

      const color = getVesselColor(vessel);
      const rotation = !isNaN(toNum(vessel.course)) ? toNum(vessel.course) : 0;

      const existing = markers.get(vessel.mmsi);
      if (existing) {
        // ── Update position + rotation ──────────────────────────────────
        existing.setLatLng([lat, lon]);
        const el = existing.getElement();
        if (el) {
          const arrow = el.querySelector<HTMLElement>('.vessel-arrow-inner');
          if (arrow) arrow.style.transform = `rotate(${rotation}deg)`;
        }
      } else {
        // ── Create new Leaflet marker ───────────────────────────────────
        const icon = L.divIcon({
          html: `<div class="vessel-arrow-inner" style="transform:rotate(${rotation}deg);width:16px;height:16px;">
                   <svg width="16" height="16" viewBox="0 0 16 16" xmlns="http://www.w3.org/2000/svg">
                     <path d="M8 1 L14 14 L8 12 L2 14 Z" fill="${color}" stroke="#000" stroke-width="0.8" opacity="0.9"/>
                   </svg>
                 </div>`,
          className: 'vessel-marker-icon',
          iconSize: [16, 16],
          iconAnchor: [8, 8],
          popupAnchor: [0, -10],
        });

        const marker = L.marker([lat, lon], { icon, interactive: true });

        marker.bindTooltip(
          `<strong style="color:rgba(0,255,255,0.95)">${vessel.vesselName || 'Unknown'}</strong>`,
          { className: 'vessel-tooltip', direction: 'top', offset: [0, -10] }
        );

        marker.bindPopup(createPopupContent(vessel), {
          maxWidth: 300,
          className: 'vessel-popup',
        });

        marker.on('click', () => onClickRef.current?.(vessel));

        marker.addTo(map);
        markers.set(vessel.mmsi, marker);
      }
    });

    // Remove stale markers (vessels that are no longer in the incoming list)
    markers.forEach((_marker, mmsi) => {
      if (!incomingIds.has(mmsi)) {
        _marker.remove();
        markers.delete(mmsi);
      }
    });

    // ⚠️  No cleanup return that removes markers – doing so would wipe them
    //     every time `vessels` updates (i.e. every WebSocket tick).
  }, [vessels, enabled, map]);

  // ─── Unmount cleanup ─────────────────────────────────────────────────────
  useEffect(() => {
    const markers = markersRef.current;
    return () => {
      markers.forEach((m) => m.remove());
      markers.clear();
    };
  }, []);

  return (
    <style jsx global>{`
      .vessel-marker-icon {
        background: transparent !important;
        border: none !important;
      }
      .vessel-arrow-inner {
        filter: drop-shadow(0 1px 3px rgba(0,0,0,0.6));
      }
      .vessel-tooltip {
        background: rgba(0,0,0,0.85) !important;
        border: 1px solid rgba(0,255,255,0.35) !important;
        border-radius: 4px !important;
        padding: 3px 8px !important;
        color: #fff;
        font-family: Inter, sans-serif;
        font-size: 12px;
      }
      .vessel-popup .leaflet-popup-content-wrapper {
        background: rgba(10,15,30,0.95);
        border: 1px solid rgba(0,255,255,0.3);
        border-radius: 8px;
        padding: 0;
        color: #fff;
        backdrop-filter: blur(8px);
      }
      .vessel-popup .leaflet-popup-content {
        margin: 12px;
        font-family: Inter, sans-serif;
      }
      .vessel-popup .leaflet-popup-tip {
        background: rgba(10,15,30,0.95);
      }
    `}</style>
  );
}
