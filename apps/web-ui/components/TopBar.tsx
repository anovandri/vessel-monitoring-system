'use client';

import { Search, Bell } from 'lucide-react';

export default function TopBar() {
  return (
    <div className="h-16 border-b border-[#1A2332] px-8 flex items-center justify-between">
      {/* Search Bar - 480px width, 40px height, 8px corner radius, 16px horizontal padding */}
      <div className="flex items-center gap-3 w-[480px] h-10 bg-[#0F1420] border border-[#1A2332] rounded-lg px-4 ml-16">
        <Search className="w-[18px] h-[18px] text-[#5A6478] shrink-0 ml-2" />
        <input
          type="text"
          placeholder="Search vessel by name, MMSI, IMO..."
          className="flex-1 bg-transparent text-sm text-[#E8F0F8] placeholder:text-[#5A6478] outline-none min-w-0"
        />
      </div>

      {/* Top Bar Right - 24px gap between items */}
      <div className="flex items-center gap-6">
        {/* Live Indicator - 8px blip, 8px gap */}
        <div className="flex items-center gap-2" title="Real-time data streaming active">
          <div className="w-2 h-2 rounded-full bg-[#00FF88] animate-pulse" />
          <span className="text-[#00FF88] font-mono text-xs font-semibold tracking-wider">
            LIVE DATA
          </span>
        </div>

        {/* Notification - 40px x 40px */}
        <button 
          className="w-10 h-10 flex items-center justify-center hover:bg-[#0F1420] rounded-lg transition-colors"
          title="Notifications"
        >
          <Bell className="w-5 h-5 text-[#8B95A8]" />
        </button>

        {/* User Profile - 32px avatar, 8px vertical + 12px horizontal padding, 8px corner radius, 12px gap */}
        <div className="flex items-center gap-3 bg-[#0F1420] rounded-lg px-3 py-2" title="User Profile">
          <div className="w-8 h-8 rounded-full bg-[#00E5FF]/20" />
          <span className="text-sm font-medium text-[#E8F0F8]">Captain Smith</span>
        </div>
      </div>
    </div>
  );
}
