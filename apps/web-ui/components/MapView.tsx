'use client';

import { MapContainer, TileLayer, useMap } from 'react-leaflet';
import { Plus, Minus, Layers, Share2, Ship, Bell, Check } from 'lucide-react';
import { useState, useEffect, useRef, useCallback } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import WeatherOverlay from './WeatherOverlay';
import CanvasVesselOverlay from './CanvasVesselOverlay';
import HistoryTrackOverlay from './HistoryTrackOverlay';
import HistoryPlaybackPanel from './HistoryPlaybackPanel';
import { WeatherData, getWeatherForBounds } from '@/lib/api/weather';
import { useVesselWebSocket } from '@/lib/hooks/useVesselWebSocket';
import { VesselPosition } from '@/lib/types/vessel';

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

// Map layer configurations
const MAP_LAYERS = {
  standard: {
    name: 'Standard',
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    hasOverlay: false as const
  },
  satellite: {
    name: 'Satellite',
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    attribution: '&copy; Esri &mdash; Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community',
    hasOverlay: false as const
  },
  nautical: {
    name: 'Nautical Chart',
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> | <a href="http://www.openseamap.org">OpenSeaMap</a>',
    hasOverlay: true as const,
    overlayUrl: 'https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png'
  }
} as const;

type LayerType = keyof typeof MAP_LAYERS;
type OverlayType = 'vessels' | 'routes' | 'zones' | 'weather';

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

interface VesselSearchResult {
  mmsi: number;
  vessel_name: string;
  latitude?: number;
  longitude?: number;
}

interface MapViewProps {
  showHistoryPanel?: boolean;
  onHideHistoryPanel?: () => void;
  mapPanToVesselRef?: React.MutableRefObject<((vessel: VesselSearchResult) => void) | null>;
}

interface MapControlsProps {
  onLayersClick: () => void;
}

// Component to track map bounds
function MapBoundsTracker({ onBoundsChange }: { onBoundsChange: (bounds: L.LatLngBounds) => void }) {
  const map = useMap();

  useEffect(() => {
    const updateBounds = () => {
      onBoundsChange(map.getBounds());
    };

    // Initial bounds
    updateBounds();

    // Update on map move
    map.on('moveend', updateBounds);
    map.on('zoomend', updateBounds);

    return () => {
      map.off('moveend', updateBounds);
      map.off('zoomend', updateBounds);
    };
  }, [map, onBoundsChange]);

  return null;
}

// Component to capture map ref
function MapRefSetter({ mapRef }: { mapRef: React.MutableRefObject<L.Map | null> }) {
  const map = useMap();
  
  useEffect(() => {
    mapRef.current = map;
  }, [map, mapRef]);
  
  return null;
}

function MapControls({ onLayersClick }: MapControlsProps) {
  const map = useMap();

  return (
    <>
      {/* Left Controls - Zoom, Layers, Share */}
      <div className="absolute top-4 left-4 z-1000 flex flex-col gap-px">
        {/* Zoom Controls */}
        <button
          onClick={() => map.zoomIn()}
          className="w-10 h-10 bg-white border border-[#D0D0D0] rounded-t flex items-center justify-center hover:bg-[#F5F5F5] transition-colors"
          title="Zoom In"
        >
          <Plus className="w-5 h-5 text-[#333333]" />
        </button>
        <button
          onClick={() => map.zoomOut()}
          className="w-10 h-10 bg-white border border-[#D0D0D0] rounded-b flex items-center justify-center hover:bg-[#F5F5F5] transition-colors"
          title="Zoom Out"
        >
          <Minus className="w-5 h-5 text-[#333333]" />
        </button>
      </div>

      {/* Layers Control */}
      <div className="absolute top-26 left-4 z-1000">
        <button
          onClick={onLayersClick}
          className="flex items-center gap-2 px-4 py-2.5 bg-white border border-[#D0D0D0] rounded hover:bg-[#F5F5F5] transition-colors"
          title="Layers"
        >
          <Layers className="w-4.5 h-4.5 text-[#333333]" />
          <span className="text-sm font-medium text-[#333333]">Layers</span>
        </button>
      </div>

      {/* Share Control */}
      <div className="absolute top-38 left-4 z-1000">
        <button
          className="flex items-center gap-2 px-4 py-2.5 bg-white border border-[#D0D0D0] rounded hover:bg-[#F5F5F5] transition-colors"
          title="Share"
        >
          <Share2 className="w-4.5 h-4.5 text-[#333333]" />
          <span className="text-sm font-medium text-[#333333]">Share</span>
        </button>
      </div>

      {/* Right Controls - Vessels, Alerts */}
      <div className="absolute top-4 right-4 z-1000 flex flex-col gap-2">
        {/* Vessels Button */}
        <button
          className="flex flex-col items-center gap-1 px-2 py-3 bg-white border border-[#D0D0D0] rounded hover:bg-[#F5F5F5] transition-colors w-15"
          title="Vessels"
        >
          <Ship className="w-6 h-6 text-[#00A0E3]" />
          <span className="text-[11px] font-medium text-[#333333]">Vessels</span>
        </button>

        {/* Alerts Button */}
        <button
          className="flex flex-col items-center gap-1 px-2 py-3 bg-white border border-[#D0D0D0] rounded hover:bg-[#F5F5F5] transition-colors w-15"
          title="Alerts"
        >
          <Bell className="w-6 h-6 text-[#333333]" />
          <span className="text-[11px] font-medium text-[#333333]">Alerts</span>
        </button>
      </div>
    </>
  );
}

export default function MapView({ showHistoryPanel = false, onHideHistoryPanel, mapPanToVesselRef }: MapViewProps = {}) {
  const [selectedLayer, setSelectedLayer] = useState<LayerType>('standard');
  const [showLayersPanel, setShowLayersPanel] = useState(false);
  const [overlays, setOverlays] = useState<Record<OverlayType, boolean>>({
    vessels: true,
    routes: false,
    zones: false,
    weather: true
  });
  const [weatherData, setWeatherData] = useState<WeatherData[]>([]);
  const [mapBounds, setMapBounds] = useState<L.LatLngBounds | null>(null);
  const [isLoadingWeather, setIsLoadingWeather] = useState(false);
  const [selectedVessel, setSelectedVessel] = useState<VesselPosition | null>(null); // eslint-disable-line @typescript-eslint/no-unused-vars
  const [historyPlaybackState, setHistoryPlaybackState] = useState({
    isPlaying: false,
    currentIndex: 0,
    historyData: [] as VesselHistoryPoint[],
    playbackSpeed: 1,
  });
  const [selectedHistoryVessel, setSelectedHistoryVessel] = useState<{mmsi: number; vessel_name: string; latitude?: number; longitude?: number} | null>(null); // eslint-disable-line @typescript-eslint/no-unused-vars
  const fetchTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const mapRef = useRef<L.Map | null>(null);

  // Expose vessel pan function to parent via ref
  useEffect(() => {
    if (mapPanToVesselRef) {
      mapPanToVesselRef.current = (vessel: VesselSearchResult) => {
        if (vessel.latitude && vessel.longitude && mapRef.current) {
          console.log('Panning map to vessel from top bar search:', vessel.vessel_name, vessel.latitude, vessel.longitude);
          mapRef.current.setView([vessel.latitude, vessel.longitude], 12, {
            animate: true,
            duration: 1
          });
        }
      };
    }
  }, [mapPanToVesselRef]);

  // WebSocket connection for real-time vessel updates (only when vessels overlay is enabled)
  const { isConnected, positions: vesselPositions } = useVesselWebSocket({
    enabled: overlays.vessels,
    onPositionUpdate: (position) => {
      console.log('Vessel position updated:', position.mmsi, position.vesselName);
    },
    onAlert: (alert) => {
      console.log('Vessel alert received:', alert);
    },
    onConnect: () => {
      console.log('Connected to vessel tracking WebSocket');
    },
    onDisconnect: () => {
      console.log('Disconnected from vessel tracking WebSocket');
    },
  });

  const handleVesselClick = useCallback((vessel: VesselPosition) => {
    setSelectedVessel(vessel);
    console.log('Vessel clicked:', vessel);
  }, []);

  // Fetch weather data when map bounds change (zoom/pan) or weather overlay is enabled
  useEffect(() => {
    if (!overlays.weather || !mapBounds) {
      setWeatherData([]);
      return;
    }

    const bounds = mapBounds;
    
    if (fetchTimeoutRef.current) {
      clearTimeout(fetchTimeoutRef.current);
    }

    fetchTimeoutRef.current = setTimeout(() => {
      const fetchWeather = async () => {
        setIsLoadingWeather(true);
        try {
          const data = await getWeatherForBounds({
            north: bounds.getNorth(),
            south: bounds.getSouth(),
            east: bounds.getEast(),
            west: bounds.getWest()
          });
          setWeatherData(data);
          console.log(`Fetched ${data.length} weather points for current map view`);
        } catch (error) {
          console.error('Error fetching weather data:', error);
        } finally {
          setIsLoadingWeather(false);
        }
      };

      fetchWeather();
    }, 500);

    return () => {
      if (fetchTimeoutRef.current) {
        clearTimeout(fetchTimeoutRef.current);
      }
    };
  }, [overlays.weather, mapBounds]);

  // Periodic refresh (every 5 minutes)
  useEffect(() => {
    if (!overlays.weather || !mapBounds) return;

    const interval = setInterval(() => {
      const fetchWeather = async () => {
        const data = await getWeatherForBounds({
          north: mapBounds.getNorth(),
          south: mapBounds.getSouth(),
          east: mapBounds.getEast(),
          west: mapBounds.getWest()
        });
        setWeatherData(data);
        console.log('Weather data refreshed (5-minute update)');
      };
      fetchWeather();
    }, 5 * 60 * 1000);

    return () => clearInterval(interval);
  }, [overlays.weather, mapBounds]);

  const toggleOverlay = (overlay: OverlayType) => {
    setOverlays(prev => ({ ...prev, [overlay]: !prev[overlay] }));
  };

  // Pan map when history vessel is selected — exposed to parent via mapPanToHistoryVesselRef
  const handleVesselSelect = useCallback((vessel: {mmsi: number; vessel_name: string; latitude?: number; longitude?: number} | null) => {
    setSelectedHistoryVessel(vessel);
    if (vessel && vessel.latitude && vessel.longitude && mapRef.current) {
      mapRef.current.setView([vessel.latitude, vessel.longitude], 12, { animate: true, duration: 1 });
    } else if (vessel && mapRef.current) {
      const vesselPosition = vesselPositions.find(v => v.mmsi === vessel.mmsi);
      if (vesselPosition && vesselPosition.latitude !== null && vesselPosition.longitude !== null) {
        mapRef.current.setView([vesselPosition.latitude, vesselPosition.longitude], 12, { animate: true, duration: 1 });
      }
    }
  }, [vesselPositions]);

  const handleHistoryClose = useCallback(() => {
    onHideHistoryPanel?.();
    setHistoryPlaybackState({ isPlaying: false, currentIndex: 0, historyData: [], playbackSpeed: 1 });
    setSelectedHistoryVessel(null);
  }, [onHideHistoryPanel]);

  return (
    <div className="relative w-full h-full bg-[#F0F0F0]">
      <MapContainer
        center={[-2.5, 118.0]}
        zoom={5}
        zoomControl={false}
        style={{ height: '100%', width: '100%', background: '#F0F0F0' }}
      >
        {/* Base Layer */}
        <TileLayer
          key={`base-${selectedLayer}`}
          url={MAP_LAYERS[selectedLayer].url}
          attribution={MAP_LAYERS[selectedLayer].attribution}
        />
        
        {/* Nautical Chart Overlay (when nautical layer is selected) */}
        {selectedLayer === 'nautical' && (
          <TileLayer
            key="nautical-overlay"
            url="https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png"
            attribution=""
          />
        )}
        
        {/* Vessel Overlay - Real-time vessel positions */}
        <CanvasVesselOverlay 
          vessels={vesselPositions} 
          enabled={overlays.vessels}
          onVesselClick={handleVesselClick}
        />
        
        {/* Weather Overlay */}
        <WeatherOverlay weatherData={weatherData} enabled={overlays.weather} />
        
        {/* History Track Overlay */}
        <HistoryTrackOverlay
          map={mapRef.current}
          historyData={historyPlaybackState.historyData}
          currentIndex={historyPlaybackState.currentIndex}
          enabled={showHistoryPanel && historyPlaybackState.historyData.length > 0}
        />
        
        {/* Track map bounds */}
        <MapBoundsTracker onBoundsChange={setMapBounds} />
        
        {/* Set map ref */}
        <MapRefSetter mapRef={mapRef} />
        
        <MapControls onLayersClick={() => setShowLayersPanel(!showLayersPanel)} />
      </MapContainer>

      {/* WebSocket Connection Status */}
      {overlays.vessels && (
        <div className={`absolute top-4 left-1/2 transform -translate-x-1/2 z-1000 px-3 py-1.5 rounded-full text-xs font-medium flex items-center gap-2 ${
          isConnected 
            ? 'bg-green-500/90 text-white' 
            : 'bg-red-500/90 text-white animate-pulse'
        }`}>
          <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-white' : 'bg-white/70'}`} />
          {isConnected ? `Live: ${vesselPositions.length} vessels` : 'Connecting...'}
        </div>
      )}

      {/* Loading Indicator */}
      {isLoadingWeather && (
        <div className="absolute top-4 right-4 z-1000 bg-white/90 backdrop-blur-sm border border-[#D0D0D0] rounded-lg shadow-lg px-4 py-2 flex items-center gap-2">
          <div className="w-4 h-4 border-2 border-[#00A0E3] border-t-transparent rounded-full animate-spin" />
          <span className="text-sm text-[#333333]">Loading weather...</span>
        </div>
      )}

      {/* Layers Panel */}
      {showLayersPanel && (
        <div className="absolute top-26 left-19 z-1000 bg-white border border-[#D0D0D0] rounded shadow-lg w-64 p-4">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-semibold text-[#333333]">Map Layers</h3>
            <button
              onClick={() => setShowLayersPanel(false)}
              className="text-[#666666] hover:text-[#333333]"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          {/* Base Maps */}
          <div className="mb-4">
            <p className="text-xs font-medium text-[#666666] mb-2 uppercase">Base Maps</p>
            <div className="space-y-1">
              {Object.entries(MAP_LAYERS).map(([key, layer]) => (
                <button
                  key={key}
                  onClick={() => setSelectedLayer(key as LayerType)}
                  className={`w-full flex items-center justify-between px-3 py-2 rounded text-sm transition-colors ${
                    selectedLayer === key
                      ? 'bg-[#00A0E3]/10 text-[#00A0E3] font-medium'
                      : 'text-[#333333] hover:bg-[#F5F5F5]'
                  }`}
                >
                  <span>{layer.name}</span>
                  {selectedLayer === key && (
                    <Check className="w-4 h-4" />
                  )}
                </button>
              ))}
            </div>
          </div>

          {/* Overlays */}
          <div>
            <p className="text-xs font-medium text-[#666666] mb-2 uppercase">Overlays</p>
            <div className="space-y-1">
              <label className="flex items-center justify-between px-3 py-2 rounded text-sm hover:bg-[#F5F5F5] cursor-pointer transition-colors">
                <span className="text-[#333333]">Vessels</span>
                <input
                  type="checkbox"
                  checked={overlays.vessels}
                  onChange={() => toggleOverlay('vessels')}
                  className="w-4 h-4 text-[#00A0E3] border-[#D0D0D0] rounded focus:ring-[#00A0E3]"
                />
              </label>
              <label className="flex items-center justify-between px-3 py-2 rounded text-sm hover:bg-[#F5F5F5] cursor-pointer transition-colors">
                <span className="text-[#333333]">Routes</span>
                <input
                  type="checkbox"
                  checked={overlays.routes}
                  onChange={() => toggleOverlay('routes')}
                  className="w-4 h-4 text-[#00A0E3] border-[#D0D0D0] rounded focus:ring-[#00A0E3]"
                />
              </label>
              <label className="flex items-center justify-between px-3 py-2 rounded text-sm hover:bg-[#F5F5F5] cursor-pointer transition-colors">
                <span className="text-[#333333]">Zones</span>
                <input
                  type="checkbox"
                  checked={overlays.zones}
                  onChange={() => toggleOverlay('zones')}
                  className="w-4 h-4 text-[#00A0E3] border-[#D0D0D0] rounded focus:ring-[#00A0E3]"
                />
              </label>
              <label className="flex items-center justify-between px-3 py-2 rounded text-sm hover:bg-[#F5F5F5] cursor-pointer transition-colors">
                <span className="text-[#333333]">Weather</span>
                <input
                  type="checkbox"
                  checked={overlays.weather}
                  onChange={() => toggleOverlay('weather')}
                  className="w-4 h-4 text-[#00A0E3] border-[#D0D0D0] rounded focus:ring-[#00A0E3]"
                />
              </label>
            </div>
          </div>
        </div>
      )}

      {/* History Playback Panel */}
      {showHistoryPanel && (
        <HistoryPlaybackPanel
          onClose={handleHistoryClose}
          onVesselSelect={handleVesselSelect}
          onPlaybackStateChange={setHistoryPlaybackState}
        />
      )}
    </div>
  );
}
