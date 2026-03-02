"use client";

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

export default function Home() {
  return (
    <div className="h-screen bg-[#F5F5F5] overflow-hidden flex flex-col">
      {/* Top Bar - 56px height */}
      <TopBar />
      {/* Map Area - fills remaining height */}
      <div className="flex-1 overflow-hidden">
        <MapView />
      </div>
    </div>
  );
}
