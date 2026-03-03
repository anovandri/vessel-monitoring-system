"use client";

import { useState, useRef } from "react";
import TopBar from "@/components/TopBar";
import dynamic from "next/dynamic";

const MapView = dynamic(() => import("@/components/MapView"), {
  ssr: false,
  loading: () => (
    <div className="w-full h-full flex items-center justify-center bg-[#F0F0F0]">
      <div className="text-[#666666]">Loading map...</div>
    </div>
  ),
});

interface VesselSearchResult {
  mmsi: number;
  vessel_name: string;
  latitude?: number;
  longitude?: number;
}

export default function Home() {
  const [showHistoryPanel, setShowHistoryPanel] = useState(false);
  const mapPanToVesselRef = useRef<((vessel: VesselSearchResult) => void) | null>(null);

  const handleVesselSelectFromTopBar = (vessel: VesselSearchResult) => {
    if (mapPanToVesselRef.current) {
      mapPanToVesselRef.current(vessel);
    }
  };

  return (
    <div className="h-screen bg-[#F5F5F5] overflow-hidden flex flex-col">
      {/* Top Bar - 56px height */}
      <TopBar 
        onHistoryClick={() => setShowHistoryPanel(true)}
        onVesselSelect={handleVesselSelectFromTopBar}
      />
      {/* Map Area - fills remaining height */}
      <div className="flex-1 overflow-hidden">
        <MapView 
          showHistoryPanel={showHistoryPanel}
          onHideHistoryPanel={() => setShowHistoryPanel(false)}
          mapPanToVesselRef={mapPanToVesselRef}
        />
      </div>
    </div>
  );
}
