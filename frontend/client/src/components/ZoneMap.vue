<template>
  <div class="zone-list-page">
    <div class="zone-page-header">
      <div class="zone-actions">
        <button class="primary-button" @click="emit('create-zone')">Создать</button>
        <span class="hint">Правый клик на зоне откроет её в редакторе.</span>
      </div>
    </div>

    <div class="zone-map-frame">
      <div ref="mapContainer" class="zone-map"></div>
      <div
        class="hover-popup"
        v-if="popupVisible"
        :style="{ left: `${popupLeft}px`, top: `${popupTop}px` }"
        @pointerenter.capture="onPopupMouseEnter"
        @pointerleave.capture="onPopupMouseLeave"
      >
        {{ hoverText }}
      </div>
    </div>

    <div class="zone-table-panel" :class="{ open: tableOpen }">
      <button type="button" class="panel-toggle" @click="tableOpen = !tableOpen">
        Список зон ({{ visibleZones.length }}) <span>{{ tableOpen ? '▾' : '▸' }}</span>
      </button>
      <div class="panel-resizer" @mousedown.prevent="startPanelResize"></div>
      <div class="zone-table-content" v-show="tableOpen" :style="{ maxHeight: `${panelHeight - 44}px` }">
        <table class="zone-table">
          <colgroup>
            <col :style="{ width: `${columnWidths[0]}px` }" />
            <col :style="{ width: `${columnWidths[1]}px` }" />
            <col :style="{ width: `${columnWidths[2]}px` }" />
            <col :style="{ width: `${columnWidths[3]}px` }" />
          </colgroup>
          <thead>
            <tr>
              <th>
                ID
                <span class="column-resizer" @mousedown.prevent="startColumnResize(0, $event)"></span>
              </th>
              <th>
                Название
                <span class="column-resizer" @mousedown.prevent="startColumnResize(1, $event)"></span>
              </th>
              <th>
                Центр
                <span class="column-resizer" @mousedown.prevent="startColumnResize(2, $event)"></span>
              </th>
              <th>Тип</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="zone in visibleZones"
              :key="zone.id"
              :data-zone-id="zone.id"
              :class="{ highlighted: zone.id === hoveredZoneId }"
              @mouseenter="hoverZone(zone.id)"
              @mouseleave="clearHoverZone"
              @contextmenu.prevent="onRowContextMenu($event, zone)"
            >
              <td>{{ zone.id }}</td>
              <td>{{ zone.name || '—' }}</td>
              <td>{{ formatCenter(zone) }}</td>
              <td>{{ zone.type }}</td>
            </tr>
            <tr v-if="!visibleZones.length">
              <td colspan="4" class="empty-row">Нет зон для отображения</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div
        v-if="rowContextMenuVisible"
        ref="rowContextMenu"
        class="row-context-menu"
        :style="{ left: `${rowContextMenuLeft}px`, top: `${rowContextMenuTop}px`, transform: rowContextMenuAbove ? 'translateY(-100%)' : 'translateY(0)' }"
      >
        <button type="button" @click="openZoneEditorFromRow">Редактор</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import Map from 'ol/Map'
import View from 'ol/View'
import TileLayer from 'ol/layer/Tile'
import VectorLayer from 'ol/layer/Vector'
import VectorSource from 'ol/source/Vector'
import OSM from 'ol/source/OSM'
import Feature from 'ol/Feature'
import Polygon from 'ol/geom/Polygon'
import { Style, Fill, Stroke } from 'ol/style'
import { fromLonLat, toLonLat } from 'ol/proj'
import { getDistance } from 'ol/sphere'
import { getZonesInRectangle, type Zone, type ZoneSummary } from '../api/geozones'

const emit = defineEmits<{
  (event: 'create-zone'): void
  (event: 'edit-zone', zoneId: number | string): void
}>()
const mapContainer = ref<HTMLDivElement | null>(null)
const map = ref<Map | null>(null)
const hoverText = ref('')
const vectorSource = new VectorSource()
const visibleZones = ref<ZoneSummary[]>([])
const tableOpen = ref(true)
const popupVisible = ref(false)
const popupFixed = ref(false)
const popupZoneId = ref<number | string | null>(null)
const popupLeft = ref(0)
const popupTop = ref(0)
const pointerOverZone = ref(false)
const popupHovered = ref(false)
const panelHeight = ref(260)
const panelResizeActive = ref(false)
const panelStartY = ref(0)
const panelHeightStart = ref(0)
const columnWidths = ref([70, 260, 280, 120])
const resizingColumn = ref<number | null>(null)
const columnResizeStartX = ref(0)
const columnResizeStartWidth = ref(0)
const rowContextMenuVisible = ref(false)
const rowContextMenuLeft = ref(0)
const rowContextMenuTop = ref(0)
const rowContextMenuAbove = ref(true)
const rowContextMenuRef = ref<HTMLDivElement | null>(null)
const contextZoneId = ref<number | string | null>(null)
const hoveredZoneId = ref<number | string | null>(null)
let zoneLayer: VectorLayer | null = null
let popupHideTimeout: number | null = null
let updateTimer: number | null = null
let lastRequestTime = 0
let pendingUpdateTimeout: number | null = null

const hoverStyle = new Style({
  fill: new Fill({ color: 'rgba(244, 67, 54, 0.18)' }),
  stroke: new Stroke({ color: '#d32f2f', width: 3 })
})

function createZoneFeature(zone: Zone) {
  if (zone.type !== 'polygon' || !Array.isArray(zone.coordinates)) {
    return null
  }

  const coords = zone.coordinates.map((coord) => fromLonLat([coord[1], coord[0]]))
  const polygon = new Polygon([coords])
  const feature = new Feature({ geometry: polygon })
  feature.set('zoneData', zone)
  return feature
}

function createMap() {
  zoneLayer = new VectorLayer({
    source: vectorSource,
    style: new Style({
      fill: new Fill({ color: 'rgba(33, 150, 243, 0.16)' }),
      stroke: new Stroke({ color: '#1976d2', width: 2 })
    })
  })

  map.value = new Map({
    target: mapContainer.value as HTMLDivElement,
    layers: [
      new TileLayer({ source: new OSM() }),
      zoneLayer
    ],
    view: new View({
      center: fromLonLat([30.437888132963106, 59.962961568076395]),
      zoom: 15,
      minZoom: 3,
      maxZoom: 22
    }),
    controls: []
  })

  map.value.on('pointermove', handlePointerMove)
  map.value.getViewport().addEventListener('contextmenu', handleContextMenu)
  map.value.getView().on('change:center', debounceUpdate)
  map.value.getView().on('change:resolution', debounceUpdate)
  map.value.on('moveend', updateZones)
  updateZones()
}

function debounceUpdate() {
  if (updateTimer) {
    clearTimeout(updateTimer)
  }
  updateTimer = window.setTimeout(() => {
    updateZones()
    updateTimer = null
  }, 300)
}

async function doUpdateZones() {
  if (!map.value) return
  const view = map.value.getView()
  const size = map.value.getSize()
  if (!size) return

  const extent = view.calculateExtent(size)
  const topLeft = toLonLat([extent[0], extent[3]])
  const bottomRight = toLonLat([extent[2], extent[1]])
  const diagonal = getDistance(topLeft, bottomRight)

  if (diagonal > 5000) {
    vectorSource.clear()
    visibleZones.value = []
    clearHoverZone()
    return
  }

  try {
    const zones = await getZonesInRectangle(
      topLeft[1],
      topLeft[0],
      bottomRight[1],
      bottomRight[0]
    )
    renderZones(zones)
  } catch (error) {
    console.warn('Ошибка запроса зон:', error)
    vectorSource.clear()
  }
}

function updateZones() {
  if (!map.value) return
  const now = Date.now()
  const elapsed = now - lastRequestTime

  if (elapsed >= 2000) {
    lastRequestTime = now
    doUpdateZones()
    return
  }

  if (pendingUpdateTimeout) {
    clearTimeout(pendingUpdateTimeout)
  }

  pendingUpdateTimeout = window.setTimeout(() => {
    lastRequestTime = Date.now()
    doUpdateZones()
    pendingUpdateTimeout = null
  }, 2000 - elapsed)
}

function renderZones(zones: unknown) {
  vectorSource.clear()
  visibleZones.value = []
  if (!Array.isArray(zones)) return

  const list: ZoneSummary[] = []
  zones.forEach((zone) => {
    const feature = createZoneFeature(zone as Zone)
    if (feature) {
      vectorSource.addFeature(feature)
      list.push(zone as ZoneSummary)
    }
  })

  visibleZones.value = list
  updateHoverStyle()
}

function updateHoverStyle() {
  vectorSource.getFeatures().forEach((feature) => {
    const zone = feature.get('zoneData') as Zone
    const isHovered = zone?.id != null && zone.id === hoveredZoneId.value
    feature.setStyle(isHovered ? hoverStyle : undefined)
  })
  zoneLayer?.changed()
}

function scrollZoneRowIntoView(zoneId: number | string | null) {
  if (zoneId == null) return
  const selector = `tbody tr[data-zone-id=\"${zoneId}\"]`
  const row = document.querySelector<HTMLTableRowElement>(selector)
  if (!row) return
  row.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

function hoverZone(zoneId: number | string | null, scroll = false) {
  const wasHovered = hoveredZoneId.value === zoneId
  hoveredZoneId.value = zoneId
  updateHoverStyle()
  if (scroll && zoneId != null && !wasHovered) {
    scrollZoneRowIntoView(zoneId)
  }
}

function cancelPopupHide() {
  if (popupHideTimeout) {
    clearTimeout(popupHideTimeout)
    popupHideTimeout = null
  }
}

function schedulePopupHide() {
  cancelPopupHide()
  popupHideTimeout = window.setTimeout(() => {
    if (!pointerOverZone.value && !popupHovered.value) {
      popupVisible.value = false
      popupFixed.value = false
      popupZoneId.value = null
      hoveredZoneId.value = null
      updateHoverStyle()
    }
    popupHideTimeout = null
  }, 200)
}

function clearHoverZone() {
  if (pointerOverZone.value || popupHovered.value) {
    return
  }
  hoveredZoneId.value = null
  updateHoverStyle()
}

function getZoneCenter(zone: ZoneSummary) {
  if (typeof zone.lat === 'number' && typeof zone.lon === 'number') {
    return [zone.lat, zone.lon] as [number, number]
  }

  if (Array.isArray(zone.coordinates) && zone.coordinates.length) {
    const total = zone.coordinates.reduce(
      (acc, coord) => [acc[0] + coord[0], acc[1] + coord[1]] as [number, number],
      [0, 0] as [number, number]
    )
    return [total[0] / zone.coordinates.length, total[1] / zone.coordinates.length]
  }

  return [0, 0] as [number, number]
}

function formatCenter(zone: ZoneSummary) {
  if (!Array.isArray(zone.coordinates) || !zone.coordinates.length) {
    return '—'
  }

  const [lat, lon] = getZoneCenter(zone)
  return `${lat.toString()}, ${lon.toString()}`
}

async function onRowContextMenu(event: MouseEvent, zone: ZoneSummary) {
  event.preventDefault()
  rowContextMenuVisible.value = true
  rowContextMenuLeft.value = event.clientX
  rowContextMenuTop.value = event.clientY
  rowContextMenuAbove.value = true
  contextZoneId.value = zone.id
  await nextTick()
  requestAnimationFrame(adjustRowContextMenuPosition)
}

function adjustRowContextMenuPosition() {
  if (!rowContextMenuRef.value) return
  const menuRect = rowContextMenuRef.value.getBoundingClientRect()
  let above = true
  let left = rowContextMenuLeft.value

  if (rowContextMenuTop.value < menuRect.height + 8) {
    above = false
  }

  if (!above && rowContextMenuTop.value + menuRect.height > window.innerHeight) {
    above = rowContextMenuTop.value >= menuRect.height + 8
  }

  if (menuRect.width >= window.innerWidth) {
    left = 8
  } else if (left + menuRect.width > window.innerWidth) {
    left = Math.max(8, window.innerWidth - menuRect.width - 8)
  }

  rowContextMenuAbove.value = above
  rowContextMenuLeft.value = left
}

function onPopupMouseEnter() {
  popupHovered.value = true
  cancelPopupHide()
}

function onPopupMouseLeave() {
  popupHovered.value = false
  schedulePopupHide()
}

watch(tableOpen, (open) => {
  if (open) {
    popupVisible.value = false
    if (hoveredZoneId.value != null) {
      scrollZoneRowIntoView(hoveredZoneId.value)
    }
  }
})

function startPanelResize(event: MouseEvent) {
  panelResizeActive.value = true
  panelStartY.value = event.clientY
  panelHeightStart.value = panelHeight.value
  window.addEventListener('mousemove', handlePanelResize)
  window.addEventListener('mouseup', stopPanelResize)
}

function handlePanelResize(event: MouseEvent) {
  if (!panelResizeActive.value) return
  const delta = panelStartY.value - event.clientY
  const nextHeight = panelHeightStart.value + delta
  panelHeight.value = Math.min(Math.max(nextHeight, 120), 650)
}

function stopPanelResize() {
  panelResizeActive.value = false
  window.removeEventListener('mousemove', handlePanelResize)
  window.removeEventListener('mouseup', stopPanelResize)
}

function startColumnResize(index: number, event: MouseEvent) {
  resizingColumn.value = index
  columnResizeStartX.value = event.clientX
  columnResizeStartWidth.value = columnWidths.value[index]
  window.addEventListener('mousemove', handleColumnResize)
  window.addEventListener('mouseup', stopColumnResize)
}

function handleColumnResize(event: MouseEvent) {
  if (resizingColumn.value === null) return
  const delta = event.clientX - columnResizeStartX.value
  const nextWidth = Math.max(40, columnResizeStartWidth.value + delta)
  columnWidths.value[resizingColumn.value] = nextWidth
}

function stopColumnResize() {
  resizingColumn.value = null
  window.removeEventListener('mousemove', handleColumnResize)
  window.removeEventListener('mouseup', stopColumnResize)
}

function openZoneEditorFromRow() {
  if (contextZoneId.value != null) {
    emit('edit-zone', contextZoneId.value)
  }
  rowContextMenuVisible.value = false
  contextZoneId.value = null
}

function hideRowContextMenu() {
  rowContextMenuVisible.value = false
  contextZoneId.value = null
}

function handlePointerMove(event: any) {
  if (!map.value || event.dragging) {
    hoverText.value = ''
    return
  }

  const pixel = map.value.getEventPixel(event.originalEvent)
  const feature = map.value.forEachFeatureAtPixel(pixel, (f) => f)
  if (feature) {
    const zoneData = feature.get('zoneData')
    const currentZoneId = zoneData?.id != null ? (zoneData.id as number | string) : null
    if (currentZoneId != null) {
      pointerOverZone.value = true
      hoverZone(currentZoneId, true)
      hoverText.value = JSON.stringify(zoneData, null, 2)
    }
    cancelPopupHide()
    map.value.getTargetElement().style.cursor = 'pointer'
    if (!tableOpen.value) {
      if (!popupVisible.value) {
        const rect = mapContainer.value?.getBoundingClientRect()
        if (rect) {
          popupLeft.value = event.originalEvent.clientX - rect.left + 12
          popupTop.value = event.originalEvent.clientY - rect.top + 12
        }
        popupVisible.value = true
        popupFixed.value = true
        popupZoneId.value = currentZoneId
      } else if (!popupHovered.value && currentZoneId != null && currentZoneId !== popupZoneId.value) {
        const rect = mapContainer.value?.getBoundingClientRect()
        if (rect) {
          popupLeft.value = event.originalEvent.clientX - rect.left + 12
          popupTop.value = event.originalEvent.clientY - rect.top + 12
        }
        popupZoneId.value = currentZoneId
      }
    } else {
      popupVisible.value = false
      popupFixed.value = false
      popupZoneId.value = null
    }
  } else {
    pointerOverZone.value = false
    map.value.getTargetElement().style.cursor = ''
    if (popupHovered.value) {
      cancelPopupHide()
    } else {
      schedulePopupHide()
    }
  }
}

function handleContextMenu(event: MouseEvent) {
  event.preventDefault()
  if (!map.value) return

  const pixel = map.value.getEventPixel(event)
  const feature = map.value.forEachFeatureAtPixel(pixel, (f) => f)
  const zoneData = feature?.get('zoneData')
  if (zoneData?.id != null) {
    emit('edit-zone', zoneData.id as number | string)
  }
}

onMounted(() => {
  createMap()
  window.addEventListener('click', hideRowContextMenu)
})

onBeforeUnmount(() => {
  if (!map.value) return
  map.value.getViewport().removeEventListener('contextmenu', handleContextMenu)
  window.removeEventListener('click', hideRowContextMenu)
  stopPanelResize()
  stopColumnResize()
  map.value.setTarget(undefined)
  map.value = null
})
</script>

<style scoped>
.zone-list-page {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.zone-page-header {
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  background: #fff;
}

.zone-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}

.primary-button {
  border: none;
  border-radius: 10px;
  padding: 6px 12px;
  background: #1b5e20;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
  font-size: 0.95rem;
}

.hint {
  color: #4f4f4f;
  font-size: 0.85rem;
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

.zone-table-panel {
  background: #fff;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
}

.panel-toggle {
  width: 100%;
  border: none;
  background: #f1f8f3;
  color: #1b5e20;
  text-align: left;
  padding: 10px 12px;
  font-weight: 700;
  cursor: pointer;
}

.zone-table-content {
  overflow: auto;
}

.panel-resizer {
  height: 6px;
  background: rgba(0, 0, 0, 0.08);
  cursor: ns-resize;
}

.zone-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}

.zone-table th,
.zone-table td {
  padding: 8px 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.08);
  text-align: left;
  font-size: 0.92rem;
  white-space: normal;
  word-break: break-word;
}

.zone-table th {
  position: relative;
}

.column-resizer {
  position: absolute;
  top: 0;
  right: 0;
  width: 10px;
  height: 100%;
  cursor: col-resize;
  user-select: none;
}

.zone-table th {
  background: #f8faf8;
  color: #2e7d32;
  font-weight: 700;
}

.zone-table tr:hover,
.zone-table tr.highlighted {
  background: rgba(33, 150, 243, 0.18);
}

.empty-row {
  padding: 16px 10px;
  color: #757575;
}

.row-context-menu {
  position: fixed;
  z-index: 20;
  padding: 8px;
  border-radius: 10px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
  max-width: calc(100vw - 16px);
  max-height: calc(100vh - 16px);
  overflow: auto;
}

.row-context-menu button {
  border: none;
  background: #1b5e20;
  color: #fff;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
}

.hover-popup {
  position: absolute;
  left: 16px;
  top: 16px;
  z-index: 10;
  min-width: 220px;
  max-width: 360px;
  max-height: 260px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  user-select: text;
  padding: 12px;
  background: rgba(255, 255, 255, 0.96);
  pointer-events: auto;
  border-radius: 12px;
  background: rgba(33, 33, 33, 0.88);
  color: #fff;
  font-size: 0.8rem;
  white-space: pre-wrap;
}
</style>
