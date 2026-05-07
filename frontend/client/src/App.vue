<template>
  <v-app>
    <v-app-bar color="primary" dark elevated app class="top-bar">
      <div class="brand">
        <div class="brand-title">LYNCEUS</div>
      </div>
    </v-app-bar>

    <v-main class="main-content">
      <div class="app-shell">
        <aside class="sidebar">
          <nav class="menu-list">
            <button
              :class="['menu-item', { active: activeSection === 'zones' }]"
              @click="switchSection('zones')"
            >
              <div class="menu-item-icon">📍</div>
              <div>
                <div class="menu-item-title">Зоны</div>
              </div>
            </button>
            <button
              :class="['menu-item', { active: activeSection === 'devices' }]"
              @click="switchSection('devices')"
            >
              <div class="menu-item-icon">📱</div>
              <div>
                <div class="menu-item-title">Устройства</div>
              </div>
            </button>
            <button
              :class="['menu-item', { active: activeSection === 'navigation' }]"
              @click="switchSection('navigation')"
            >
              <div class="menu-item-icon">🗺️</div>
              <div>
                <div class="menu-item-title">Поиск навигации</div>
              </div>
            </button>
            <button
              :class="['menu-item', { active: activeSection === 'vehicles' }]"
              @click="switchSection('vehicles')"
            >
              <div class="menu-item-icon">🚗</div>
              <div>
                <div class="menu-item-title">ТС на карте</div>
              </div>
            </button>
            <button
              :class="['menu-item', { active: activeSection === 'events' }]"
              @click="switchSection('events')"
            >
              <div class="menu-item-icon">📊</div>
              <div>
                <div class="menu-item-title">События</div>
              </div>
            </button>
          </nav>
        </aside>

        <section class="content-panel">
          <!-- Zones Section -->
          <div v-if="activeSection === 'zones'" class="zones-section">
            <div class="tabs">
              <button :class="['tab-button', { active: activeTab === 'zones' }]" @click="switchTab('zones')">Зоны</button>
              <button :class="['tab-button', { active: activeTab === 'editor' }]" @click="switchTab('editor')" :disabled="!editorOpen">Редактор</button>
            </div>
            <div class="tab-content">
              <ZoneMap
                v-if="activeTab === 'zones'"
                @edit-zone="openEditor"
                @create-zone="openCreator"
              ></ZoneMap>
              <ZoneEditor
                v-if="activeTab === 'editor'"
                :key="editorKey"
                :zoneId="editorZoneId"
                :isNew="isNewZone"
                @close="closeEditor"
                @saved="handleSavedZone"
              ></ZoneEditor>
            </div>
          </div>

          <!-- Devices Section -->
          <div v-else-if="activeSection === 'devices'" class="devices-section">
            <DevicesTable />
          </div>

          <!-- Navigation Search Section -->
          <div v-else-if="activeSection === 'navigation'" class="navigation-section">
            <NavigationSearch />
          </div>

          <!-- Vehicles Map Section -->
          <div v-else-if="activeSection === 'vehicles'" class="vehicles-section">
            <VehiclesMap />
          </div>

          <!-- Events Section -->
          <div v-else-if="activeSection === 'events'" class="events-section">
            <EventsTable />
          </div>
        </section>
      </div>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import ZoneEditor from './components/ZoneEditor.vue'
import ZoneMap from './components/ZoneMap.vue'
import DevicesTable from './components/DevicesTable.vue'
import NavigationSearch from './components/NavigationSearch.vue'
import VehiclesMap from './components/VehiclesMap.vue'
import EventsTable from './components/EventsTable.vue'

type SectionName = 'zones' | 'devices' | 'navigation' | 'vehicles' | 'events'
type TabName = 'zones' | 'editor'

const activeSection = ref<SectionName>('zones')
const activeTab = ref<TabName>('zones')
const editorZoneId = ref<number | string | null>(null)
const isNewZone = ref(false)
const editorKey = ref(0)

const editorOpen = computed(() => activeTab.value === 'editor' && (isNewZone.value || editorZoneId.value != null))

function switchSection(section: SectionName) {
  activeSection.value = section
  // Reset to zones tab when switching back to zones section
  if (section === 'zones') {
    activeTab.value = 'zones'
  }
}

function switchTab(tab: TabName) {
  if (tab === 'editor' && !editorOpen.value) {
    return
  }

  activeTab.value = tab
}

function openCreator() {
  editorZoneId.value = null
  isNewZone.value = true
  editorKey.value += 1
  activeTab.value = 'editor'
}

function openEditor(zoneId: number | string) {
  editorZoneId.value = zoneId
  isNewZone.value = false
  editorKey.value += 1
  activeTab.value = 'editor'
}

function closeEditor() {
  activeTab.value = 'zones'
}

function handleSavedZone(newId: number | string | null) {
  if (newId) {
    editorZoneId.value = newId
    isNewZone.value = false
  }
}
</script>

<style scoped>
.top-bar {
  padding-left: 24px;
  display: flex;
  align-items: center;
}

.brand {
  display: flex;
  align-items: center;
}

.brand-title {
  font-size: 1.6rem;
  font-weight: 800;
  letter-spacing: 0.04em;
}

.main-content {
  background: #e8f5e9;
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  height: auto;
}

.app-shell {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.sidebar {
  width: 220px;
  min-width: 220px;
  padding: 18px;
  background: linear-gradient(180deg, #1b5e20 0%, #2e7d32 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.menu-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  border-radius: 14px;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1),
              background 0.2s ease,
              border-color 0.2s ease,
              box-shadow 0.2s ease;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.menu-item:hover,
.menu-item.active {
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(255, 255, 255, 0.24);
  transform: translateY(-3px) scale(1.03);
  box-shadow: 0 6px 12px rgba(0, 0, 0, 0.1);
}

.menu-item-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.15);
  transition: background 0.2s ease, transform 0.2s ease;
}

.menu-item:hover .menu-item-icon,
.menu-item.active .menu-item-icon {
  background: rgba(255, 255, 255, 0.25);
  transform: scale(1.05);
}

.menu-item-title {
  font-size: 0.95rem;
  font-weight: 700;
}

.content-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
  background: #f1f8f3;
  overflow: hidden;
}

.zones-section,
.devices-section,
.navigation-section,
.vehicles-section {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.map-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  background: #ffffff;
  border-radius: 12px;
  margin: 20px;
  padding: 40px;
  text-align: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  border: 2px dashed #c8e6c9;
}

.map-placeholder h2 {
  color: #1b5e20;
  margin-bottom: 12px;
  font-size: 1.8rem;
}

.map-placeholder p {
  color: #4caf50;
  font-size: 1.1rem;
  max-width: 400px;
  line-height: 1.5;
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

.tab-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.tab-content {
  flex: 1;
  min-height: 0;
}
</style>