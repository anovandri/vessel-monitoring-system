'use client';

import { Search, Bell, User, X } from 'lucide-react';
import { useState, useEffect, useRef } from 'react';

interface VesselSearchResult {
  mmsi: number;
  vessel_name: string;
  vessel_type: string;
  latitude?: number;
  longitude?: number;
}

interface TopBarProps {
  onHistoryClick?: () => void;
  onVesselSelect?: (vessel: VesselSearchResult) => void;
}

export default function TopBar({ onHistoryClick, onVesselSelect }: TopBarProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<VesselSearchResult[]>([]);
  const [showSearchDropdown, setShowSearchDropdown] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const searchTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  // Wrap the entire search widget (input + dropdown) for outside-click detection
  const searchWidgetRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (searchWidgetRef.current && !searchWidgetRef.current.contains(event.target as Node)) {
        setShowSearchDropdown(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Search vessels with debouncing
  useEffect(() => {
    if (searchTimeoutRef.current) {
      clearTimeout(searchTimeoutRef.current);
    }

    if (searchQuery.trim().length < 2) {
      setSearchResults([]);
      setShowSearchDropdown(false);
      return;
    }

    setIsSearching(true);
    searchTimeoutRef.current = setTimeout(async () => {
      try {
        const response = await fetch(
          `http://localhost:8082/api/vessels/history/search?query=${encodeURIComponent(searchQuery)}`
        );
        if (response.ok) {
          const data = await response.json();
          setSearchResults(data);
          setShowSearchDropdown(true);
        }
      } catch (error) {
        console.error('Error searching vessels:', error);
      } finally {
        setIsSearching(false);
      }
    }, 300); // 300ms debounce

    return () => {
      if (searchTimeoutRef.current) {
        clearTimeout(searchTimeoutRef.current);
      }
    };
  }, [searchQuery]);

  const handleVesselSelect = async (vessel: VesselSearchResult) => {
    setSearchQuery(vessel.vessel_name);
    setShowSearchDropdown(false);
    
    // Fetch full vessel info with coordinates
    try {
      const response = await fetch(`http://localhost:8082/api/vessels/history/${vessel.mmsi}/info`);
      if (response.ok) {
        const vesselInfo = await response.json();
        onVesselSelect?.({
          ...vessel,
          latitude: vesselInfo.latitude,
          longitude: vesselInfo.longitude
        });
      } else {
        onVesselSelect?.(vessel);
      }
    } catch (error) {
      console.error('Error fetching vessel info:', error);
      onVesselSelect?.(vessel);
    }
  };

  const handleClearSearch = () => {
    setSearchQuery('');
    setSearchResults([]);
    setShowSearchDropdown(false);
  };

  return (
    <div className="h-14 border-b border-[#E0E0E0] px-6 flex items-center justify-between bg-white relative z-[10000]">
      {/* Left side - Logo and Nav Links */}
      <div className="flex items-center gap-6">
        <div className="text-lg font-semibold text-[#00A0E3]">
          Vessel Monitoring
        </div>
        <div className="flex items-center gap-4">
          <button className="text-sm text-[#333333] hover:text-[#00A0E3] transition-colors">
            Edit
          </button>
          <button 
            onClick={onHistoryClick}
            className="text-sm text-[#333333] hover:text-[#00A0E3] transition-colors"
          >
            History
          </button>
          <button className="text-sm text-[#333333] hover:text-[#00A0E3] transition-colors">
            Export
          </button>
        </div>
      </div>

      {/* Center - Search Bar with Autocomplete */}
      <div className="relative" ref={searchWidgetRef}>
        <div className="flex items-center gap-2 w-80 h-9 bg-white border border-[#E0E0E0] rounded px-3">
          <Search className="w-[18px] h-[18px] text-[#999999] shrink-0" />
          <input
            type="text"
            placeholder="Search vessels..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="flex-1 bg-transparent text-sm text-[#333333] placeholder:text-[#999999] outline-none min-w-0"
          />
          {searchQuery && (
            <button
              onClick={handleClearSearch}
              className="hover:bg-[#F5F5F5] rounded p-1 transition-colors"
            >
              <X className="w-[14px] h-[14px] text-[#999999]" />
            </button>
          )}
        </div>

        {/* Search Results Dropdown – fixed so it escapes Leaflet's stacking context */}
        {showSearchDropdown && (
          <div
            style={{ position: 'fixed', top: 56, zIndex: 99999 }}
            className="w-80 bg-white border border-[#E0E0E0] rounded shadow-lg max-h-80 overflow-y-auto"
          >
            {isSearching ? (
              <div className="px-4 py-3 text-sm text-[#999999]">Searching...</div>
            ) : searchResults.length > 0 ? (
              searchResults.map((vessel) => (
                <button
                  key={vessel.mmsi}
                  onClick={() => handleVesselSelect(vessel)}
                  className="w-full px-4 py-3 text-left hover:bg-[#F5F5F5] transition-colors border-b border-[#F0F0F0] last:border-b-0"
                >
                  <div className="font-medium text-sm text-[#333333]">{vessel.vessel_name}</div>
                  <div className="text-xs text-[#999999] mt-1">
                    MMSI: {vessel.mmsi} • Type: {vessel.vessel_type}
                  </div>
                </button>
              ))
            ) : (
              <div className="px-4 py-3 text-sm text-[#999999]">No vessels found</div>
            )}
          </div>
        )}
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
