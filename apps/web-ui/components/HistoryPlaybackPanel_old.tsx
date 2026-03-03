'use client';

import { useState, useEffect, useRef } from 'react';
import { X, Calendar, ChevronDown, Play, Pause, SkipBack, SkipForward, Search } from 'lucide-react';

interface VesselInfo {
  mmsi: number;
  vessel_name: string;
  vessel_type: string;
  country?: string;
  flag_state?: string;
  imo_number?: string;
  callsign?: string;
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
  const [playbackSpeed, setPlaybackSpeed] = useState(1); // 0.5x, 1x, 2x, 4x
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const playbackInterval = useRef<NodeJS.Timeout | null>(null);

  // Initialize dates (last 7 days)
  useEffect(() => {
    const end = new Date();
    const start = new Date();
    start.setDate(start.getDate() - 7);
    
    setEndDate(end.toISOString().slice(0, 16));
    setStartDate(start.toISOString().slice(0, 16));
  }, []);

  // Search vessels
  useEffect(() => {
    if (searchQuery.length < 2) {
      setSearchResults([]);
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
          setShowSearchDropdown(true);
        }
      } catch (err) {
        console.error('Error searching vessels:', err);
      }
    }, 300);

    return () => clearTimeout(debounce);
  }, [searchQuery]);

  // Handle vessel selection
  const handleVesselSelect = (vessel: VesselInfo) => {
    setSelectedVessel(vessel);
    setSearchQuery(vessel.vessel_name);
    setShowSearchDropdown(false);
    setError(null);
    onVesselSelect(vessel);
  };

  // Load vessel history
  const loadHistory = async () => {
    if (!selectedVessel || !startDate || !endDate) {
      setError('Please select a vessel and date range');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch(
        `http://localhost:8082/api/vessels/history/${selectedVessel.mmsi}?` +
        `startDate=${encodeURIComponent(startDate)}&endDate=${encodeURIComponent(endDate)}`
      );

      if (!response.ok) {
        throw new Error('Failed to load vessel history');
      }

      const data: VesselHistoryPoint[] = await response.json();
      
      if (data.length === 0) {
        setError('No history data found for this vessel in the selected date range');
        setHistoryData([]);
        return;
      }

      setHistoryData(data);
      setCurrentIndex(0);
      setError(null);
    } catch (err) {
      console.error('Error loading history:', err);
      setError('Failed to load vessel history. Please try again.');
      setHistoryData([]);
    } finally {
      setIsLoading(false);
    }
  };

  // Playback controls
  useEffect(() => {
    if (isPlaying && historyData.length > 0) {
      const intervalMs = 1000 / playbackSpeed; // Base interval 1 second
      
      playbackInterval.current = setInterval(() => {
        setCurrentIndex(prev => {
          if (prev >= historyData.length - 1) {
            setIsPlaying(false);
            return prev;
          }
          return prev + 1;
        });
      }, intervalMs);
    } else {
      if (playbackInterval.current) {
        clearInterval(playbackInterval.current);
        playbackInterval.current = null;
      }
    }

    return () => {
      if (playbackInterval.current) {
        clearInterval(playbackInterval.current);
      }
    };
  }, [isPlaying, playbackSpeed, historyData.length]);

  // Notify parent of playback state changes
  useEffect(() => {
    onPlaybackStateChange({
      isPlaying,
      currentIndex,
      historyData,
      playbackSpeed
    });
  }, [isPlaying, currentIndex, historyData, playbackSpeed, onPlaybackStateChange]);

  const togglePlayback = () => {
    if (historyData.length === 0) {
      setError('Please load vessel history first');
      return;
    }
    setIsPlaying(!isPlaying);
  };

  const skipBackward = () => {
    setCurrentIndex(prev => Math.max(0, prev - 10));
  };

  const skipForward = () => {
    setCurrentIndex(prev => Math.min(historyData.length - 1, prev + 10));
  };

  const currentPosition = historyData[currentIndex];
  const progress = historyData.length > 0 ? (currentIndex / (historyData.length - 1)) * 100 : 0;

  return (
    <div className="fixed right-6 top-20 w-[480px] bg-white rounded-xl border border-[#E0E0E0] shadow-2xl z-1100">
      <div className="p-6 flex flex-col gap-5">
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
            <div className="relative">
              <Calendar className="absolute left-4 top-1/2 transform -translate-y-1/2 w-[18px] h-[18px] text-[#999999] pointer-events-none" />
              <input
                type="datetime-local"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="w-full h-11 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg pl-11 pr-4 text-[13px] text-[#333333] focus:outline-none focus:border-[#00A0E3] transition-colors"
              />
            </div>
            <div className="relative">
              <Calendar className="absolute left-4 top-1/2 transform -translate-y-1/2 w-[18px] h-[18px] text-[#999999] pointer-events-none" />
              <input
                type="datetime-local"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="w-full h-11 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg pl-11 pr-4 text-[13px] text-[#333333] focus:outline-none focus:border-[#00A0E3] transition-colors"
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
            <Search className="absolute left-4 top-1/2 transform -translate-y-1/2 w-[18px] h-[18px] text-[#999999] pointer-events-none" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                if (e.target.value.length >= 2) {
                  searchVessels(e.target.value);
                } else {
                  setSearchResults([]);
                  setShowSearchDropdown(false);
                }
              }}
              onFocus={() => {
                if (searchResults.length > 0) {
                  setShowSearchDropdown(true);
                }
              }}
              placeholder={selectedVessel ? selectedVessel.vessel_name : "Search vessel by name or MMSI..."}
              className="w-full h-11 bg-[#F5F5F5] border border-[#E0E0E0] rounded-lg pl-11 pr-12 text-[13px] text-[#333333] placeholder:text-[#999999] focus:outline-none focus:border-[#00A0E3] transition-colors"
            />
            <ChevronDown className="absolute right-4 top-1/2 transform -translate-y-1/2 w-[18px] h-[18px] text-[#999999] pointer-events-none" />
            
            {/* Search Results Dropdown */}
            {showSearchDropdown && searchResults.length > 0 && (
              <div className="absolute top-full left-0 right-0 mt-2 bg-white border border-[#E0E0E0] rounded-lg shadow-xl max-h-[200px] overflow-y-auto z-10">
                {searchResults.map((vessel) => (
                  <button
                    key={vessel.mmsi}
                    onClick={() => {
                      setSelectedVessel(vessel);
                      setSearchQuery(vessel.vessel_name);
                      setShowSearchDropdown(false);
                      onVesselSelect(vessel);
                      console.log('Vessel selected:', vessel);
                    }}
                    className="w-full px-4 py-3 text-left hover:bg-[#F5F5F5] transition-colors border-b border-[#E0E0E0] last:border-b-0"
                  >
                    <div className="text-[13px] font-medium text-[#333333]">{vessel.vessel_name}</div>
                    <div className="text-xs text-[#666666] mt-1">
                      MMSI: {vessel.mmsi} {vessel.vessel_type ? `• ${vessel.vessel_type}` : ''}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
        </div>
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onFocus={() => searchResults.length > 0 && setShowSearchDropdown(true)}
              placeholder="Search vessel by name..."
              className="w-full h-11 bg-[#0A0E1A] border border-[#1F2937] rounded-lg pl-11 pr-10 text-sm text-white placeholder-[#6B7280] focus:outline-none focus:border-[#22D3EE] transition-colors"
            />
            <ChevronDown className="absolute right-4 top-1/2 transform -translate-y-1/2 w-[18px] h-[18px] text-[#6B7280] pointer-events-none" />
            
            {/* Search Dropdown */}
            {showSearchDropdown && searchResults.length > 0 && (
              <div className="absolute top-full left-0 right-0 mt-2 bg-[#0F1419] border border-[#1F2937] rounded-lg shadow-xl max-h-[200px] overflow-y-auto z-10">
                {searchResults.map((vessel) => (
                  <button
                    key={vessel.mmsi}
                    onClick={() => handleVesselSelect(vessel)}
                    className="w-full px-4 py-3 text-left hover:bg-[#1F2937] transition-colors border-b border-[#1F2937] last:border-b-0"
                  >
                    <div className="text-sm font-medium text-white">{vessel.vessel_name}</div>
                    <div className="text-xs text-[#6B7280] mt-1">
                      MMSI: {vessel.mmsi} • {vessel.vessel_type || 'Unknown Type'}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </div>
          
          {selectedVessel && (
            <div className="mt-3 p-3 bg-[#0A0E1A] rounded-lg border border-[#1F2937]">
              <div className="text-sm font-medium text-white">{selectedVessel.vessel_name}</div>
              <div className="text-xs text-[#6B7280] mt-1">
                MMSI: {selectedVessel.mmsi} • {selectedVessel.vessel_type || 'Unknown'}
              </div>
            </div>
          )}
        </div>

        {/* Load History Button */}
        <button
          onClick={loadHistory}
          disabled={isLoading || !selectedVessel}
          className="w-full h-11 bg-[#22D3EE] hover:bg-[#06B6D4] disabled:bg-[#1F2937] disabled:text-[#6B7280] text-[#0A0E1A] font-semibold rounded-lg transition-colors mb-5"
        >
          {isLoading ? 'Loading...' : 'Load History'}
        </button>

        {/* Error Message */}
        {error && (
          <div className="mb-5 p-3 bg-red-500/10 border border-red-500/20 rounded-lg">
            <p className="text-sm text-red-400">{error}</p>
          </div>
        )}

        {/* Playback Controls Section */}
        {historyData.length > 0 && (
          <>
            <div className="mb-5">
              <h3 className="text-sm font-medium text-[#9CA3AF] mb-4">Playback Controls</h3>
              
              {/* Control Buttons */}
              <div className="flex items-center justify-center gap-3 mb-4">
                <button
                  onClick={skipBackward}
                  className="w-12 h-12 bg-[#0A0E1A] border border-[#1F2937] rounded-lg flex items-center justify-center hover:bg-[#1F2937] transition-colors"
                >
                  <SkipBack className="w-5 h-5 text-white" />
                </button>
                
                <button
                  onClick={togglePlayback}
                  className="w-14 h-14 bg-[#22D3EE] hover:bg-[#06B6D4] rounded-full flex items-center justify-center transition-colors"
                >
                  {isPlaying ? (
                    <Pause className="w-6 h-6 text-[#0A0E1A]" fill="currentColor" />
                  ) : (
                    <Play className="w-6 h-6 text-[#0A0E1A]" fill="currentColor" />
                  )}
                </button>
                
                <button
                  onClick={skipForward}
                  className="w-12 h-12 bg-[#0A0E1A] border border-[#1F2937] rounded-lg flex items-center justify-center hover:bg-[#1F2937] transition-colors"
                >
                  <SkipForward className="w-5 h-5 text-white" />
                </button>
              </div>

              {/* Speed Control */}
              <div className="grid grid-cols-4 gap-3">
                {[0.5, 1, 2, 4].map((speed) => (
                  <button
                    key={speed}
                    onClick={() => setPlaybackSpeed(speed)}
                    className={`h-10 rounded-lg font-medium text-sm transition-colors ${
                      playbackSpeed === speed
                        ? 'bg-[#22D3EE] text-white'
                        : 'bg-[#0A0E1A] text-[#9CA3AF] hover:bg-[#1F2937]'
                    }`}
                  >
                    {speed}x
                  </button>
                ))}
              </div>
            </div>

            {/* Timeline Section */}
            <div>
              <h3 className="text-sm font-medium text-[#9CA3AF] mb-3">Timeline</h3>
              <div className="bg-[#0A0E1A] rounded-lg p-4">
                {/* Progress Bar */}
                <div className="relative h-2 bg-[#1F2937] rounded-full mb-3">
                  <div
                    className="absolute left-0 top-0 h-full bg-[#22D3EE] rounded-full transition-all duration-300"
                    style={{ width: `${progress}%` }}
                  />
                  <div
                    className="absolute top-1/2 transform -translate-y-1/2 -translate-x-1/2 w-4 h-4 bg-[#22D3EE] rounded-full border-2 border-[#0A0E1A] shadow-lg"
                    style={{ left: `${progress}%` }}
                  />
                </div>

                {/* Current Position Info */}
                {currentPosition && (
                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <div>
                      <span className="text-[#6B7280]">Position:</span>
                      <span className="text-white ml-2">
                        {currentIndex + 1} / {historyData.length}
                      </span>
                    </div>
                    <div>
                      <span className="text-[#6B7280]">Speed:</span>
                      <span className="text-white ml-2">
                        {currentPosition.speed.toFixed(1)} kn
                      </span>
                    </div>
                    <div className="col-span-2">
                      <span className="text-[#6B7280]">Time:</span>
                      <span className="text-white ml-2">
                        {new Date(currentPosition.timestamp).toLocaleString()}
                      </span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
