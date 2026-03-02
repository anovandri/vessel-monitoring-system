"use client";

import Sidebar from "@/components/Sidebar";
import TopBar from "@/components/TopBar";
import dynamic from "next/dynamic";

const MapView = dynamic(() => import("@/components/MapView"), {
  ssr: false,
  loading: () => (
    <div className="w-full h-full flex items-center justify-center bg-[#1A2332]">
      <div className="text-[#8B95A8]">Loading map...</div>
    </div>
  ),
});

export default function Home() {
  return (
    <div className="h-screen bg-[#0A0E1A] overflow-hidden relative">
      <Sidebar />
      {/* Main Container - absolute positioned, starts at left: 80px */}
      <main className="absolute left-20 top-0 right-0 bottom-0 flex flex-col">
        {/* Top Bar - 64px height */}
        <TopBar />
        {/* Map Area - fills remaining height */}
        <div className="flex-1 overflow-hidden">
          <MapView />
        </div>
      </main>
    </div>
  );
}
