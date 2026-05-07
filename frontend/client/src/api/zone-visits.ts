export interface ZoneVisitEvent {
  inOut: 'In' | 'Out';
  deviceId: number;
  vehicleId: number;
  zoneId: number;
  zoneName: string;
  zoneDateTime: string; // ISO 8601 format
}

export interface ZoneVisitsWebSocketCallbacks {
  onOpen?: () => void;
  onClose?: (event: CloseEvent) => void;
  onError?: (error: Event) => void;
  onMessage?: (message: ZoneVisitEvent) => void;
  onReconnect?: (attempt: number) => void;
}

export class ZoneVisitsWebSocket {
  private ws: WebSocket | null = null;
  private url: string;
  private callbacks: ZoneVisitsWebSocketCallbacks;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000;
  private isConnecting = false;
  private shouldReconnect = true;
  private reconnectTimeout: number | null = null;

  constructor(url: string, callbacks: ZoneVisitsWebSocketCallbacks) {
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
      console.log('ZoneVisits WebSocket connected to', this.url);
      this.isConnecting = false;
      this.reconnectAttempts = 0;
      this.callbacks.onOpen?.();
    };

    this.ws.onclose = (event) => {
      console.log('ZoneVisits WebSocket disconnected:', event.code, event.reason);
      this.isConnecting = false;
      this.ws = null;
      this.callbacks.onClose?.(event);

      if (this.shouldReconnect && event.code !== 1000) {
        this.scheduleReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('ZoneVisits WebSocket error:', error);
      this.isConnecting = false;
      this.callbacks.onError?.(error);
    };

    this.ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        if (this.isZoneVisitEvent(data)) {
          this.callbacks.onMessage?.(data);
        } else {
          console.warn('Unknown message format:', data);
        }
      } catch (error) {
        console.error('Failed to parse WebSocket message:', error, event.data);
      }
    };
  }

  private isZoneVisitEvent(data: any): data is ZoneVisitEvent {
    return (
      typeof data === 'object' &&
      data !== null &&
      (data.inOut === 'In' || data.inOut === 'Out') &&
      typeof data.deviceId === 'number' &&
      typeof data.vehicleId === 'number' &&
      typeof data.zoneId === 'number' &&
      typeof data.zoneName === 'string' &&
      typeof data.zoneDateTime === 'string'
    );
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

// Helper function to create WebSocket URL for zone visits
export function getZoneVisitsWebSocketUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const host = window.location.host;
  return `${protocol}//${host}/ws-telemetry/zone-visits`;
}

// Default export for convenience
export default ZoneVisitsWebSocket;