<template>
  <div class="events-table-container">
    <div class="events-header">
      <div class="header-top">
        <div>
          <h2 class="events-title">События посещения зон</h2>
          <div class="events-subtitle">События в реальном времени (последние 500)</div>
          <div class="connection-status">
            <span :class="['status-indicator', connectionStatusClass]"></span>
            {{ connectionStatusText }}
            <span v-if="events.length > 0" class="events-count-badge">
              {{ events.length }}/500
            </span>
          </div>
        </div>
        <button
          @click="toggleConnection"
          class="connection-button simple-button"
          :class="{ 'connected': isConnected, 'disconnected': !isConnected }"
        >
          {{ isConnected ? 'Отключить' : 'Подключить' }}
        </button>
      </div>
    </div>

    <v-card class="events-card" elevation="2">
      <v-card-text>
        <!-- Loading state -->
        <div v-if="loading" class="loading-state">
          <v-progress-linear
            color="primary"
            indeterminate
            height="4"
          ></v-progress-linear>
          <div class="loading-text">Подключение к WebSocket...</div>
        </div>

        <!-- Error state -->
        <v-alert
          v-else-if="error"
          type="error"
          variant="tonal"
          class="error-alert"
        >
          Ошибка WebSocket: {{ error }}
          <template v-slot:append>
            <v-btn
              variant="text"
              color="error"
              @click="connectWebSocket"
              size="small"
            >
              Повторить
            </v-btn>
          </template>
        </v-alert>

        <!-- Data table -->
        <v-data-table
          v-else
          :headers="headers"
          :items="events"
          :items-per-page="15"
          :sort-by="[{ key: 'zoneDateTime', order: 'desc' }]"
          class="events-data-table"
          hover
        >
          <template v-slot:item.inOut="{ item }">
            <v-chip
              :color="item.inOut === 'In' ? 'success' : 'error'"
              variant="flat"
              size="small"
            >
              {{ item.inOut === 'In' ? 'Вход' : 'Выход' }}
            </v-chip>
          </template>

          <template v-slot:item.deviceId="{ item }">
            <code class="device-id-code">{{ item.deviceId }}</code>
          </template>

          <template v-slot:item.vehicleId="{ item }">
            <span class="vehicle-id">ТС {{ item.vehicleId }}</span>
          </template>

          <template v-slot:item.zoneId="{ item }">
            <span class="zone-id">Зона {{ item.zoneId }}</span>
          </template>

          <template v-slot:item.zoneName="{ item }">
            <div class="zone-name">{{ item.zoneName }}</div>
          </template>

          <template v-slot:item.zoneDateTime="{ item }">
            {{ formatDateTime(item.zoneDateTime) }}
          </template>

          <template v-slot:bottom>
            <div class="table-footer">
              <div class="events-count">
                Событий: {{ events.length }} (максимум 500)
              </div>
              <div class="last-update">
                Последнее обновление: {{ lastUpdateTime }}
              </div>
            </div>
          </template>
        </v-data-table>

        <!-- Empty state -->
        <div v-if="!loading && !error && events.length === 0" class="empty-state">
          <div class="empty-icon">📊</div>
          <div class="empty-text">События не получены</div>
          <div class="empty-subtext">Ожидание данных от WebSocket соединения</div>
          <button @click="connectWebSocket" class="connect-button simple-button">
            Подключить вручную
          </button>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ZoneVisitsWebSocket, getZoneVisitsWebSocketUrl, type ZoneVisitEvent } from '../api/zone-visits'

const events = ref<ZoneVisitEvent[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const isConnected = ref(false)
const lastUpdateTime = ref<string>('—')
const webSocket = ref<ZoneVisitsWebSocket | null>(null)

const MAX_EVENTS = 500

const headers = [
  { title: 'Вход/Выход', key: 'inOut', width: '120px' },
  { title: 'ID устройства', key: 'deviceId', width: '120px' },
  { title: 'ID ТС', key: 'vehicleId', width: '100px' },
  { title: 'ID зоны', key: 'zoneId', width: '100px' },
  { title: 'Название зоны', key: 'zoneName' },
  { title: 'Дата и время', key: 'zoneDateTime', width: '180px' },
]

const connectionStatusText = computed(() => {
  if (!webSocket.value) return 'Не подключено'
  const state = webSocket.value.connectionState
  switch (state) {
    case 'CONNECTING': return 'Подключение...'
    case 'OPEN': return 'Подключено'
    case 'CLOSING': return 'Отключение...'
    case 'CLOSED': return 'Отключено'
    default: return state
  }
})

const connectionStatusClass = computed(() => {
  if (!webSocket.value) return 'disconnected'
  const state = webSocket.value.connectionState
  switch (state) {
    case 'CONNECTING': return 'connecting'
    case 'OPEN': return 'connected'
    case 'CLOSING': return 'disconnecting'
    case 'CLOSED': return 'disconnected'
    default: return 'disconnected'
  }
})

function formatDateTime(dateTimeString: string): string {
  try {
    const date = new Date(dateTimeString)
    return date.toLocaleDateString('ru-RU', {
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

function updateLastUpdateTime() {
  const now = new Date()
  lastUpdateTime.value = now.toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

function addEvent(event: ZoneVisitEvent) {
  // Add new event at the beginning of the array
  events.value.unshift(event)
  
  // Remove old events if we exceed the limit
  if (events.value.length > MAX_EVENTS) {
    events.value = events.value.slice(0, MAX_EVENTS)
  }
  
  updateLastUpdateTime()
}

function connectWebSocket() {
  if (webSocket.value && webSocket.value.isConnected) {
    return
  }

  loading.value = true
  error.value = null

  const url = getZoneVisitsWebSocketUrl()
  
  webSocket.value = new ZoneVisitsWebSocket(url, {
    onOpen: () => {
      console.log('ZoneVisits WebSocket connected')
      isConnected.value = true
      loading.value = false
      updateLastUpdateTime()
    },
    onClose: (event) => {
      console.log('ZoneVisits WebSocket closed:', event.code, event.reason)
      isConnected.value = false
      if (event.code !== 1000) {
        error.value = `Соединение закрыто: ${event.reason || 'Код ' + event.code}`
        loading.value = false
      }
    },
    onError: (errorEvent) => {
      console.error('ZoneVisits WebSocket error:', errorEvent)
      error.value = 'Ошибка WebSocket соединения'
      loading.value = false
      isConnected.value = false
    },
    onMessage: (message) => {
      addEvent(message)
    },
    onReconnect: (attempt) => {
      console.log(`Reconnection attempt ${attempt}`)
      loading.value = true
      error.value = null
    }
  })

  webSocket.value.connect()
}

function disconnectWebSocket() {
  if (webSocket.value) {
    webSocket.value.disconnect()
    webSocket.value = null
    isConnected.value = false
    loading.value = false
  }
}

function toggleConnection() {
  if (isConnected.value) {
    disconnectWebSocket()
  } else {
    connectWebSocket()
  }
}

onMounted(() => {
  // Auto-connect when component is mounted
  connectWebSocket()
})

onUnmounted(() => {
  // Clean up WebSocket when component is destroyed
  disconnectWebSocket()
})
</script>

<style scoped>
.events-table-container {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.events-header {
  margin-bottom: 24px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

@media (max-width: 768px) {
  .header-top {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  
  .connection-button {
    align-self: flex-start;
  }
}

.events-title {
  font-size: 1.8rem;
  font-weight: 800;
  color: #1b5e20;
  margin: 0 0 8px 0;
}

.events-subtitle {
  font-size: 0.95rem;
  color: rgba(0, 0, 0, 0.6);
  margin-bottom: 8px;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: rgba(0, 0, 0, 0.7);
}

.status-indicator {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.status-indicator.connected {
  background-color: #4caf50;
  box-shadow: 0 0 8px #4caf50;
}

.status-indicator.connecting {
  background-color: #ff9800;
  animation: pulse 1.5s infinite;
}

.status-indicator.disconnected,
.status-indicator.disconnecting {
  background-color: #f44336;
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

.events-count-badge {
  background: #e8f5e9;
  color: #1b5e20;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 600;
  margin-left: 8px;
}

.connection-button.simple-button {
  background: #2e7d32;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 10px 20px;
  font-weight: 500;
  font-size: 0.95rem;
  cursor: pointer;
  transition: background-color 0.2s;
  white-space: nowrap;
  margin-top: 4px;
}

.connection-button.simple-button:hover {
  background: #1b5e20;
}

.connection-button.simple-button.connected {
  background: #f44336;
}

.connection-button.simple-button.connected:hover {
  background: #d32f2f;
}

.connection-button.simple-button:active {
  transform: translateY(1px);
}

.events-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
  border-radius: 12px;
}

.events-card :deep(.v-card-text) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.loading-state {
  padding: 48px 24px;
  text-align: center;
}

.loading-text {
  margin-top: 16px;
  color: #2e7d32;
  font-weight: 500;
}

.error-alert {
  margin: 24px;
}

.events-data-table {
  flex: 1;
  overflow: auto;
}

.events-data-table :deep(.v-data-table__th) {
  background-color: #f1f8f3;
  font-weight: 700;
  color: #1b5e20;
  padding: 12px 16px;
}

.events-data-table :deep(.v-data-table__td) {
  padding: 12px 16px;
}

.events-data-table :deep(.v-data-table__tr:hover) {
  background-color: #f8fdf9;
}

.device-id-code {
  font-family: 'Roboto Mono', monospace;
  font-size: 0.85rem;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  color: #555;
}

.vehicle-id {
  font-weight: 600;
  color: #1976d2;
}

.zone-id {
  font-weight: 600;
  color: #7b1fa2;
}

.zone-name {
  font-weight: 500;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-footer {
  padding: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  background-color: #f9f9f9;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.events-count {
  font-weight: 600;
  color: #2e7d32;
}

.last-update {
  font-size: 0.9rem;
  color: rgba(0, 0, 0, 0.6);
}

.empty-state {
  padding: 64px 24px;
  text-align: center;
  color: rgba(0, 0, 0, 0.5);
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-text {
  font-size: 1.2rem;
  font-weight: 600;
  margin-bottom: 8px;
}

.empty-subtext {
  font-size: 0.9rem;
  margin-bottom: 20px;
}

.connect-button.simple-button {
  background: #2e7d32;
  color: white;
  border: none;
  border-radius: 6px;
  padding: 10px 20px;
  font-weight: 500;
  font-size: 0.95rem;
  cursor: pointer;
  transition: background-color 0.2s;
}

.connect-button.simple-button:hover {
  background: #1b5e20;
}
</style>