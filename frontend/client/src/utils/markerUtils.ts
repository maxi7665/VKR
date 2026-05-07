import Feature from 'ol/Feature';
import Point from 'ol/geom/Point';
import { fromLonLat } from 'ol/proj';
import { Style, Fill, Stroke, RegularShape, Icon } from 'ol/style';

export interface VehicleMarkerData {
  deviceId: number;
  vehicleId: number;
  latitude: number;
  longitude: number;
  azimuth: number;
  packetTime: string;
  receptionTime: string;
}

export interface VehicleMarker {
  deviceId: number;
  feature: Feature;
  lastPosition: number[]; // [x, y] in map coordinates
  lastAzimuth: number;
  animation?: {
    start: number[];
    end: number[];
    startTime: number;
    duration: number;
  };
}

/**
 * Create a canvas element with an acute triangle (no red circle)
 */
function createAcuteTriangleCanvas(color: string = '#2196f3'): HTMLCanvasElement {
  const canvas = document.createElement('canvas');
  const size = 36; // Canvas size
  const context = canvas.getContext('2d')!;
  
  canvas.width = size;
  canvas.height = size;
  
  // Clear canvas
  context.clearRect(0, 0, size, size);
  
  // Draw acute triangle (long and pointy)
  // Triangle points: front tip at (18, 4), back corners at (6, 32) and (30, 32)
  context.beginPath();
  context.moveTo(18, 4);  // Front tip (top center)
  context.lineTo(6, 32);  // Bottom left
  context.lineTo(30, 32); // Bottom right
  context.closePath();
  
  // Fill triangle
  context.fillStyle = color;
  context.fill();
  
  // Draw outline
  context.lineWidth = 2;
  context.strokeStyle = '#0d47a1';
  context.stroke();
  
  return canvas;
}

/**
 * Create a triangle style for vehicle markers with azimuth rotation
 * Creates an acute triangle that clearly shows direction
 *
 * @param azimuth - Direction in degrees (0 = North, 90 = East, 180 = South, 270 = West)
 * @param color - Triangle fill color
 * @param rotationOffset - Additional rotation offset in degrees (default: -90 to align North)
 */
export function createTriangleStyle(
  azimuth: number = 0,
  color: string = '#2196f3',
  rotationOffset: number = -90
): Style {
  // Convert to radians with offset
  // Default offset -90 means: azimuth 0° (North) will point up
  // Because OpenLayers 0 radians points east, so -90° points north
  const rotation = (azimuth + rotationOffset) * Math.PI / 180;
  
  // Create canvas with acute triangle
  const canvas = createAcuteTriangleCanvas(color);
  
  return new Style({
    image: new Icon({
      img: canvas,
      rotation: rotation,
      anchor: [0.5, 0.8], // Anchor point at the center bottom of triangle
      scale: 1.0
    })
  });
}

/**
 * Create a vehicle marker feature
 */
export function createVehicleMarker(data: VehicleMarkerData, color: string = '#2196f3'): VehicleMarker {
  const position = fromLonLat([data.longitude, data.latitude]);
  const point = new Point(position);
  const feature = new Feature({
    geometry: point,
    deviceId: data.deviceId,
    vehicleId: data.vehicleId,
    azimuth: data.azimuth,
    packetTime: data.packetTime
  });
  
  feature.setStyle(createTriangleStyle(data.azimuth, color, 0));
  
  return {
    deviceId: data.deviceId,
    feature,
    lastPosition: position,
    lastAzimuth: data.azimuth
  };
}

/**
 * Update vehicle marker position with smooth animation
 */
export function updateVehicleMarker(
  marker: VehicleMarker,
  newData: VehicleMarkerData,
  animationDuration: number = 300 // ms
): VehicleMarker {
  const newPosition = fromLonLat([newData.longitude, newData.latitude]);
  
  // Update the feature's geometry immediately for proper rendering
  const point = marker.feature.getGeometry() as Point;
  point.setCoordinates(newPosition);
  
  // Update style with new azimuth (use default color and 0° offset)
  marker.feature.setStyle(createTriangleStyle(newData.azimuth, '#2196f3', 0));
  
  // Update marker data
  marker.lastPosition = newPosition;
  marker.lastAzimuth = newData.azimuth;
  
  // Store animation data for smooth interpolation
  marker.animation = {
    start: marker.lastPosition,
    end: newPosition,
    startTime: Date.now(),
    duration: animationDuration
  };
  
  return marker;
}

/**
 * Animate marker movement using requestAnimationFrame
 * Returns true if animation is complete
 */
export function animateMarker(
  marker: VehicleMarker,
  currentTime: number = Date.now()
): boolean {
  if (!marker.animation) {
    return true; // No animation in progress
  }
  
  const { start, end, startTime, duration } = marker.animation;
  const elapsed = currentTime - startTime;
  
  if (elapsed >= duration) {
    // Animation complete
    const point = marker.feature.getGeometry() as Point;
    point.setCoordinates(end);
    delete marker.animation;
    return true;
  }
  
  // Linear interpolation
  const progress = elapsed / duration;
  const currentX = start[0] + (end[0] - start[0]) * progress;
  const currentY = start[1] + (end[1] - start[1]) * progress;
  
  const point = marker.feature.getGeometry() as Point;
  point.setCoordinates([currentX, currentY]);
  
  return false;
}

/**
 * Create a marker manager to handle multiple vehicle markers
 */
export class MarkerManager {
  private markers: Map<number, VehicleMarker> = new Map();
  private animationFrameId: number | null = null;
  private isAnimating = false;
  
  constructor() {}
  
  /**
   * Add or update a vehicle marker
   */
  updateVehicle(data: VehicleMarkerData, animate: boolean = true): VehicleMarker {
    const existing = this.markers.get(data.deviceId);
    
    if (existing) {
      if (animate) {
        return updateVehicleMarker(existing, data);
      } else {
        // Update without animation
        const newPosition = fromLonLat([data.longitude, data.latitude]);
        const point = existing.feature.getGeometry() as Point;
        point.setCoordinates(newPosition);
        existing.feature.setStyle(createTriangleStyle(data.azimuth, '#2196f3', 0));
        existing.lastPosition = newPosition;
        existing.lastAzimuth = data.azimuth;
        delete existing.animation;
        return existing;
      }
    } else {
      // Create new marker
      const marker = createVehicleMarker(data, '#2196f3');
      this.markers.set(data.deviceId, marker);
      return marker;
    }
  }
  
  /**
   * Remove a vehicle marker
   */
  removeVehicle(deviceId: number): boolean {
    return this.markers.delete(deviceId);
  }
  
  /**
   * Get all marker features
   */
  getFeatures(): Feature[] {
    return Array.from(this.markers.values()).map(m => m.feature);
  }
  
  /**
   * Get a specific marker
   */
  getMarker(deviceId: number): VehicleMarker | undefined {
    return this.markers.get(deviceId);
  }
  
  /**
   * Get all markers
   */
  getAllMarkers(): VehicleMarker[] {
    return Array.from(this.markers.values());
  }
  
  /**
   * Clear all markers
   */
  clear(): void {
    this.markers.clear();
  }
  
  /**
   * Start animation loop
   */
  startAnimation(): void {
    if (this.isAnimating) return;
    
    this.isAnimating = true;
    
    const animate = () => {
      if (!this.isAnimating) return;
      
      const currentTime = Date.now();
      let needsAnotherFrame = false;
      
      // Animate all markers
      for (const marker of this.markers.values()) {
        if (marker.animation) {
          const isComplete = animateMarker(marker, currentTime);
          if (!isComplete) {
            needsAnotherFrame = true;
          }
        }
      }
      
      if (needsAnotherFrame) {
        this.animationFrameId = requestAnimationFrame(animate);
      } else {
        this.isAnimating = false;
        this.animationFrameId = null;
      }
    };
    
    this.animationFrameId = requestAnimationFrame(animate);
  }
  
  /**
   * Stop animation loop
   */
  stopAnimation(): void {
    this.isAnimating = false;
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = null;
    }
  }
  
  /**
   * Clean up resources
   */
  dispose(): void {
    this.stopAnimation();
    this.clear();
  }
}

// Default export for convenience
export default {
  createTriangleStyle,
  createVehicleMarker,
  updateVehicleMarker,
  animateMarker,
  MarkerManager
};