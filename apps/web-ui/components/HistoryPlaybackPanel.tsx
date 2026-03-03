'use client';

import { useState, useEffect, useRef } from 'react';
import { X, Calendar, ChevronDown, Play, Pause, SkipBack, SkipForward } from 'lucide-react';

interface VesselInfo {
  mmsi: number;
  vessel_name: string;
  vessel_type: string;
  country?: string;
  flag_state?: string;
  imo_number?: string;
  callsign?: string;
  latitude?: number;
  longitude?: number;
}

interface VesselHistoryPoint {
  mmsi: number;
  vessel_name: string;
  latitude: number;
  longitude: number;
  speed: number;
  course: number;
  heading: number;
  navigational_status: number;
  timestamp: number;
}

interface HistoryPlaybackPanelProps {
  onClose: () => void;
  onVesselSelect: (vessel: VesselInfo | null) => void;
  onPlaybackStateChange: (state: {
    isPlaying: boolean;
    currentIndex: number;
    historyData: VesselHistoryPoint[];
    playbackSpeed: number;
  }) => void;
}

export default function HistoryPlaybackPanel({
  onClose,
  onVesselSelect,
  onPlaybackStateChange
}: HistoryPlaybackPanelProps) {
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');
  const [selectedVessel, setSelectedVessel] = useState<VesselInfo | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<VesselInfo[]>([]);
  const [showSearchDropdown, setShowSearchDropdown] = useState(false);
  
  const [historyData, setHistoryData] = useState<VesselHistoryPoint[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const playbackInterval = useRef<NodeJS.Timeout | null>(null);
  const startDateRef = useRef<HTMLInputElement>(null);
  const endDateRef = useRef<HTMLInputElement>(null);

  // Initialize dates (last 7 days)
  useEffect(() => {
    const end = new Date();
    const start = new Date();
    start.setDate(start.getDate() - 7);
    
    setEndDate(end.toISOString().slice(0, 16));
    setStartDate(start.toISOString().slice(0, 16));
  }, []);

  // Search vessels with debounce
  useEffect(() => {
    if (searchQuery.length < 2) {
      setSearchResults([]);
      setShowSearchDropdown(false);
      return;
    }

    const debounce = setTimeout(async () => {
      try {
        const response = await fetch(
          `http://localhost:8082/api/vessels/history/search?query=${encodeURIComponent(searchQuery)}&limit=10`
        );
        if (response.ok) {
          const data = await response.json();
          setSearchResults(data);
          if (data.length > 0) {
            setShowSearchDropdown(true);
          }
        }
      } catch (err) {
        console.error('Error searching vessels:', err);
      }
    }, 300);

    return () => clearTimeout(debounce);
  }, [searchQuery]);

  // Handle vessel selection
  const handleVesselSelect = async (vessel: VesselInfo) => {
    console.log('Vessel selected:', vessel);
    setSelectedVessel(vessel);
    setSearchQuery(vessel.vessel_name);
    setShowSearchDropdown(false);
    setError(null);
    
    // Fetch vessel info with last known position
    try {
      const response = await fetch(`http://localhost:8082/api/vessels/history/${vessel.mmsi}/info`);
      if (response.ok) {
        const vesselInfo = await response.json();
        console.log('Vessel info with position:', vesselInfo);
        // Pass vessel info with coordinates to parent
        onVesselSelect({
          ...vessel,
          latitude: vesselInfo.latitude,
          longitude: vesselInfo.longitude
        });
      } else {
        // If we can't get position, just pass the vessel info
        onVesselSelect(vessel);
      }
    } catch (err) {
      console.error('Error fetching vessel info:', err);
      // If we can't get position, just pass the vessel info
      onVesselSelect(vessel);
    }
  };

  // Load history data
  const loadHistory = async () => {
    if (!selectedVessel || !startDate || !endDate) {
      setError('Please select a vessel and date range');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch(
        `http://localhost:8082/api/vessels/history/${selectedVessel.mmsi}?startDate=${startDate}&endDate=${endDate}`
      );

      if (!response.ok) {
        throw new Error(`Failed to fetch history: ${response.statusText}`);
      }

      const data = await response.json();
      setHistoryData(data);
      setCurrentIndex(0);
      setIsPlaying(false);
      
      if (data.length === 0) {
        setError('No history data found for this vessel in the selected period');
      }
    } catch (err) {
      console.error('Error loading history:', err);
      setError(err instanceof Error ? err.message : 'Failed to load history');
    } finally {
      setIsLoading(false);
    }
  };

  // Playback controls
  useEffect(() => {
    if (!isPlaying || historyData.length === 0) {
      if (playbackInterval.current) {
        clearInterval(playbackInterval.current);
        playbackInterval.current = null;
      }
      return;
    }

    const interval = 1000 / playbackSpeed; // Speed adjustment
    playbackInterval.current = setInterval(() => {
      setCurrentIndex(prev => {
        if (prev >= historyData.length - 1) {
          setIsPlaying(false);
          return prev;
        }
        return prev + 1;
      });
    }, interval);

    return () => {
      if (playbackInterval.current) {
        clearInterval(playbackInterval.current);
      }
    };
  }, [isPlaying, historyData.length, playbackSpeed]);

  // Notify parent of playback state changes
  useEffect(() => {
    onPlaybackStateChange({
      isPlaying,
      currentIndex,
      historyData,
      playbackSpeed
    });
  }, [isPlaying, currentIndex, historyData, playbackSpeed, onPlaybackStateChange]);

  const togglePlayPause = () => {
    if (historyData.length === 0) return;
    if (currentIndex >= historyData.length - 1) {
      setCurrentIndex(0);
      setIsPlaying(true);
    } else {
      setIsPlaying(!isPlaying);
    }
  };

  const skipBack = () => {
    setCurrentIndex(prev => Math.max(0, prev - 10));
  };

  const skipForward = () => {
    setCurrentIndex(prev => Math.min(historyData.length - 1, prev + 10));
  };

  const currentPosition = historyData[currentIndex];
  const progress = historyData.length > 0 ? (currentIndex / (historyData.length - 1)) * 100 : 0;

  return (
    <div className="fixed right-6 top-20 w-120 bg-white rounded-xl border border-[#E0E0E0] shadow-lg z-1100">
      <div className="p-6 flex flex-col gap-5 max-h-[calc(100vh-96px)] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-[#333333]">History Playback</h2>
          <button
            onClick={onClose}
            className="w-8 h-8 flex items-center justify-center hover:bg-[#F5F5F5] rounded-lg transition-colors"
          >
            <X className="w-5 h-5 text-[#666666]" />
          </button>
        </div>

        {/* Date Range Section */}
        <div className="flex flex-col gap-3">
          <label className="text-[13px] font-medium text-[#666666]">
            Select Date Range
          </label>
          <div className="grid grid-cols-2 gap-3">
            {/* Start date */}
            <div className="relative">
              <button
                type="button"
                onClick={() => startDateRef.current?.showPicker()}
                className="flex items-center gap-2 h-11 w-full bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg px-3 hover:border-[#00A0E3] transition-colors"
              >
                <Calendar className="w-4 h-4 text-[#999999] shrink-0" />
                <span className="text-[13px] text-[#333333] flex-1 text-left truncate">
                  {startDate
                    ? new Date(startDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
                    : 'Start date'}
                </span>
              </button>
              <input
                ref={startDateRef}
                type="date"
                value={startDate ? startDate.slice(0, 10) : ''}
                onChange={(e) => {
                  const d = e.target.value;
                  setStartDate(d ? `${d}T00:00` : '');
                }}
                className="sr-only"
              />
            </div>
            {/* End date */}
            <div className="relative">
              <button
                type="button"
                onClick={() => endDateRef.current?.showPicker()}
                className="flex items-center gap-2 h-11 w-full bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg px-3 hover:border-[#00A0E3] transition-colors"
              >
                <Calendar className="w-4 h-4 text-[#999999] shrink-0" />
                <span className="text-[13px] text-[#333333] flex-1 text-left truncate">
                  {endDate
                    ? new Date(endDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
                    : 'End date'}
                </span>
              </button>
              <input
                ref={endDateRef}
                type="date"
                value={endDate ? endDate.slice(0, 10) : ''}
                onChange={(e) => {
                  const d = e.target.value;
                  setEndDate(d ? `${d}T23:59` : '');
                }}
                className="sr-only"
              />
            </div>
          </div>
        </div>

        {/* Vessel Search Section */}
        <div className="flex flex-col gap-3">
          <label className="text-[13px] font-medium text-[#666666]">
            Select Vessel
          </label>
          <div className="relative">
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => {
                if (searchResults.length > 0) {
                  setShowSearchDropdown(true);
                }
              }}
              placeholder={selectedVessel ? selectedVessel.vessel_name : "MV PACIFIC EXPLORER"}
              className="w-full h-11 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg px-4 pr-10 text-[13px] text-[#333333] placeholder:text-[#333333] focus:outline-none focus:border-[#00A0E3] transition-colors"
            />
            <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[#999999] pointer-events-none" />

            {/* Search Results Dropdown */}
            {showSearchDropdown && searchResults.length > 0 && (
              <div className="absolute top-full left-0 right-0 mt-1 bg-white border border-[#E0E0E0] rounded-lg shadow-lg max-h-50 overflow-y-auto z-20">
                {searchResults.map((vessel) => (
                  <button
                    key={vessel.mmsi}
                    onClick={() => handleVesselSelect(vessel)}
                    className="w-full px-4 py-3 text-left hover:bg-[#F5F5F5] transition-colors border-b border-[#E0E0E0] last:border-b-0"
                  >
                    <div className="text-[13px] font-medium text-[#333333]">{vessel.vessel_name}</div>
                    <div className="text-xs text-[#666666] mt-0.5">
                      MMSI: {vessel.mmsi}{vessel.vessel_type ? ` • ${vessel.vessel_type}` : ''}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* Playback Controls Section */}
        <div className="flex flex-col gap-4">
          <label className="text-[13px] font-medium text-[#666666]">
            Playback Controls
          </label>

          {/* Play/Pause/Skip Buttons */}
          <div className="flex items-center justify-center gap-3">
            <button
              onClick={skipBack}
              disabled={historyData.length === 0}
              className="w-12 h-12 flex items-center justify-center bg-[#F5F5F5] border border-[#E0E0E0] hover:bg-[#E0E0E0] disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors"
            >
              <SkipBack className="w-5 h-5 text-[#333333]" />
            </button>

            <button
              onClick={togglePlayPause}
              disabled={historyData.length === 0}
              className="w-14 h-14 flex items-center justify-center bg-[#00A0E3] hover:bg-[#008FC7] disabled:opacity-50 disabled:cursor-not-allowed rounded-full transition-colors"
            >
              {isPlaying ? (
                <Pause className="w-6 h-6 text-white" />
              ) : (
                <Play className="w-6 h-6 text-white ml-0.5" />
              )}
            </button>

            <button
              onClick={skipForward}
              disabled={historyData.length === 0}
              className="w-12 h-12 flex items-center justify-center bg-[#F5F5F5] border border-[#E0E0E0] hover:bg-[#E0E0E0] disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors"
            >
              <SkipForward className="w-5 h-5 text-[#333333]" />
            </button>
          </div>

          {/* Speed Controls */}
          <div className="grid grid-cols-4 gap-3">
            {[0.5, 1, 2, 4].map(speed => (
              <button
                key={speed}
                onClick={() => setPlaybackSpeed(speed)}
                disabled={historyData.length === 0}
                className={`h-10 rounded-lg text-[13px] font-medium transition-colors ${
                  playbackSpeed === speed
                    ? 'bg-[#00A0E3] text-white font-semibold'
                    : 'bg-[#F5F5F5] text-[#666666] hover:bg-[#E0E0E0]'
                } disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                {speed}x
              </button>
            ))}
          </div>
        </div>

        {/* Timeline Section */}
        <div className="flex flex-col gap-3">
          <label className="text-[13px] font-medium text-[#666666]">
            Timeline
          </label>

          <div className="relative h-15 bg-[#F5F5F5] rounded-lg overflow-hidden">
            {historyData.length > 0 && (
              <>
                <div
                  className="absolute top-0 left-0 h-full bg-[#00A0E3]/20 transition-all duration-300"
                  style={{ width: `${progress}%` }}
                />
                <div
                  className="absolute top-3 w-1 h-9 bg-[#00A0E3] rounded-full transition-all duration-300"
                  style={{ left: `calc(${progress}% - 2px)` }}
                />
              </>
            )}

            <span className="absolute left-3 bottom-3 text-[11px] text-[#999999] font-medium">
              {startDate ? new Date(startDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : 'Start'}
            </span>
            <span className="absolute right-3 bottom-3 text-[11px] text-[#999999] font-medium">
              {endDate ? new Date(endDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : 'End'}
            </span>
          </div>
        </div>

        {/* Load History / Position Info */}
        {historyData.length === 0 ? (
          <>
            <button
              onClick={loadHistory}
              disabled={isLoading || !selectedVessel}
              className="w-full h-11 bg-[#00A0E3] hover:bg-[#008FC7] disabled:bg-[#E0E0E0] disabled:text-[#999999] text-white font-semibold rounded-lg transition-colors text-[13px]"
            >
              {isLoading ? 'Loading...' : 'Load History'}
            </button>

            {error && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-lg">
                <p className="text-[13px] text-red-600">{error}</p>
              </div>
            )}
          </>
        ) : (
          <div className="grid grid-cols-3 gap-3 p-3 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg text-[13px]">
            <div>
              <div className="text-[#999999]">Position</div>
              <div className="text-[#333333] font-medium">{currentIndex + 1}/{historyData.length}</div>
            </div>
            <div>
              <div className="text-[#999999]">Speed</div>
              <div className="text-[#333333] font-medium">{currentPosition?.speed.toFixed(1) || '0.0'} kn</div>
            </div>
            <div>
              <div className="text-[#999999]">Course</div>
              <div className="text-[#333333] font-medium">{currentPosition?.course || '0'}°</div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
