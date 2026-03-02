'use client';

import { useEffect, useRef, useState } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { VesselPosition, VesselAlert } from '@/lib/types/vessel';

const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:8082';

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
  
  // Batch updates to reduce re-renders
  const pendingUpdatesRef = useRef<Map<number, VesselPosition>>(new Map());
  
  // Store callbacks in refs to avoid recreating the client
  const callbacksRef = useRef({ onPositionUpdate, onAlert, onConnect, onDisconnect, onError });
  
  useEffect(() => {
    callbacksRef.current = { onPositionUpdate, onAlert, onConnect, onDisconnect, onError };
  }, [onPositionUpdate, onAlert, onConnect, onDisconnect, onError]);
  
  // Flush pending updates every 500ms
  useEffect(() => {
    const flushUpdates = () => {
      if (pendingUpdatesRef.current.size > 0) {
        setPositions((prev) => {
          const updated = new Map(prev);
          pendingUpdatesRef.current.forEach((position, mmsi) => {
            updated.set(mmsi, position);
          });
          pendingUpdatesRef.current.clear();
          return updated;
        });
      }
    };
    
    const interval = setInterval(flushUpdates, 500); // Batch updates every 500ms
    return () => clearInterval(interval);
  }, []);

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
            let position: VesselPosition;
            
            // Parse the message body - handle double-encoded JSON from backend
            const body = message.body;
            if (typeof body === 'string') {
              const parsed = JSON.parse(body);
              // Check if it's still a string (double-encoded)
              position = typeof parsed === 'string' ? JSON.parse(parsed) : parsed;
            } else {
              position = body;
            }
            
            console.log('Vessel position updated:', position.mmsi, position.vesselName);
            
            // Add to pending updates instead of immediate state update
            pendingUpdatesRef.current.set(position.mmsi, position);
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
