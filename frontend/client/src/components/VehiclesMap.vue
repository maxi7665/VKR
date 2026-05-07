<template>
  <div class="vehicles-map-container">
    <div ref="mapContainer" class="vehicles-map"></div>
    <div v-if="connectionStatus !== 'OPEN'" class="connection-status">
      <div class="status-indicator" :class="connectionStatus.toLowerCase()"></div>
      <span class="status-text">{{ connectionStatusText }}</span>
    </div>
    <div v-if="vehicleCount > 0" class="vehicle-counter">
      Транспортных средств: {{ vehicleCount }}
    </div>
    <div
      v-if="popupVisible"
      class="vehicle-popup"
      :style="{ left: `${popupLeft}px`, top: `${popupTop}px` }"
      @mouseenter="cancelPopupHide"
      @mouseleave="schedulePopupHide"
    >
      <div class="popup-header">
        <span class="popup-title">Данные ТС</span>
        <button class="popup-close" @click="closePopup">×</button>
      </div>
      <div class="popup-content">
        <pre class="json-data">{{ popupData }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import OSM from 'ol/source/OSM'
import { fromLonLat, toLonLat } from 'ol/proj'
import { getWebSocketUrl, TelemetryWebSocket, type Point } from '../api/telemetry'
import { MarkerManager } from '../utils/markerUtils'

const mapContainer = ref<HTMLDivElement | null>(null)
const map = ref<Map | null>(null)
let vectorSource: VectorSource | null = null
let vectorLayer: VectorLayer | null = null

// WebSocket connection
const ws = ref<TelemetryWebSocket | null>(null)
const connectionStatus = ref<'CONNECTING' | 'OPEN' | 'CLOSED' | 'ERROR'>('CONNECTING')
const connectionStatusText = ref('Подключение...')
const vehicleCount = ref(0)

// Marker management
const markerManager = ref<MarkerManager | null>(null)

// Popup variables (simple approach like ZoneMap.vue)
const popupVisible = ref(false)
const popupData = ref<string>('')
const popupLeft = ref(0)
const popupTop = ref(0)
let hidePopupTimer: number | null = null
const HIDE_POPUP_DELAY = 300 // ms

// Subscription management
let currentSubscription: Point[] | null = null
let subscriptionDebounceTimer: number | null = null
const SUBSCRIPTION_DEBOUNCE_MS = 500

function createMap() {
  if (!mapContainer.value) return

  // Create vector source and layer for vehicle markers
  vectorSource = new VectorSource()
  vectorLayer = new VectorLayer({
    source: vectorSource
  })

  map.value = new Map({
    target: mapContainer.value,
    layers: [
      new TileLayer({ source: new OSM() }),
      vectorLayer
    ],
    view: new View({
      center: fromLonLat([30.437888132963106, 59.962961568076395]), // Санкт-Петербург
      zoom: 12,
      minZoom: 3,
      maxZoom: 22
    }),
    controls: []
  })

  // Listen to map view changes to update subscription
  map.value.getView().on('change:center', debounceUpdateSubscription)
  map.value.getView().on('change:resolution', debounceUpdateSubscription)

  // Add pointermove listener for hover popup (like ZoneMap.vue)
  map.value.on('pointermove', handlePointerMove)
}

function debounceUpdateSubscription() {
  if (subscriptionDebounceTimer) {
    clearTimeout(subscriptionDebounceTimer)
  }
  
  subscriptionDebounceTimer = window.setTimeout(() => {
    updateSubscription()
    subscriptionDebounceTimer = null
  }, SUBSCRIPTION_DEBOUNCE_MS)
}

function getMapViewPolygon(): Point[] {
  if (!map.value) return []
  
  const view = map.value.getView()
  const size = map.value.getSize()
  if (!size) return []
  
  const extent = view.calculateExtent(size)
  
  // Convert extent corners to lon/lat
  const bottomLeft = toLonLat([extent[0], extent[1]])
  const bottomRight = toLonLat([extent[2], extent[1]])
  const topRight = toLonLat([extent[2], extent[3]])
  const topLeft = toLonLat([extent[0], extent[3]])
  
  // Return polygon points in clockwise order
  return [
    { lon: bottomLeft[0], lat: bottomLeft[1] },
    { lon: bottomRight[0], lat: bottomRight[1] },
    { lon: topRight[0], lat: topRight[1] },
    { lon: topLeft[0], lat: topLeft[1] },
    { lon: bottomLeft[0], lat: bottomLeft[1] } // Close the polygon
  ]
}

function updateSubscription() {
  if (!ws.value || !ws.value.isConnected) return
  
  const polygon = getMapViewPolygon()
  if (polygon.length === 0) return
  
  // Check if polygon has changed significantly
  if (currentSubscription && polygonsAreSimilar(currentSubscription, polygon)) {
    return
  }
  
  // Subscribe to new polygon (no need to unsubscribe from previous)
  ws.value.subscribeToPolygon(polygon)
  currentSubscription = polygon
  
  console.log('Updated subscription polygon:', polygon)
}

function polygonsAreSimilar(poly1: Point[], poly2: Point[], threshold: number = 0.01): boolean {
  if (poly1.length !== poly2.length) return false
  
  for (let i = 0; i < poly1.length; i++) {
    const diffLon = Math.abs(poly1[i].lon - poly2[i].lon)
    const diffLat = Math.abs(poly1[i].lat - poly2[i].lat)
    
    if (diffLon > threshold || diffLat > threshold) {
      return false
    }
  }
  
  return true
}

function setupWebSocket() {
  const url = getWebSocketUrl()
  
  ws.value = new TelemetryWebSocket(url, {
    onOpen: () => {
      connectionStatus.value = 'OPEN'
      connectionStatusText.value = 'Подключено'
      console.log('WebSocket connected, setting up initial subscription')
      
      // Initial subscription
      updateSubscription()
      
      // Start marker animations
      if (markerManager.value) {
        markerManager.value.startAnimation()
      }
    },
    
    onClose: (event) => {
      connectionStatus.value = 'CLOSED'
      connectionStatusText.value = `Отключено (код: ${event.code})`
      console.log('WebSocket closed:', event.code, event.reason)
      
      // Stop animations
      if (markerManager.value) {
        markerManager.value.stopAnimation()
      }
    },
    
    onError: (error) => {
      connectionStatus.value = 'ERROR'
      connectionStatusText.value = 'Ошибка подключения'
      console.error('WebSocket error:', error)
    },
    
    onReconnect: (attempt) => {
      connectionStatus.value = 'CONNECTING'
      connectionStatusText.value = `Переподключение (попытка ${attempt})`
      console.log(`Reconnection attempt ${attempt}`)
    },
    
    onMessage: (message) => {
      handleTelemetryMessage(message)
    }
  })
  
  // Initialize marker manager
  markerManager.value = new MarkerManager()
  
  // Connect WebSocket
  ws.value.connect()
}

function handleTelemetryMessage(message: any) {
  if (!markerManager.value || !vectorSource) return
  
  const deviceId = message.deviceId
  
  // Check if we already have a marker for this device
  const existingMarker = markerManager.value.getMarker(deviceId)
  
  if (existingMarker) {
    // Update existing marker
    markerManager.value.updateVehicle({
      deviceId: deviceId,
      vehicleId: message.vehicleId,
      latitude: message.latitude,
      longitude: message.longitude,
      azimuth: message.azimuth,
      packetTime: message.packetTime,
      receptionTime: message.receptionTime
    })
    
    // Store raw WebSocket data on the feature for popup
    existingMarker.feature.set('rawData', message)
  } else {
    // Create new marker
    const marker = markerManager.value.updateVehicle({
      deviceId: deviceId,
      vehicleId: message.vehicleId,
      latitude: message.latitude,
      longitude: message.longitude,
      azimuth: message.azimuth,
      packetTime: message.packetTime,
      receptionTime: message.receptionTime
    })
    
    // Store raw WebSocket data on the feature for popup
    marker.feature.set('rawData', message)
    
    // Double-check that the feature isn't already in the vector source
    // (race condition protection)
    const features = vectorSource.getFeatures()
    if (!features.includes(marker.feature)) {
      vectorSource.addFeature(marker.feature)
    }
  }
  
  // Update vehicle count
  vehicleCount.value = markerManager.value.getAllMarkers().length
}

function cleanup() {
  // Clean up WebSocket
  if (ws.value) {
    ws.value.disconnect()
    ws.value = null
  }
  
  // Clean up marker manager
  if (markerManager.value) {
    markerManager.value.dispose()
    markerManager.value = null
  }
  
  // Clean up vector source
  if (vectorSource) {
    vectorSource.clear()
    vectorSource = null
  }
  
  // Clean up timers
  if (subscriptionDebounceTimer) {
    clearTimeout(subscriptionDebounceTimer)
    subscriptionDebounceTimer = null
  }
  
  // Clean up popup timer
  if (hidePopupTimer) {
    clearTimeout(hidePopupTimer)
    hidePopupTimer = null
  }
}

// Popup functions (simple approach like ZoneMap.vue)
function cancelPopupHide() {
  if (hidePopupTimer) {
    clearTimeout(hidePopupTimer)
    hidePopupTimer = null
  }
}

function schedulePopupHide() {
  cancelPopupHide()
  hidePopupTimer = window.setTimeout(() => {
    if (popupVisible.value) {
      popupVisible.value = false
      popupData.value = ''
    }
    hidePopupTimer = null
  }, HIDE_POPUP_DELAY)
}

function closePopup() {
  popupVisible.value = false
  popupData.value = ''
}

function handlePointerMove(event: any) {
  if (!map.value || event.dragging) {
    return
  }

  const pixel = map.value.getEventPixel(event.originalEvent)
  const feature = map.value.forEachFeatureAtPixel(pixel, (f) => f, { hitTolerance: 10 })
  
  if (feature) {
    // Get the raw WebSocket data stored on the feature
    const rawData = feature.get('rawData')
    
    if (rawData) {
      // Format JSON for display
      popupData.value = JSON.stringify(rawData, null, 2)
    } else {
      // Fallback to feature properties
      const props = feature.getProperties()
      popupData.value = JSON.stringify(props, null, 2)
    }
    
    // Calculate popup position relative to map container
    const rect = mapContainer.value?.getBoundingClientRect()
    if (rect) {
      popupLeft.value = event.originalEvent.clientX - rect.left + 12
      popupTop.value = event.originalEvent.clientY - rect.top + 12
    }
    
    popupVisible.value = true
    cancelPopupHide()
    map.value.getTargetElement().style.cursor = 'pointer'
  } else {
    map.value.getTargetElement().style.cursor = ''
    schedulePopupHide()
  }
}

onMounted(() => {
  createMap()
  setupWebSocket()
})

onBeforeUnmount(() => {
  cleanup()
  
  if (map.value) {
    map.value.setTarget(undefined)
    map.value = null
  }
})

// Watch for connection status changes
watch(connectionStatus, (status) => {
  console.log('Connection status changed:', status)
})
</script>

<style scoped>
.vehicles-map-container {
  width: 100%;
  height: 100%;
  position: relative;
}

.vehicles-map {
  width: 100%;
  height: 100%;
}

.connection-status {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  font-size: 14px;
}

.status-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.status-indicator.connecting {
  background-color: #ff9800;
  animation: pulse 1.5s infinite;
}

.status-indicator.open {
  background-color: #4caf50;
}

.status-indicator.closed {
  background-color: #f44336;
}

.status-indicator.error {
  background-color: #f44336;
  animation: pulse 1s infinite;
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

.vehicle-counter {
  position: absolute;
  bottom: 10px;
  left: 10px;
  background: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 13px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 1000;
}

/* Popup styles */
.vehicle-popup {
  position: absolute;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
  min-width: 300px;
  max-width: 500px;
  max-height: 400px;
  overflow: hidden;
  z-index: 1001;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.4;
  pointer-events: auto;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 15px;
  background: #2196f3;
  color: white;
  font-weight: bold;
  font-size: 14px;
  font-family: sans-serif;
}

.popup-title {
  flex: 1;
}

.popup-close {
  background: transparent;
  border: none;
  color: white;
  font-size: 20px;
  cursor: pointer;
  line-height: 1;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: background-color 0.2s;
}

.popup-close:hover {
  background: rgba(255, 255, 255, 0.2);
}

.popup-content {
  padding: 15px;
  background: #f8f9fa;
  max-height: 350px;
  overflow-y: auto;
}

.json-data {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  color: #333;
}


/* Dark theme for JSON syntax highlighting */
.json-data .key { color: #d32f2f; }
.json-data .string { color: #388e3c; }
.json-data .number { color: #1976d2; }
.json-data .boolean { color: #7b1fa2; }
.json-data .null { color: #616161; }
</style>