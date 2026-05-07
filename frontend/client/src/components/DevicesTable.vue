<template>
  <div class="devices-table-container">
    <div class="devices-header">
      <div class="header-top">
        <div>
          <h2 class="devices-title">Устройства</h2>
          <div class="devices-subtitle">Список всех зарегистрированных устройств</div>
        </div>
        <button
          @click="showCreateDialog = true"
          class="add-device-button simple-button"
        >
          Добавить устройство
        </button>
      </div>
    </div>

    <DeviceCreateDialog
      v-model="showCreateDialog"
      @created="handleDeviceCreated"
    />

    <v-card class="devices-card" elevation="2">
      <v-card-text>
        <!-- Loading state -->
        <div v-if="loading" class="loading-state">
          <v-progress-linear
            color="primary"
            indeterminate
            height="4"
          ></v-progress-linear>
          <div class="loading-text">Загрузка устройств...</div>
        </div>

        <!-- Error state -->
        <v-alert
          v-else-if="error"
          type="error"
          variant="tonal"
          class="error-alert"
        >
          Ошибка при загрузке устройств: {{ error }}
          <template v-slot:append>
            <v-btn
              variant="text"
              color="error"
              @click="fetchDevices"
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
          :items="devices"
          :items-per-page="10"
          :sort-by="[{ key: 'id', order: 'asc' }]"
          class="devices-data-table"
          hover
        >
          <template v-slot:item.id="{ item }">
            <span class="device-id">{{ item.id }}</span>
          </template>

          <template v-slot:item.name="{ item }">
            <div class="device-name">{{ item.name }}</div>
          </template>

          <template v-slot:item.registrationNumber="{ item }">
            <v-chip
              color="primary"
              variant="outlined"
              size="small"
            >
              {{ item.registrationNumber }}
            </v-chip>
          </template>

          <template v-slot:item.deviceId="{ item }">
            <code class="device-id-code">{{ item.deviceId }}</code>
          </template>

          <template v-slot:item.typeId="{ item }">
            <v-chip
              :color="getTypeColor(item.typeId)"
              variant="flat"
              size="small"
            >
              Тип {{ item.typeId }}
            </v-chip>
          </template>

          <template v-slot:item.departmentId="{ item }">
            <span class="department-id">Отдел {{ item.departmentId }}</span>
          </template>

          <template v-slot:item.createdAt="{ item }">
            {{ formatDate(item.createdAt) }}
          </template>

          <template v-slot:bottom>
            <div class="table-footer">
              <div class="devices-count">
                Всего устройств: {{ devices.length }}
              </div>
            </div>
          </template>
        </v-data-table>

        <!-- Empty state -->
        <div v-if="!loading && !error && devices.length === 0" class="empty-state">
          <div class="empty-icon">📱</div>
          <div class="empty-text">Устройства не найдены</div>
          <div class="empty-subtext">Добавьте первое устройство через API</div>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getDevices, type DeviceDto } from '../api/devices'
import DeviceCreateDialog from './DeviceCreateDialog.vue'

const devices = ref<DeviceDto[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const showCreateDialog = ref(false)

const headers = [
  { title: 'ID', key: 'id', width: '80px' },
  { title: 'Название', key: 'name' },
  { title: 'Рег. номер', key: 'registrationNumber' },
  { title: 'ID устройства', key: 'deviceId' },
  { title: 'Тип', key: 'typeId', width: '100px' },
  { title: 'Отдел', key: 'departmentId', width: '100px' },
  { title: 'Дата создания', key: 'createdAt', width: '150px' },
]

async function fetchDevices() {
  loading.value = true
  error.value = null
  try {
    devices.value = await getDevices()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Неизвестная ошибка'
    console.error('Failed to fetch devices:', err)
  } finally {
    loading.value = false
  }
}

function getTypeColor(typeId: number): string {
  const colors = ['primary', 'secondary', 'success', 'warning', 'info', 'error']
  return colors[typeId % colors.length] || 'primary'
}

function formatDate(dateString: string): string {
  try {
    const date = new Date(dateString)
    return date.toLocaleDateString('ru-RU', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    })
  } catch {
    return dateString
  }
}

function handleDeviceCreated() {
  fetchDevices()
}

onMounted(() => {
  fetchDevices()
})
</script>

<style scoped>
.devices-table-container {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.devices-header {
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
  
  .add-device-button {
    align-self: flex-start;
  }
}

.devices-title {
  font-size: 1.8rem;
  font-weight: 800;
  color: #1b5e20;
  margin: 0 0 8px 0;
}

.devices-subtitle {
  font-size: 0.95rem;
  color: rgba(0, 0, 0, 0.6);
}

.add-device-button {
  margin-top: 4px;
}

.add-device-button.simple-button {
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
}

.add-device-button.simple-button:hover {
  background: #1b5e20;
}

.add-device-button.simple-button:active {
  transform: translateY(1px);
}

.devices-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #ffffff;
  border-radius: 12px;
}

.devices-card :deep(.v-card-text) {
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

.devices-data-table {
  flex: 1;
  overflow: auto;
}

.devices-data-table :deep(.v-data-table__th) {
  background-color: #f1f8f3;
  font-weight: 700;
  color: #1b5e20;
  padding: 12px 16px;
}

.devices-data-table :deep(.v-data-table__td) {
  padding: 12px 16px;
}

.devices-data-table :deep(.v-data-table__tr:hover) {
  background-color: #f8fdf9;
}

.device-id {
  font-family: 'Roboto Mono', monospace;
  font-weight: 600;
  color: #2e7d32;
}

.device-name {
  font-weight: 500;
}

.device-id-code {
  font-family: 'Roboto Mono', monospace;
  font-size: 0.85rem;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  color: #555;
}

.department-id {
  color: #666;
  font-weight: 500;
}

.table-footer {
  padding: 16px;
  border-top: 1px solid rgba(0, 0, 0, 0.08);
  background-color: #f9f9f9;
}

.devices-count {
  font-weight: 600;
  color: #2e7d32;
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
}
</style>