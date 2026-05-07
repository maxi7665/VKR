<template>
  <div class="zone-editor-page">
    <div class="zone-page-header">
      <div class="zone-actions">
        <div class="editor-title">Редактирование зоны</div>
        <span class="hint">Перетаскивайте вершины, кликните по краю для добавления и правый клик по точке для удаления.</span>
      </div>
      <div v-if="selectedZone" class="zone-controls">
        <div class="control-row">
          <label>Режим редактирования:</label>
          <select v-model="editMode">
            <option value="polygon">Полигон</option>
            <option value="center">Центр</option>
          </select>
          <label class="toggle-label">
            <input type="checkbox" v-model="showS2" /> S2-зоны
          </label>
          <label>
            Уровень S2
            <input type="number" v-model.number="maxLevel" min="1" max="30" />
          </label>
        </div>
        <div class="control-row">
          <button class="secondary-button" @click="saveZone">Сохранить</button>
          <button class="secondary-button" @click="cancelEdit">Закрыть</button>
        </div>
      </div>
    </div>

    <div class="zone-editor-body">
      <aside class="zone-sidebar">
        <div class="zone-card">
          <h2>Зона</h2>
          <template v-if="selectedZone">
            <div class="field-row">
              <label>Название</label>
              <input v-model="selectedZone.name" />
            </div>
            <div class="field-row">
              <label>Тип</label>
              <input v-model="selectedZone.type" />
            </div>
            <div class="field-row checkbox-row">
              <label><input type="checkbox" v-model="selectedZone.isActive" /> Активна</label>
            </div>
            <div class="field-row">
              <label>Центр</label>
              <div class="coordinate-row">
                <div class="coordinate-field">
                  <input type="number" step="0.000001" v-model.number="selectedZone.lat" />
                  <span>Lat</span>
                </div>
                <div class="coordinate-field">
                  <input type="number" step="0.000001" v-model.number="selectedZone.lon" />
                  <span>Lon</span>
                </div>
              </div>
            </div>
            <div class="field-row">
              <label>Точек</label>
              <div>{{ selectedZone.coordinates?.length || 0 }}</div>
            </div>
            <div class="help-text">
              Редактируйте вершины перетаскиванием или кликните по линии/пустому месту, чтобы добавить точку.
              Правый клик на точке — удалить.
            </div>
          </template>
          <template v-else>
            <div class="empty-state">Нет зоны для редактирования. Откройте зону из списка.</div>
          </template>
        </div>
      </aside>

      <div class="zone-map-frame">
        <div ref="mapContainer" class="zone-map"></div>
        <div class="hover-popup" v-show="hoverText">{{ hoverText }}</div>
        <div v-if="contextMenuVisible" class="context-menu" :style="{ left: `${menuLeft}px`, top: `${menuTop}px` }">
          <button v-if="contextTarget === 'vertex'" @click="deleteVertex">Удалить точку</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import OSM from 'ol/source/OSM'
import Feature from 'ol/Feature'
import Polygon from 'ol/geom/Polygon'
import Point from 'ol/geom/Point'
import Collection from 'ol/Collection'
import Translate from 'ol/interaction/Translate'
import { Style, Fill, Stroke, Circle as CircleStyle } from 'ol/style'
import { fromLonLat, toLonLat } from 'ol/proj'
import Modify from 'ol/interaction/Modify'
import {
  createZone,
  getZoneById,
  getS2Cells,
  type Zone,
  type S2Cell
} from '../api/geozones'

type Coordinate = [number, number]

const props = defineProps<{
  zoneId: number | string | null
  isNew: boolean
}>()
const emit = defineEmits<{
  (event: 'close'): void
  (event: 'saved', zoneId: number | string | null): void
}>()

const mapContainer = ref<HTMLDivElement | null>(null)
const map = ref<Map | null>(null)
const hoverText = ref('')
const contextMenuVisible = ref(false)
const contextTarget = ref('')
const menuLeft = ref(0)
const menuTop = ref(0)
const contextVertexIndex = ref(-1)

const editMode = ref<'polygon' | 'center'>('polygon')
const showS2 = ref(false)
const maxLevel = ref(24)
const selectedZone = ref<Zone | null>(null)

const editSource = new VectorSource()
const centerSource = new VectorSource()
const s2Source = new VectorSource()
const centerTranslateFeatures = new Collection<Feature<Point>>()

let modifyInteraction: Modify | null = null
let translateInteraction: Translate | null = null
let isMapReady = false
const centerFeature = ref<Feature<Point> | null>(null)

const editorOpen = computed(() => !!selectedZone.value)

function createZoneFeature(zone: Zone) {
  const coords = Array.isArray(zone.coordinates) ? zone.coordinates.map((coord) => fromLonLat([coord[1], coord[0]])) : []
  const polygon = new Polygon([coords])
  const feature = new Feature({ geometry: polygon })
  feature.set('zoneData', zone)
  return feature
}

function createS2Feature(item: S2Cell) {
  const polyCoords = item.polygon.map((pt) => fromLonLat([pt[1], pt[0]]))
  const poly = new Polygon([polyCoords])
  const feature = new Feature({ geometry: poly })
  feature.set('s2Data', item)
  return feature
}

function createCenterFeature(zone: Zone) {
  if (typeof zone.lat !== 'number' || typeof zone.lon !== 'number') {
    return null
  }

  const point = new Point(fromLonLat([zone.lon, zone.lat]))
  const feature = new Feature({ geometry: point })
  feature.set('centerData', zone)
  return feature
}

function updateCenterFeaturePosition() {
  if (!selectedZone.value || !centerFeature.value) return

  const coordinate = fromLonLat([selectedZone.value.lon, selectedZone.value.lat])
  const geometry = centerFeature.value.getGeometry() as Point
  geometry.setCoordinates(coordinate)
}

function updateCenterFromFeature() {
  if (!selectedZone.value || !centerFeature.value) return

  const geometry = centerFeature.value.getGeometry() as Point
  const coordinate = geometry.getCoordinates()
  const [lon, lat] = toLonLat(coordinate)
  selectedZone.value.lat = lat
  selectedZone.value.lon = lon
}

function createMap() {
  const editLayer = new VectorLayer({
    source: editSource,
    style: new Style({
      fill: new Fill({ color: 'rgba(76, 175, 80, 0.18)' }),
      stroke: new Stroke({ color: '#388e3c', width: 2 })
    })
  })
  editLayer.setZIndex(20)

  const centerLayer = new VectorLayer({
    source: centerSource,
    style: new Style({
      image: new CircleStyle({
        radius: 8,
        fill: new Fill({ color: '#d32f2f' }),
        stroke: new Stroke({ color: '#fff', width: 2 })
      })
    })
  })
  centerLayer.setZIndex(40)

  const s2Layer = new VectorLayer({
    source: s2Source,
    style: new Style({
      fill: new Fill({ color: 'rgba(255, 193, 7, 0.18)' }),
      stroke: new Stroke({ color: '#fbc02d', width: 1 })
    })
  })
  s2Layer.setZIndex(30)

  map.value = new Map({
    target: mapContainer.value as HTMLDivElement,
    layers: [
      new TileLayer({ source: new OSM() }),
      s2Layer,
      editLayer,
      centerLayer
    ],
    view: new View({
      center: fromLonLat([30.437888132963106, 59.962961568076395]),
      zoom: 15,
      minZoom: 3,
      maxZoom: 22
    }),
    controls: []
  })

  modifyInteraction = new Modify({ source: editSource, pixelTolerance: 12 })
  modifyInteraction.on('modifyend', () => {
    updateEditorCoordinates()
  })
  map.value.addInteraction(modifyInteraction)
  modifyInteraction.setActive(editMode.value === 'polygon')

  translateInteraction = new Translate({ features: centerTranslateFeatures })
  translateInteraction.setActive(editMode.value === 'center')
  translateInteraction.on('translateend', () => {
    updateCenterFromFeature()
  })
  map.value.addInteraction(translateInteraction)

  map.value.on('pointermove', handlePointerMove)
  map.value.on('singleclick', handleMapClick)
  map.value.getViewport().addEventListener('contextmenu', handleContextMenu)

  isMapReady = true
}

function handlePointerMove(event: any) {
  if (!map.value || event.dragging) {
    hoverText.value = ''
    return
  }

  const pixel = map.value.getEventPixel(event.originalEvent)
  const feature = map.value.forEachFeatureAtPixel(pixel, (f) => f)

  if (feature?.get('s2Data')) {
    hoverText.value = JSON.stringify(feature.get('s2Data'), null, 2)
  } else {
    hoverText.value = ''
  }
}

function handleMapClick(event: any) {
  if (!editorOpen.value) {
    return
  }

  if (editMode.value !== 'polygon') {
    return
  }

  if (!editSource.getFeatures().length) {
    return
  }

  addPointAt(event.coordinate)
}

function handleContextMenu(event: MouseEvent) {
  if (editMode.value !== 'polygon') {
    return
  }

  event.preventDefault()
  if (!map.value) return

  const pixel = map.value.getEventPixel(event)
  const coordinate = (event as any).coordinate || map.value.getCoordinateFromPixel(pixel)
  const vertexInfo = findClosestVertex(coordinate as Coordinate)

  if (vertexInfo && vertexInfo.distance < (map.value.getView().getResolution() ?? 0) * 14) {
    contextTarget.value = 'vertex'
    contextVertexIndex.value = vertexInfo.index
    showContextMenu(event.clientX, event.clientY)
    return
  }

  hideContextMenu()
}

function showContextMenu(left: number, top: number) {
  menuLeft.value = left
  menuTop.value = top
  contextMenuVisible.value = true
}

function hideContextMenu() {
  contextMenuVisible.value = false
  contextTarget.value = ''
  contextVertexIndex.value = -1
}

function deleteVertex() {
  if (!editorOpen.value || contextVertexIndex.value < 0) {
    hideContextMenu()
    return
  }

  const feature = editSource.getFeatures()[0]
  if (!feature) {
    hideContextMenu()
    return
  }

  const geometry = feature.getGeometry() as Polygon
  const coords = geometry.getCoordinates()[0].slice()
  if (coords.length <= 4) {
    alert('Нельзя удалить точку — полигон должен содержать минимум 3 вершины.')
    hideContextMenu()
    return
  }

  coords.splice(contextVertexIndex.value, 1)
  geometry.setCoordinates([coords])
  updateEditorCoordinates()
  hideContextMenu()
}

function addPointAt(mapCoordinate: Coordinate) {
  const feature = editSource.getFeatures()[0]
  if (!feature) return

  const geometry = feature.getGeometry() as Polygon
  const ring = geometry.getCoordinates()[0].slice() as Coordinate[]
  if (ring.length < 2) return

  const { index, distance } = findClosestSegment(mapCoordinate, ring)
  const threshold = (map.value?.getView().getResolution() ?? 0) * 14
  if (index >= 0 && distance <= threshold) {
    ring.splice(index + 1, 0, mapCoordinate)
  } else {
    ring.splice(ring.length - 1, 0, mapCoordinate)
  }

  geometry.setCoordinates([ring])
  updateEditorCoordinates()
}

function findClosestVertex(coord: Coordinate) {
  const feature = editSource.getFeatures()[0]
  if (!feature) return null

  const coords = (feature.getGeometry() as Polygon).getCoordinates()[0] as Coordinate[]
  let bestDistance = Infinity
  let bestIndex = -1

  for (let i = 0; i < coords.length; i += 1) {
    const d = distanceBetween(coords[i], coord)
    if (d < bestDistance) {
      bestDistance = d
      bestIndex = i
    }
  }

  return bestIndex >= 0 ? { index: bestIndex, distance: bestDistance } : null
}

function findClosestSegment(point: Coordinate, coords: Coordinate[]) {
  let bestIndex = -1
  let bestDistance = Infinity

  for (let i = 0; i < coords.length - 1; i += 1) {
    const segmentStart = coords[i]
    const segmentEnd = coords[i + 1]
    const distance = pointToSegmentDistance(point, segmentStart, segmentEnd)
    if (distance < bestDistance) {
      bestDistance = distance
      bestIndex = i
    }
  }

  return { index: bestIndex, distance: bestDistance }
}

function pointToSegmentDistance(point: Coordinate, start: Coordinate, end: Coordinate) {
  const x = point[0]
  const y = point[1]
  const x1 = start[0]
  const y1 = start[1]
  const x2 = end[0]
  const y2 = end[1]
  const dx = x2 - x1
  const dy = y2 - y1

  if (dx === 0 && dy === 0) {
    return distanceBetween(point, start)
  }

  const t = ((x - x1) * dx + (y - y1) * dy) / (dx * dx + dy * dy)
  const projection: Coordinate = t < 0 ? start : t > 1 ? end : [x1 + t * dx, y1 + t * dy]
  return distanceBetween(point, projection)
}

function distanceBetween(a: Coordinate, b: Coordinate) {
  const dx = a[0] - b[0]
  const dy = a[1] - b[1]
  return Math.sqrt(dx * dx + dy * dy)
}

function createDefaultZone(): Zone {
  const coords: Coordinate[] = [
    [59.9645, 30.4355],
    [59.9645, 30.4395],
    [59.9615, 30.4395],
    [59.9615, 30.4355]
  ]

  return {
    id: null,
    name: 'Новая зона',
    type: 'polygon',
    coordinates: coords,
    isActive: true,
    s2Key: 0,
    lat: 59.963,
    lon: 30.437
  }
}

async function loadZoneById() {
  editSource.clear()
  s2Source.clear()
  selectedZone.value = null

  if (props.zoneId == null || props.isNew) {
    selectedZone.value = createDefaultZone()
  } else {
    try {
      var zone = await getZoneById(props.zoneId)
      selectedZone.value = zone
    } catch (error) {
      console.warn('Ошибка загрузки зоны:', error)
      alert('Ошибка загрузки зоны. Смотрите консоль.')
      return
    }
  }

  prepareEditorFeature()
  if (props.zoneId != null && !props.isNew) {
    centerMapOnZone(selectedZone.value)
  }
  editMode.value = 'polygon'
  showS2.value = false
}

function centerMapOnZone(zone: Zone | null) {
  if (!map.value || !zone) return
  if (typeof zone.lat !== 'number' || typeof zone.lon !== 'number') return

  const view = map.value.getView()
  const size = map.value.getSize()
  if (!view || !size) return

  const feature = editSource.getFeatures()[0]
  if (feature) {
    const geometry = feature.getGeometry()
    if (geometry) {
      const extent = geometry.getExtent()
      const padding = [size[1] * 0.25, size[0] * 0.25, size[1] * 0.25, size[0] * 0.25]
      view.fit(extent, {
        size,
        padding,
        duration: 250,
        maxZoom: 18
      })
      return
    }
  }

  view.setCenter(fromLonLat([zone.lon, zone.lat]))
}

function prepareEditorFeature() {
  editSource.clear()
  centerSource.clear()
  centerFeature.value = null
  if (!selectedZone.value) return

  if (selectedZone.value.coordinates && selectedZone.value.coordinates.length) {
    const feature = createZoneFeature(selectedZone.value)
    if (feature) {
      editSource.addFeature(feature)
    }
  }

  const newCenterFeature = createCenterFeature(selectedZone.value)
  if (newCenterFeature) {
    centerFeature.value = newCenterFeature
    centerSource.addFeature(newCenterFeature)
    centerTranslateFeatures.clear()
    centerTranslateFeatures.push(newCenterFeature)
  }

  modifyInteraction?.setActive(editMode.value === 'polygon')
  translateInteraction?.setActive(editMode.value === 'center')
}

function updateEditorCoordinates() {
  const feature = editSource.getFeatures()[0]
  if (!feature) return

  const geometry = feature.getGeometry() as Polygon
  const coords = (geometry.getCoordinates()[0] as Coordinate[])
  const normalized = coords.map((coord) => toLonLat(coord).reverse() as Coordinate)
  if (selectedZone.value) {
    selectedZone.value.coordinates = normalized
  }
}

function computeCentroid(coords: Coordinate[]) {
  if (!coords.length) return [0, 0] as Coordinate
  const total = coords.reduce((acc, point) => [acc[0] + point[0], acc[1] + point[1]] as Coordinate, [0, 0] as Coordinate)
  return [total[0] / coords.length, total[1] / coords.length] as Coordinate
}

function cancelEdit() {
  emit('close')
  selectedZone.value = null
  editSource.clear()
  s2Source.clear()
  showS2.value = false
  hideContextMenu()
}

async function saveZone() {
  if (!selectedZone.value) return

  if (selectedZone.value.id) {
    alert('Сохранение правок пока доступно локально. У API нет метода обновления зоны.')
    return
  }

  try {
    const result = await createZone({
      name: selectedZone.value.name,
      type: selectedZone.value.type,
      coordinates: selectedZone.value.coordinates,
      isActive: selectedZone.value.isActive,
      s2Key: selectedZone.value.s2Key || 0,
      lat: selectedZone.value.lat,
      lon: selectedZone.value.lon
    })

    selectedZone.value.id = result.id ?? selectedZone.value.id
    alert('Зона создана успешно')
    emit('saved', selectedZone.value.id)
    emit('close')
  } catch (error) {
    console.warn('Ошибка создания зоны:', error)
    alert('Ошибка создания зоны. Смотрите консоль.')
  }
}

watch(editMode, (value) => {
  if (!modifyInteraction || !translateInteraction) return
  modifyInteraction.setActive(value === 'polygon')
  translateInteraction.setActive(value === 'center')
})

watch(showS2, () => {
  updateS2Cells()
})

watch(maxLevel, () => {
  if (showS2.value) {
    updateS2Cells()
  }
})

watch(selectedZone, () => {
  if (showS2.value) {
    updateS2Cells()
  }
})

watch([
  () => selectedZone.value?.lat,
  () => selectedZone.value?.lon
], () => {
  updateCenterFeaturePosition()
})

async function updateS2Cells() {
  s2Source.clear()
  if (!showS2.value || !selectedZone.value || !selectedZone.value.coordinates.length) {
    return
  }

  try {
    const cells = await getS2Cells(selectedZone.value.coordinates, maxLevel.value)
    if (!Array.isArray(cells)) {
      return
    }

    cells.forEach((item) => {
      const feature = createS2Feature(item as S2Cell)
      if (feature) {
        s2Source.addFeature(feature)
      }
    })
  } catch (error) {
    console.warn('Ошибка запроса S2-зон:', error)
  }
}

onMounted(() => {
  createMap()
  loadZoneById()
})

watch(() => props.zoneId, () => {
  loadZoneById()
})

watch(() => props.isNew, () => {
  loadZoneById()
})

onBeforeUnmount(() => {
  if (!map.value) return
  map.value.getViewport().removeEventListener('contextmenu', handleContextMenu)
  map.value.setTarget(undefined)
  map.value = null
})
</script>

<style scoped>
.zone-editor-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.zone-page-header {
  padding: 18px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  background: #fff;
}

.zone-actions {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.primary-button,
.secondary-button {
  border: none;
  border-radius: 12px;
  padding: 10px 16px;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
}

.primary-button {
  background: #1b5e20;
}

.secondary-button {
  background: #388e3c;
}

.hint {
  color: #4f4f4f;
  font-size: 0.95rem;
}

.zone-controls {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.control-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.control-row label {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-weight: 600;
}

input[type='number'],
input[type='text'],
select {
  min-width: 90px;
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid rgba(0, 0, 0, 0.14);
}

.zone-editor-body {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.zone-sidebar {
  width: 320px;
  padding: 18px;
  background: #fafafa;
  border-right: 1px solid rgba(0, 0, 0, 0.08);
  overflow-y: auto;
}

.zone-card {
  background: #fff;
  border-radius: 20px;
  padding: 18px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.04);
}

.zone-card h2 {
  margin: 0 0 16px;
}

.field-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
}

.checkbox-row {
  flex-direction: row;
  align-items: center;
}

.coordinate-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.coordinate-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.coordinates {
  color: #424242;
}

.help-text,
.empty-state {
  margin-top: 12px;
  color: #616161;
  line-height: 1.5;
}

.zone-map-frame {
  position: relative;
  flex: 1;
  min-height: 0;
}

.zone-map {
  width: 100%;
  height: 100%;
}

.hover-popup {
  position: absolute;
  left: 16px;
  top: 16px;
  z-index: 10;
  min-width: 220px;
  max-width: 360px;
  max-height: 260px;
  pointer-events: none;
  overflow: auto;
  padding: 12px;
  border-radius: 12px;
  background: rgba(33, 33, 33, 0.88);
  color: #fff;
  font-size: 0.8rem;
  white-space: pre-wrap;
}

.context-menu {
  position: absolute;
  z-index: 20;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border-radius: 12px;
  background: #ffffffee;
  box-shadow: 0 10px 25px rgba(0, 0, 0, 0.15);
}

.context-menu button {
  border: none;
  background: #1976d2;
  color: #fff;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
}
</style>
