'use client';

import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import { ZoomIn, ZoomOut, Layers, Maximize2 } from 'lucide-react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

// Fix for default marker icon
if (typeof window !== 'undefined') {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  delete (L.Icon.Default.prototype as any)._getIconUrl;
  L.Icon.Default.mergeOptions({
    iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
    iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
    shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  });
}

function MapControls() {
  const map = useMap();

  return (
    <div className="absolute top-10 left-10 z-1000 flex flex-col gap-2">
      {/* Zoom In */}
      <button
        onClick={() => map.zoomIn()}
        className="w-12 h-12 bg-[#0F1420] border border-[#1A2332] rounded-lg flex items-center justify-center hover:bg-[#0A0E1A] transition-colors"
        title="Zoom In"
      >
        <ZoomIn className="w-5 h-5 text-[#8B95A8]" />
      </button>

      {/* Zoom Out */}
      <button
        onClick={() => map.zoomOut()}
        className="w-12 h-12 bg-[#0F1420] border border-[#1A2332] rounded-lg flex items-center justify-center hover:bg-[#0A0E1A] transition-colors"
        title="Zoom Out"
      >
        <ZoomOut className="w-5 h-5 text-[#8B95A8]" />
      </button>

      {/* Layers */}
      <button
        className="w-12 h-12 bg-[#0F1420] border border-[#1A2332] rounded-lg flex items-center justify-center hover:bg-[#0A0E1A] transition-colors"
        title="Layers"
      >
        <Layers className="w-5 h-5 text-[#8B95A8]" />
      </button>

      {/* Fullscreen */}
      <button
        className="w-12 h-12 bg-[#0F1420] border border-[#1A2332] rounded-lg flex items-center justify-center hover:bg-[#0A0E1A] transition-colors"
        title="Fullscreen"
      >
        <Maximize2 className="w-5 h-5 text-[#8B95A8]" />
      </button>
    </div>
  );
}

export default function MapView() {
  return (
    <div className="relative w-full h-full bg-[#1A2332]">
      <MapContainer
        center={[-2.5, 118.0]}
        zoom={5}
        zoomControl={false}
        style={{ height: '100%', width: '100%', background: '#2A3B4D' }}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        />
        <MapControls />
      </MapContainer>
    </div>
  );
}
