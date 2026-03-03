'use client';

import { useEffect, useRef, useState } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { VesselPosition, VesselAlert } from '@/lib/types/vessel';

const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8082';

// Helper to convert string numbers to actual numbers (backend sends strings due to Jackson config)
function normalizeVesselPosition(position: unknown): VesselPosition {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const pos = position as any;
  return {
    ...pos,
    mmsi: typeof pos.mmsi === 'string' ? parseInt(pos.mmsi) : pos.mmsi,
    latitude: typeof pos.latitude === 'string' ? parseFloat(pos.latitude) : pos.latitude,
    longitude: typeof pos.longitude === 'string' ? parseFloat(pos.longitude) : pos.longitude,
    speed: pos.speed !== null && typeof pos.speed === 'string' ? parseFloat(pos.speed) : pos.speed,
    course: pos.course !== null && typeof pos.course === 'string' ? parseFloat(pos.course) : pos.course,
    heading: pos.heading !== null && typeof pos.heading === 'string' ? parseFloat(pos.heading) : pos.heading,
    draught: pos.draught !== null && typeof pos.draught === 'string' ? parseFloat(pos.draught) : pos.draught,
    length: pos.length !== null && typeof pos.length === 'string' ? parseFloat(pos.length) : pos.length,
    width: pos.width !== null && typeof pos.width === 'string' ? parseFloat(pos.width) : pos.width,
    distanceFromPrevious: pos.distanceFromPrevious !== null && typeof pos.distanceFromPrevious === 'string' ? parseFloat(pos.distanceFromPrevious) : pos.distanceFromPrevious,
    timeSinceLastUpdate: pos.timeSinceLastUpdate !== null && typeof pos.timeSinceLastUpdate === 'string' ? parseInt(pos.timeSinceLastUpdate) : pos.timeSinceLastUpdate,
    validated: typeof pos.validated === 'string' ? pos.validated === 'true' : pos.validated,
  };
}

interface UseVesselWebSocketOptions {
  enabled?: boolean;
  onPositionUpdate?: (position: VesselPosition) => void;
  onAlert?: (alert: VesselAlert) => void;
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (error: Error) => void;
}

export function useVesselWebSocket(options: UseVesselWebSocketOptions = {}) {
  const {
    enabled = true,
    onPositionUpdate,
    onAlert,
    onConnect,
    onDisconnect,
    onError,
  } = options;

  const [isConnected, setIsConnected] = useState(false);
  const [positions, setPositions] = useState<Map<number, VesselPosition>>(new Map());
  const [alerts, setAlerts] = useState<VesselAlert[]>([]);
  const clientRef = useRef<Client | null>(null);
  
  // High-performance batching with requestAnimationFrame
  const updateBufferRef = useRef<VesselPosition[]>([]);
  const animationFrameScheduledRef = useRef(false);
  
  // Store callbacks in refs to avoid recreating the client
  const callbacksRef = useRef({ onPositionUpdate, onAlert, onConnect, onDisconnect, onError });
  
  useEffect(() => {
    callbacksRef.current = { onPositionUpdate, onAlert, onConnect, onDisconnect, onError };
  }, [onPositionUpdate, onAlert, onConnect, onDisconnect, onError]);
  
  // Process buffered updates using requestAnimationFrame for optimal performance
  useEffect(() => {
    const processBuffer = () => {
      if (updateBufferRef.current.length > 0) {
        // Process all buffered updates in a single state update
        const batch = updateBufferRef.current.splice(0, updateBufferRef.current.length);
        
        setPositions((prev) => {
          const updated = new Map(prev);
          batch.forEach((position) => {
            updated.set(position.mmsi, position);
          });
          return updated;
        });
      }
      
      animationFrameScheduledRef.current = false;
    };
    
    // Poll for updates - this gets called automatically by RAF
    const scheduleUpdate = () => {
      if (!animationFrameScheduledRef.current && updateBufferRef.current.length > 0) {
        animationFrameScheduledRef.current = true;
        requestAnimationFrame(() => {
          processBuffer();
          // Continue polling if there are more updates
          if (enabled) {
            scheduleUpdate();
          }
        });
      }
    };
    
    // Check for updates every 100ms (10 FPS for updates)
    const interval = setInterval(scheduleUpdate, 100);
    
    return () => clearInterval(interval);
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      // Disconnect if disabled
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
      return;
    }

    console.log('Connecting to WebSocket:', WS_BASE_URL + '/ws');

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_BASE_URL + '/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      debug: (str) => {
        console.log('STOMP:', str);
      },
      onConnect: () => {
        console.log('WebSocket connected');
        setIsConnected(true);
        callbacksRef.current.onConnect?.();

        // Subscribe to vessel positions
        client.subscribe('/topic/vessel-positions', (message: IMessage) => {
          try {
            let rawPosition: unknown;
            
            // Parse the message body - handle double-encoded JSON from backend
            const body = message.body;
            if (typeof body === 'string') {
              const parsed = JSON.parse(body);
              // Check if it's still a string (double-encoded)
              rawPosition = typeof parsed === 'string' ? JSON.parse(parsed) : parsed;
            } else {
              rawPosition = body;
            }
            
            // Normalize the position data (convert string numbers to actual numbers)
            const position = normalizeVesselPosition(rawPosition);
            
            console.log('Vessel position updated:', position.mmsi, position.vesselName);
            
            // Add to buffer instead of immediate state update
            updateBufferRef.current.push(position);
            callbacksRef.current.onPositionUpdate?.(position);
          } catch (error) {
            console.error('Error parsing vessel position:', error, message.body);
          }
        });

        // Subscribe to vessel alerts
        client.subscribe('/topic/vessel-alerts', (message: IMessage) => {
          try {
            const alert: VesselAlert = JSON.parse(message.body);
            console.log('Received vessel alert:', alert);
            
            setAlerts((prev) => [alert, ...prev].slice(0, 100)); // Keep last 100 alerts
            callbacksRef.current.onAlert?.(alert);
          } catch (error) {
            console.error('Error parsing vessel alert:', error);
          }
        });
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        setIsConnected(false);
        callbacksRef.current.onDisconnect?.();
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
        const error = new Error(`STOMP error: ${frame.headers['message'] || 'Unknown error'}`);
        callbacksRef.current.onError?.(error);
      },
      onWebSocketError: (event) => {
        console.error('WebSocket error:', event);
        const error = new Error('WebSocket connection error');
        callbacksRef.current.onError?.(error);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      console.log('Cleaning up WebSocket connection');
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
    };
  }, [enabled]); // Only reconnect when enabled changes

  return {
    isConnected,
    positions: Array.from(positions.values()),
    alerts,
  };
}
