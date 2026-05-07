export interface Point {
  lon: number;
  lat: number;
}

export interface SubscribePolygonMessage {
  type: 'subscribe_polygon';
  points: Point[];
}

export interface UnsubscribePolygonMessage {
  type: 'unsubscribe_polygon';
}

export interface TelemetryMessage {
  id: number | null;
  vehicleId: number;
  deviceId: number;
  packetTime: string;
  receptionTime: string;
  latitude: number;
  longitude: number;
  s2Cell: number;
  azimuth: number;
}

export type WebSocketMessage = SubscribePolygonMessage | UnsubscribePolygonMessage;
export type IncomingMessage = TelemetryMessage;

export interface WebSocketCallbacks {
  onOpen?: () => void;
  onClose?: (event: CloseEvent) => void;
  onError?: (error: Event) => void;
  onMessage?: (message: IncomingMessage) => void;
  onReconnect?: (attempt: number) => void;
}

// HTTP API Interfaces
export interface DeviceTelemetryRequest {
  deviceId: number;
  fromDateTime: string;
  toDateTime: string;
}

export type TelemetryPacket = TelemetryMessage;

// Base URL for HTTP API
const API_BASE = '/telemetry';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init);
  if (!response.ok) {
    const errorText = await response.text().catch(() => response.statusText);
    throw new Error(errorText || `HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

/**
 * Get device telemetry for a specific time period
 * POST /api/telemetry/device-telemetry
 */
export async function getDeviceTelemetry(
  payload: DeviceTelemetryRequest
): Promise<TelemetryPacket[]> {
  return request<TelemetryPacket[]>('/device-telemetry', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
}

/**
 * Query telemetry by polygon and time interval
 * POST /api/telemetry/query-by-polygon
 */
export interface PointDto {
  latitude: number;
  longitude: number;
}

export interface PolygonTimeRequest {
  polygon: PointDto[];
  fromDateTime: string;
  toDateTime: string;
}

export interface TelemetryIntervalResponse {
  vehicleId: number;
  deviceId: number;
  fromDateTime: string;
  toDateTime: string;
  // Optional entries if provided by API
  entries?: TelemetryPacket[];
}

export async function queryTelemetryByPolygon(
  payload: PolygonTimeRequest
): Promise<TelemetryIntervalResponse> {
  return request<TelemetryIntervalResponse>('/query-by-polygon', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
}

export class TelemetryWebSocket {
  private ws: WebSocket | null = null;
  private url: string;
  private callbacks: WebSocketCallbacks;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000;
  private isConnecting = false;
  private shouldReconnect = true;
  private reconnectTimeout: number | null = null;

  constructor(url: string, callbacks: WebSocketCallbacks) {
    this.url = url;
    this.callbacks = callbacks;
  }

  connect(): void {
    if (this.isConnecting || this.ws?.readyState === WebSocket.OPEN) {
      return;
    }

    this.isConnecting = true;
    this.shouldReconnect = true;

    try {
      this.ws = new WebSocket(this.url);
      this.setupEventListeners();
    } catch (error) {
      console.error('Failed to create WebSocket:', error);
      this.isConnecting = false;
      this.scheduleReconnect();
    }
  }

  private setupEventListeners(): void {
    if (!this.ws) return;

    this.ws.onopen = () => {
      console.log('WebSocket connected to', this.url);
      this.isConnecting = false;
      this.reconnectAttempts = 0;
      this.callbacks.onOpen?.();
    };

    this.ws.onclose = (event) => {
      console.log('WebSocket disconnected:', event.code, event.reason);
      this.isConnecting = false;
      this.ws = null;
      this.callbacks.onClose?.(event);

      if (this.shouldReconnect && event.code !== 1000) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      this.isConnecting = false;
      this.callbacks.onError?.(error);
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (this.isTelemetryMessage(data)) {
          this.callbacks.onMessage?.(data);
        } else {
          console.warn('Unknown message format:', data);
        }
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error, event.data);
      }
    };
  }

  private isTelemetryMessage(data: any): data is TelemetryMessage {
    return (
      typeof data === 'object' &&
      data !== null &&
      typeof data.deviceId === 'number' &&
      typeof data.latitude === 'number' &&
      typeof data.longitude === 'number'
    );
  }

  send(message: WebSocketMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      console.warn('WebSocket is not open, cannot send message:', message);
    }
  }

  subscribeToPolygon(points: Point[]): void {
    this.send({
      type: 'subscribe_polygon',
      points
    });
  }

  unsubscribeFromPolygon(): void {
    this.send({
      type: 'unsubscribe_polygon'
    });
  }

  private scheduleReconnect(): void {
    if (!this.shouldReconnect || this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log('Max reconnection attempts reached or reconnection disabled');
      return;
    }

    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
    }

    this.reconnectAttempts++;
    const delay = this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts - 1);
    
    console.log(`Scheduling reconnect attempt ${this.reconnectAttempts} in ${delay}ms`);
    
    this.reconnectTimeout = window.setTimeout(() => {
      this.callbacks.onReconnect?.(this.reconnectAttempts);
      this.connect();
    }, delay);
  }

  disconnect(): void {
    this.shouldReconnect = false;
    
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }

    if (this.ws) {
      this.ws.onclose = null; // Prevent reconnect on manual close
      this.ws.close(1000, 'Manual disconnect');
      this.ws = null;
    }
  }

  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  get connectionState(): string {
    if (!this.ws) return 'DISCONNECTED';
    switch (this.ws.readyState) {
      case WebSocket.CONNECTING: return 'CONNECTING';
      case WebSocket.OPEN: return 'OPEN';
      case WebSocket.CLOSING: return 'CLOSING';
      case WebSocket.CLOSED: return 'CLOSED';
      default: return 'UNKNOWN';
    }
  }
}

// Helper function to create WebSocket URL based on current environment
export function getWebSocketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  return `${protocol}//${host}/ws-telemetry/telemetry`;
}

// Default export for convenience
export default TelemetryWebSocket;