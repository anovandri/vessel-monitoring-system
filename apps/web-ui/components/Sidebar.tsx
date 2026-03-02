"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { 
  Anchor, 
  LayoutDashboard, 
  Map, 
  Search,
  TriangleAlert, 
  TrendingUp,
  History,
  Settings
} from "lucide-react";

const navItems = [
  { icon: LayoutDashboard, label: "Dashboard", href: "/" },
  { icon: Map, label: "Map", href: "/map" },
  { icon: Search, label: "Search", href: "/search" },
  { icon: TriangleAlert, label: "Alerts", href: "/alerts" },
  { icon: TrendingUp, label: "Analytics", href: "/analytics" },
  { icon: History, label: "History", href: "/history" },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="fixed left-0 top-0 h-screen w-20 bg-[#060912] flex flex-col items-center py-6 z-50 overflow-visible">
      {/* Logo - 48px height, 32px icon */}
      <div className="flex items-center justify-center h-12 w-20 group relative overflow-visible">
        <Anchor className="w-8 h-8 text-[#00E5FF]" />
        <span className="absolute left-full ml-3 px-4 py-2 bg-[#1A2332] text-[#E8F0F8] text-sm font-medium rounded-md opacity-0 group-hover:opacity-100 transition-opacity duration-200 whitespace-nowrap shadow-xl border border-[#00E5FF]/20 pointer-events-none">
          Vessel Monitoring System
        </span>
      </div>

      {/* Gap between logo and nav - 24px */}
      <div className="h-6" />

      {/* Navigation Items - 56px height each, 8px gap */}
      <nav className="flex flex-col gap-2 w-full">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`group relative flex items-center justify-center h-14 w-full transition-all overflow-visible ${
                isActive
                  ? "bg-[#00E5FF]/10 border-l-[3px] border-[#00E5FF]"
                  : "hover:bg-[#0A0E1A]/50"
              }`}
            >
              <Icon
                className={`w-6 h-6 ${
                  isActive ? "text-[#00E5FF]" : "text-[#8B95A8]"
                }`}
              />
              <span className="absolute left-full ml-3 px-4 py-2 bg-[#1A2332] text-[#E8F0F8] text-sm font-medium rounded-md opacity-0 group-hover:opacity-100 transition-opacity duration-200 whitespace-nowrap shadow-xl border border-[#00E5FF]/20 pointer-events-none">
                {item.label}
              </span>
            </Link>
          );
        })}
      </nav>

      {/* Spacer to push settings to bottom */}
      <div className="flex-1" />

      {/* Settings - 56px height at bottom */}
      <Link
        href="/settings"
        className="group relative flex items-center justify-center h-14 w-full hover:bg-[#0A0E1A]/50 transition-all overflow-visible"
      >
        <Settings className="w-6 h-6 text-[#8B95A8]" />
        <span className="absolute left-full ml-3 px-4 py-2 bg-[#1A2332] text-[#E8F0F8] text-sm font-medium rounded-md opacity-0 group-hover:opacity-100 transition-opacity duration-200 whitespace-nowrap shadow-xl border border-[#00E5FF]/20 pointer-events-none">
          Settings
        </span>
      </Link>
    </aside>
  );
}
