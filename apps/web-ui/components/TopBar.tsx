'use client';

import { Search, Bell, User } from 'lucide-react';

export default function TopBar() {
  return (
    <div className="h-14 border-b border-[#E0E0E0] px-6 flex items-center justify-between bg-white">
      {/* Left side - Logo and Nav Links */}
      <div className="flex items-center gap-6">
        <div className="text-lg font-semibold text-[#00A0E3]">
          Vessel Monitoring
        </div>
        <div className="flex items-center gap-4">
          <button className="text-sm text-[#333333] hover:text-[#00A0E3] transition-colors">
            Edit
          </button>
          <button className="text-sm text-[#333333] hover:text-[#00A0E3] transition-colors">
            History
          </button>
          <button className="text-sm text-[#333333] hover:text-[#00A0E3] transition-colors">
            Export
          </button>
        </div>
      </div>

      {/* Center - Search Bar */}
      <div className="flex items-center gap-2 w-80 h-9 bg-white border border-[#E0E0E0] rounded px-3">
        <Search className="w-[18px] h-[18px] text-[#999999] shrink-0" />
        <input
          type="text"
          placeholder="Search vessels..."
          className="flex-1 bg-transparent text-sm text-[#333333] placeholder:text-[#999999] outline-none min-w-0"
        />
      </div>

      {/* Right side - User controls */}
      <div className="flex items-center gap-4">
        <button 
          className="w-9 h-9 flex items-center justify-center hover:bg-[#F5F5F5] rounded transition-colors"
          title="Notifications"
        >
          <Bell className="w-5 h-5 text-[#666666]" />
        </button>
        <button 
          className="w-9 h-9 flex items-center justify-center hover:bg-[#F5F5F5] rounded transition-colors"
          title="User Profile"
        >
          <User className="w-5 h-5 text-[#666666]" />
        </button>
      </div>
    </div>
  );
}
