<template>
  <div class="navigation-search-page">
    <div class="tabs">
      <button
        :class="['tab-button', { active: activeTab === 'device' }]"
        @click="activeTab = 'device'"
      >
        Навигация устройства
      </button>
      <button
        :class="['tab-button', { active: activeTab === 'zone' }]"
        @click="activeTab = 'zone'"
      >
        Поиск в зоне
      </button>
    </div>

    <div class="tab-content">
      <!-- Device Navigation Tab -->
      <div v-show="activeTab === 'device'" class="device-tab">
        <div class="search-form">
          <div class="form-row">
            <div class="form-group">
              <label for="device-input">Устройство</label>
              <input
                id="device-input"
                v-model="deviceInput"
                type="text"
                placeholder="Введите ID устройства или название"
                class="form-input"
              />
            </div>
            <div class="form-group">
              <label for="date-from">С</label>
              <input
                id="date-from"
                v-model="dateFrom"
                type="datetime-local"
                class="form-input"
              />
            </div>
            <div class="form-group">
              <label for="date-to">По</label>
              <input
                id="date-to"
                v-model="dateTo"
                type="datetime-local"
                class="form-input"
              />
            </div>
            <button class="primary-button" @click="searchDeviceNavigation">
              Поиск
            </button>
          </div>
        </div>
        <div class="map-container">
          <div ref="deviceMapContainer" class="map"></div>
          <div v-if="deviceSearchLoading" class="loading-overlay">
            Загрузка...
          </div>
        </div>
      </div>

      <!-- Zone Search Tab -->
      <div v-show="activeTab === 'zone'" class="zone-tab">
        <div class="search-form">
          <div class="form-row">
            <div class="form-group">
              <label for="zone-date-from">С</label>
              <input
                id="zone-date-from"
                v-model="zoneDateFrom"
                type="datetime-local"
                step="300"
                class="form-input"
                @change="roundZoneDateFrom"
              />
              <div class="hint">Время кратно 5 минутам</div>
            </div>
            <div class="form-group">
              <label for="zone-date-to">По</label>
              <input
                id="zone-date-to"
                v-model="zoneDateTo"
                type="datetime-local"
                step="300"
                class="form-input"
                @change="roundZoneDateTo"
              />
              <div class="hint">Время кратно 5 минутам</div>
            </div>
            <button class="primary-button" @click="searchZoneNavigation">
              Поиск
            </button>
            <button
              class="secondary-button"
              @click="drawingMode = !drawingMode"
              :class="{ active: drawingMode }"
            >
              {{ drawingMode ? 'Отменить рисование' : 'Нарисовать зону' }}
            </button>
            <div v-if="drawnRectangle" class="drawn-info">
              <div>Выделена зона: {{ drawnRectangleBounds }}</div>
              <button class="clear-button" @click="clearDrawnRectangle">Очистить</button>
            </div>
          </div>
        </div>
        <div v-show="!showZoneResults" class="map-container">
          <div ref="zoneMapContainer" class="map"></div>
          <div v-if="zoneSearchLoading" class="loading-overlay">
            Загрузка...
          </div>
        </div>

        <!-- Results panel -->
        <div v-if="showZoneResults" class="results-panel">
          <div class="results-header">
            <h3>Найденные устройства в зоне</h3>
            <button class="close-button" @click="hideZoneResults">×</button>
          </div>
          
          <div v-if="zoneSearchError" class="error-message">
            {{ zoneSearchError }}
          </div>
          
          <div v-if="zoneSearchResults.length === 0 && !zoneSearchError" class="no-results">
            Устройства не найдены
          </div>
          
          <div v-else class="results-list">
            <div class="result-item" v-for="interval in zoneSearchResults" :key="interval.deviceId">
              <div class="device-info">
                <div class="device-id">Устройство ID: {{ interval.deviceId }}</div>
                <div class="vehicle-id">Транспорт ID: {{ interval.vehicleId }}</div>
                <div class="time-range">
                  Период: {{ formatDateTime(interval.fromDateTime) }} – {{ formatDateTime(interval.toDateTime) }}
                </div>
              </div>
              <div class="actions">
                <button class="view-button" @click="buildTrackForDevice(interval)">
                  Построить трек
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Dialog: Нет данных -->
    <div v-if="showNoDataDialog" class="dialog-overlay">
      <div class="dialog">
        <div class="dialog-header">
          <h3>Нет данных</h3>
          <button class="dialog-close" @click="showNoDataDialog = false">×</button>
        </div>
        <div class="dialog-content">
          <p>По указанным параметрам не найдено телеметрических данных.</p>
          <p>Попробуйте изменить параметры поиска.</p>
        </div>
        <div class="dialog-footer">
          <button class="primary-button" @click="showNoDataDialog = false">
            Закрыть
          </button>
        </div>
      </div>
    </div>

    <!-- Popup for telemetry data on hover -->
    <div
      v-if="hoverPopup.visible"
      class="telemetry-popup"
      :style="{
        left: hoverPopup.x + 'px',
        top: hoverPopup.y + 'px'
      }"
    >
      <div class="popup-arrow"></div>
      <div class="popup-content">
        <h4>Телеметрические данные</h4>
        <div class="popup-row">
          <span class="popup-label">Устройство:</span>
          <span class="popup-value">{{ hoverPopup.data?.deviceId }}</span>
        </div>
        <div class="popup-row">
          <span class="popup-label">Транспорт:</span>
          <span class="popup-value">{{ hoverPopup.data?.vehicleId }}</span>
        </div>
        <div class="popup-row">
          <span class="popup-label">Широта:</span>
          <span class="popup-value">{{ hoverPopup.data?.latitude?.toFixed(6) }}</span>
        </div>
        <div class="popup-row">
          <span class="popup-label">Долгота:</span>
          <span class="popup-value">{{ hoverPopup.data?.longitude?.toFixed(6) }}</span>
        </div>
        <div class="popup-row">
          <span class="popup-label">Азимут:</span>
          <span class="popup-value">{{ hoverPopup.data?.azimuth }}°</span>
        </div>
        <div class="popup-row">
          <span class="popup-label">Время пакета:</span>
          <span class="popup-value">{{ formatDateTime(hoverPopup.data?.packetTime) }}</span>
        </div>
        <div class="popup-row">
          <span class="popup-label">Время приёма:</span>
          <span class="popup-value">{{ formatDateTime(hoverPopup.data?.receptionTime) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import OSM from 'ol/source/OSM'
import Feature from 'ol/Feature'
import Polygon from 'ol/geom/Polygon'
import Point from 'ol/geom/Point'
import LineString from 'ol/geom/LineString'
import { Style, Fill, Stroke, Circle as CircleStyle } from 'ol/style'
import { fromLonLat, toLonLat } from 'ol/proj'
import Draw from 'ol/interaction/Draw'
import { getDistance } from 'ol/sphere'
import Overlay from 'ol/Overlay'
import { getDeviceTelemetry, queryTelemetryByPolygon, type TelemetryPacket, type TelemetryIntervalResponse, type PointDto } from '../api/telemetry'
import { createTriangleStyle } from '../utils/markerUtils'

type Tab = 'device' | 'zone'

const activeTab = ref<Tab>('device')
const deviceInput = ref('')
const dateFrom = ref('')
const dateTo = ref('')
const zoneDateFrom = ref('')
const zoneDateTo = ref('')
const drawingMode = ref(false)
const drawnRectangle = ref<Feature | null>(null)
const drawnRectangleBounds = ref('')
const deviceSearchLoading = ref(false)
const zoneSearchLoading = ref(false)
const showNoDataDialog = ref(false)

// Zone search results state
const showZoneResults = ref(false)
const zoneSearchResults = ref<TelemetryIntervalResponse[]>([])
const zoneSearchError = ref<string>('')

// Hover popup state
const hoverPopup = ref({
  visible: false,
  x: 0,
  y: 0,
  data: null as TelemetryPacket | null
})

// Map references
const deviceMapContainer = ref<HTMLDivElement | null>(null)
const zoneMapContainer = ref<HTMLDivElement | null>(null)
let deviceMap: Map | null = null
let zoneMap: Map | null = null
let drawInteraction: Draw | null = null
let vectorSource: VectorSource | null = null

// Telemetry visualization layers
let deviceVectorSource: VectorSource | null = null
let deviceVectorLayer: VectorLayer<VectorSource> | null = null
let lineVectorSource: VectorSource | null = null
let lineVectorLayer: VectorLayer<VectorSource> | null = null

// Store telemetry data for popup access
let currentTelemetryData: TelemetryPacket[] = []

// Initialize maps
onMounted(() => {
  if (deviceMapContainer.value) {
    // Create vector sources for device telemetry visualization
    deviceVectorSource = new VectorSource()
    lineVectorSource = new VectorSource()
    
    deviceVectorLayer = new VectorLayer({
      source: deviceVectorSource,
      // Style will be set per feature based on azimuth
      style: (feature) => {
        const azimuth = feature.get('azimuth') || 0
        return createTriangleStyle(azimuth, '#1b5e20', 0)
      }
    })
    
    lineVectorLayer = new VectorLayer({
      source: lineVectorSource,
      style: new Style({
        stroke: new Stroke({
          color: '#2196f3',
          width: 3,
          lineDash: [5, 5],
          lineCap: 'round',
          lineJoin: 'round'
        })
      })
    })

    deviceMap = new Map({
      target: deviceMapContainer.value,
      layers: [
        new TileLayer({ source: new OSM() }),
        lineVectorLayer,
        deviceVectorLayer
      ],
      view: new View({
        center: fromLonLat([30.437888132963106, 59.962961568076395]),
        zoom: 15,
        minZoom: 3,
        maxZoom: 22
      }),
      controls: []
    })

    // Setup hover interaction
    setupHoverInteraction()
  }

  if (zoneMapContainer.value) {
    vectorSource = new VectorSource()
    const vectorLayer = new VectorLayer({ source: vectorSource })

    zoneMap = new Map({
      target: zoneMapContainer.value,
      layers: [
        new TileLayer({ source: new OSM() }),
        vectorLayer
      ],
      view: new View({
        center: fromLonLat([30.437888132963106, 59.962961568076395]),
        zoom: 15,
        minZoom: 3,
        maxZoom: 22
      }),
      controls: []
    })
  }
})

function setupHoverInteraction() {
  if (!deviceMap || !deviceVectorSource) return

  // Handle pointer move for hover
  deviceMap.on('pointermove', (event) => {
    if (!deviceMap) return
    
    const feature = deviceMap.forEachFeatureAtPixel(
      event.pixel,
      (feature) => feature
    )
    
    if (feature && feature.get('deviceId')) {
      // Find the telemetry data for this feature
      const deviceId = feature.get('deviceId')
      const packetTime = feature.get('packetTime')
      const telemetryData = currentTelemetryData.find(
        data => data.deviceId === deviceId && data.packetTime === packetTime
      )
      
      if (telemetryData) {
        const coordinate = event.coordinate
        const pixel = deviceMap.getPixelFromCoordinate(coordinate)
        
        hoverPopup.value = {
          visible: true,
          x: pixel[0] + 10,
          y: pixel[1] - 10,
          data: telemetryData
        }
        
        // Change cursor to pointer
        deviceMap.getTargetElement().style.cursor = 'pointer'
      }
    } else {
      hoverPopup.value.visible = false
      if (deviceMap) {
        deviceMap.getTargetElement().style.cursor = ''
      }
    }
  })

  // Hide popup when pointer leaves map
  deviceMap.getTargetElement().addEventListener('mouseleave', () => {
    hoverPopup.value.visible = false
    if (deviceMap) {
      deviceMap.getTargetElement().style.cursor = ''
    }
  })
}

onBeforeUnmount(() => {
  if (deviceMap) {
    deviceMap.setTarget(undefined)
    deviceMap = null
  }
  if (zoneMap) {
    zoneMap.setTarget(undefined)
    zoneMap = null
  }
  removeDrawInteraction()
})

watch(drawingMode, (newVal) => {
  if (newVal) {
    startDrawing()
  } else {
    removeDrawInteraction()
  }
})

watch(activeTab, (newTab) => {
  // Update map size when tab becomes active
  nextTick(() => {
    requestAnimationFrame(() => {
      if (newTab === 'device' && deviceMap) {
        deviceMap.updateSize()
      } else if (newTab === 'zone' && zoneMap) {
        zoneMap.updateSize()
      }
    })
  })
})

function startDrawing() {
  if (!zoneMap || !vectorSource) return
  removeDrawInteraction()
  // Use Polygon drawing, user can draw rectangle by clicking four points
  drawInteraction = new Draw({
    source: vectorSource,
    type: 'Polygon',
    maxPoints: 4,
    finishCondition: (event) => {
      // Finish after 4 clicks
      return event.coordinate.length >= 4
    }
  })
  zoneMap.addInteraction(drawInteraction)
  drawInteraction.on('drawend', (event) => {
    const feature = event.feature
    drawnRectangle.value = feature
    const geometry = feature.getGeometry() as Polygon
    const coords = geometry.getCoordinates()[0]
    const lonLatCoords = coords.map(coord => toLonLat(coord))
    drawnRectangleBounds.value = lonLatCoords
      .map(coord => `[${coord[0].toFixed(4)}, ${coord[1].toFixed(4)}]`)
      .join(', ')
    drawingMode.value = false
    removeDrawInteraction()
  })
}

function removeDrawInteraction() {
  if (zoneMap && drawInteraction) {
    zoneMap.removeInteraction(drawInteraction)
    drawInteraction = null
  }
}

function roundToFiveMinutes(dateTimeString: string): string {
  if (!dateTimeString) return dateTimeString
  // Parse datetime string: YYYY-MM-DDTHH:mm
  const [datePart, timePart] = dateTimeString.split('T')
  if (!timePart) return dateTimeString
  const [hours, minutes] = timePart.split(':').map(Number)
  const roundedMinutes = Math.round(minutes / 5) * 5
  const adjustedHours = hours + Math.floor(roundedMinutes / 60)
  const adjustedMinutes = roundedMinutes % 60
  const newTime = `${adjustedHours.toString().padStart(2, '0')}:${adjustedMinutes.toString().padStart(2, '0')}`
  return `${datePart}T${newTime}`
}

function roundZoneDateFrom() {
  if (zoneDateFrom.value) {
    zoneDateFrom.value = roundToFiveMinutes(zoneDateFrom.value)
  }
}

function roundZoneDateTo() {
  if (zoneDateTo.value) {
    zoneDateTo.value = roundToFiveMinutes(zoneDateTo.value)
  }
}

function formatDateTime(dateTimeString?: string): string {
  if (!dateTimeString) return '—'
  try {
    const date = new Date(dateTimeString)
    return date.toLocaleString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return dateTimeString
  }
}

async function searchDeviceNavigation() {
  if (!deviceInput.value.trim()) {
    alert('Пожалуйста, введите ID устройства')
    return
  }

  if (!dateFrom.value || !dateTo.value) {
    alert('Пожалуйста, укажите временной интервал')
    return
  }

  deviceSearchLoading.value = true
  hoverPopup.value.visible = false
  
  try {
    // Parse device ID (assuming numeric ID)
    const deviceId = parseInt(deviceInput.value.trim(), 10)
    if (isNaN(deviceId)) {
      throw new Error('ID устройства должен быть числом')
    }

    // Call API
    const telemetryData = await getDeviceTelemetry({
      deviceId,
      fromDateTime: dateFrom.value,
      toDateTime: dateTo.value
    })

    // Store data for popup access
    currentTelemetryData = telemetryData

    // Clear previous features
    if (deviceVectorSource) {
      deviceVectorSource.clear()
    }
    if (lineVectorSource) {
      lineVectorSource.clear()
    }

    // Check if data is empty
    if (telemetryData.length === 0) {
      showNoDataDialog.value = true
      deviceSearchLoading.value = false
      return
    }

    // Sort by packetTime to ensure chronological order
    telemetryData.sort((a, b) => 
      new Date(a.packetTime).getTime() - new Date(b.packetTime).getTime()
    )

    // Create point features
    const pointFeatures: Feature[] = []
    const lineCoordinates: number[][] = []

    telemetryData.forEach((packet: TelemetryPacket) => {
      const coord = fromLonLat([packet.longitude, packet.latitude])
      lineCoordinates.push(coord)

      const pointFeature = new Feature({
        geometry: new Point(coord),
        deviceId: packet.deviceId,
        vehicleId: packet.vehicleId,
        azimuth: packet.azimuth,
        packetTime: packet.packetTime,
        receptionTime: packet.receptionTime,
        latitude: packet.latitude,
        longitude: packet.longitude
      })
      
      // Style will be applied by the layer's style function
      pointFeatures.push(pointFeature)
    })

    // Add points to map
    if (deviceVectorSource) {
      deviceVectorSource.addFeatures(pointFeatures)
    }

    // Create smoothed line
    if (lineCoordinates.length >= 2) {
      const smoothedCoordinates = smoothLineCoordinates(lineCoordinates)
      const lineFeature = new Feature({
        geometry: new LineString(smoothedCoordinates)
      })
      if (lineVectorSource) {
        lineVectorSource.addFeature(lineFeature)
      }

      // Fit map view to show all points
      if (deviceMap && deviceVectorSource) {
        const extent = deviceVectorSource.getExtent()
        if (extent && extent[0] !== Infinity && extent[1] !== Infinity) {
          deviceMap.getView().fit(extent, {
            padding: [50, 50, 50, 50],
            duration: 1000
          })
        }
      }
    }

  } catch (error) {
    console.error('Error fetching device telemetry:', error)
    alert(`Ошибка при получении данных: ${error instanceof Error ? error.message : 'Неизвестная ошибка'}`)
  } finally {
    deviceSearchLoading.value = false
  }
}

/**
 * Smooth line coordinates using simple averaging for slight rounding
 */
function smoothLineCoordinates(coordinates: number[][]): number[][] {
  if (coordinates.length <= 2) {
    return coordinates
  }

  const smoothed: number[][] = [coordinates[0]]
  
  for (let i = 1; i < coordinates.length - 1; i++) {
    const prev = coordinates[i - 1]
    const curr = coordinates[i]
    const next = coordinates[i + 1]
    
    // Simple averaging for slight smoothing
    const smoothedPoint = [
      (prev[0] + curr[0] + next[0]) / 3,
      (prev[1] + curr[1] + next[1]) / 3
    ]
    smoothed.push(smoothedPoint)
  }
  
  smoothed.push(coordinates[coordinates.length - 1])
  return smoothed
}

function clearDrawnRectangle() {
  if (vectorSource) {
    vectorSource.clear()
  }
  drawnRectangle.value = null
  drawnRectangleBounds.value = ''
}

async function searchZoneNavigation() {
  // Validate inputs
  if (!zoneDateFrom.value || !zoneDateTo.value) {
    alert('Пожалуйста, укажите временной интервал')
    return
  }

  if (!drawnRectangle.value) {
    alert('Пожалуйста, нарисуйте полигон на карте')
    return
  }

  zoneSearchLoading.value = true
  zoneSearchError.value = ''
  showZoneResults.value = false

  try {
    // Convert drawn polygon to PointDto array
    const geometry = drawnRectangle.value.getGeometry() as Polygon
    const coords = geometry.getCoordinates()[0]
    const polygonPoints: PointDto[] = coords.map(coord => {
      const lonLat = toLonLat(coord)
      return {
        latitude: lonLat[1],
        longitude: lonLat[0]
      }
    })

    // Call API
    console.log('Calling queryTelemetryByPolygon with:', {
      polygon: polygonPoints,
      fromDateTime: zoneDateFrom.value,
      toDateTime: zoneDateTo.value
    })
    const response = await queryTelemetryByPolygon({
      polygon: polygonPoints,
      fromDateTime: zoneDateFrom.value,
      toDateTime: zoneDateTo.value
    })
    console.log('API response:', response)

    // Normalize response to array
    let resultsArray: TelemetryIntervalResponse[] = []
    if (Array.isArray(response)) {
      resultsArray = response
    } else if (response && typeof response === 'object') {
      // Check if response has 'intervals' property (old format)
      if ('intervals' in response && Array.isArray(response.intervals)) {
        resultsArray = response.intervals
      } else {
        // Assume response is a single interval object
        resultsArray = [response]
      }
    }
    console.log('Normalized results array:', resultsArray)

    // Store results
    zoneSearchResults.value = resultsArray
    showZoneResults.value = true
    console.log('State updated: showZoneResults=', showZoneResults.value, 'zoneSearchResults length=', zoneSearchResults.value.length)

    // If no results, show message
    if (resultsArray.length === 0) {
      zoneSearchError.value = 'По указанным параметрам не найдено устройств'
    }
  } catch (error) {
    console.error('Error querying telemetry by polygon:', error)
    zoneSearchError.value = `Ошибка при выполнении запроса: ${error instanceof Error ? error.message : 'Неизвестная ошибка'}`
    showZoneResults.value = false
  } finally {
    zoneSearchLoading.value = false
  }
}

function hideZoneResults() {
  showZoneResults.value = false
  // Update map size after a tick to ensure DOM is updated
  nextTick(() => {
    if (zoneMap) {
      zoneMap.updateSize()
      // Force a render to ensure map is visible
      zoneMap.render()
    }
  })
}

function buildTrackForDevice(interval: TelemetryIntervalResponse) {
  // Switch to device navigation tab
  activeTab.value = 'device'
  
  // Set device ID
  deviceInput.value = interval.deviceId.toString()
  
  // Convert ISO datetime to datetime-local format (YYYY-MM-DDTHH:mm)
  dateFrom.value = isoToDatetimeLocal(interval.fromDateTime)
  dateTo.value = isoToDatetimeLocal(interval.toDateTime)
  
  // Trigger search after a small delay to ensure DOM is updated
  nextTick(() => {
    searchDeviceNavigation()
  })
}

function isoToDatetimeLocal(isoString: string): string {
  if (!isoString) return ''
  const date = new Date(isoString)
  // Format: YYYY-MM-DDTHH:mm
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hours}:${minutes}`
}
</script>

<style scoped>
.navigation-search-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
}

.tabs {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  background: #f1f8f3;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.tab-button {
  border: 1px solid transparent;
  background: transparent;
  color: #1b5e20;
  padding: 6px 12px;
  border-radius: 8px 8px 0 0;
  cursor: pointer;
  font-weight: 700;
  min-height: 32px;
}

.tab-button.active {
  background: #fff;
  color: #1b5e20;
  border-color: rgba(0, 0, 0, 0.12);
  border-bottom-color: transparent;
}

.tab-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.search-form {
  padding: 16px;
  background: #fff;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.form-row {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 200px;
}

.form-group label {
  font-size: 0.9rem;
  font-weight: 600;
  color: #333;
}

.form-input {
  padding: 8px 12px;
  border: 1px solid #ccc;
  border-radius: 6px;
  font-size: 0.95rem;
}

.hint {
  font-size: 0.8rem;
  color: #666;
  margin-top: 2px;
}

.primary-button {
  border: none;
  border-radius: 10px;
  padding: 8px 16px;
  background: #1b5e20;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
  font-size: 0.95rem;
  height: fit-content;
  align-self: flex-end;
}

.secondary-button {
  border: 1px solid #1b5e20;
  border-radius: 10px;
  padding: 8px 16px;
  background: transparent;
  color: #1b5e20;
  cursor: pointer;
  font-weight: 700;
  font-size: 0.95rem;
  height: fit-content;
  align-self: flex-end;
}

.secondary-button.active {
  background: #1b5e20;
  color: #fff;
}

.drawn-info {
  padding: 8px 12px;
  background: #e8f5e9;
  border-radius: 6px;
  font-size: 0.9rem;
  color: #1b5e20;
}

.device-tab,
.zone-tab {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.map-container {
  position: relative;
  flex: 1;
  min-height: 0;
}

.map {
  width: 100%;
  height: 100%;
}

.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  color: #1b5e20;
}

/* Dialog styles */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: #fff;
  border-radius: 12px;
  width: 90%;
  max-width: 400px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #f1f8f3;
  border-bottom: 1px solid rgba(0, 0, 0, 0.1);
}

.dialog-header h3 {
  margin: 0;
  color: #1b5e20;
  font-size: 1.2rem;
}

.dialog-close {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #666;
  cursor: pointer;
  padding: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.dialog-close:hover {
  background: rgba(0, 0, 0, 0.05);
}

.dialog-content {
  padding: 20px;
  color: #333;
  line-height: 1.5;
}

.dialog-footer {
  padding: 16px 20px;
  background: #f9f9f9;
  border-top: 1px solid rgba(0, 0, 0, 0.1);
  display: flex;
  justify-content: flex-end;
}

/* Telemetry popup styles */
.telemetry-popup {
  position: absolute;
  z-index: 1000;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  min-width: 280px;
  max-width: 350px;
  border: 1px solid #e0e0e0;
  pointer-events: none;
}

.popup-arrow {
  position: absolute;
  top: -10px;
  left: 20px;
  width: 0;
  height: 0;
  border-left: 10px solid transparent;
  border-right: 10px solid transparent;
  border-bottom: 10px solid white;
  filter: drop-shadow(0 -1px 1px rgba(0, 0, 0, 0.1));
}

.popup-content {
  padding: 16px;
  position: relative;
  z-index: 1;
  background: white;
  border-radius: 8px;
}

.popup-content h4 {
  margin: 0 0 12px 0;
  color: #1b5e20;
  font-size: 1.1rem;
  border-bottom: 1px solid #e8f5e9;
  padding-bottom: 8px;
}

.popup-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 0.9rem;
}

.popup-row:last-child {
  margin-bottom: 0;
}

.popup-label {
  color: #666;
  font-weight: 500;
  min-width: 120px;
}

.popup-value {
  color: #333;
  font-weight: 600;
  text-align: right;
  word-break: break-word;
  max-width: 150px;
}

/* Results panel styles */
.results-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  border: 1px solid rgba(0, 0, 0, 0.12);
  overflow: hidden;
  margin: 16px;
}

.results-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: #f1f8f3;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
}

.results-header h3 {
  margin: 0;
  color: #1b5e20;
  font-size: 1.2rem;
}

.close-button {
  background: none;
  border: none;
  font-size: 1.5rem;
  color: #666;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  line-height: 1;
}

.close-button:hover {
  background: rgba(0, 0, 0, 0.05);
}

.error-message {
  padding: 16px 20px;
  background: #ffebee;
  color: #c62828;
  border-bottom: 1px solid #ffcdd2;
  font-size: 0.95rem;
}

.no-results {
  padding: 40px 20px;
  text-align: center;
  color: #666;
  font-size: 1rem;
}

.results-list {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.result-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  transition: background 0.2s;
}

.result-item:hover {
  background: #f9f9f9;
}

.result-item:last-child {
  border-bottom: none;
}

.device-info {
  flex: 1;
}

.device-id {
  font-weight: 600;
  color: #1b5e20;
  font-size: 1rem;
  margin-bottom: 4px;
}

.vehicle-id {
  color: #666;
  font-size: 0.9rem;
  margin-bottom: 4px;
}

.time-range {
  color: #333;
  font-size: 0.9rem;
  margin-bottom: 4px;
}

.entries-count {
  color: #666;
  font-size: 0.85rem;
  font-style: italic;
}

.actions {
  margin-left: 16px;
}

.view-button {
  border: 1px solid #1b5e20;
  border-radius: 6px;
  padding: 6px 12px;
  background: transparent;
  color: #1b5e20;
  cursor: pointer;
  font-weight: 600;
  font-size: 0.9rem;
}

.view-button:hover {
  background: #1b5e20;
  color: #fff;
}
</style>