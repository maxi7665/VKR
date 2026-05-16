export type Coordinate = [number, number]

export interface Zone {
  id: number | string | null
  name: string
  type: string
  coordinates: Coordinate[]
  isActive: boolean
  s2Key: number
  lat: number
  lon: number
  [key: string]: unknown
}

export interface ZoneSummary {
  id: number | string
  name?: string
  type: string
  coordinates: Coordinate[]
  lat?: number
  lon?: number
  [key: string]: unknown
}

export interface S2Cell {
  s2CellId: string
  level: number
  polygon: Coordinate[]
  [key: string]: unknown
}

const API_BASE = '/api/geozones'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
  if (!response.ok) {
    const errorText = await response.text().catch(() => response.statusText)
    throw new Error(errorText || `HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function getZonesInRectangle(
  topLeftLat: number,
  topLeftLon: number,
  bottomRightLat: number,
  bottomRightLon: number
): Promise<ZoneSummary[]> {
  return request<ZoneSummary[]>('/rectangle', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      topLeftLat,
      topLeftLon,
      bottomRightLat,
      bottomRightLon
    })
  })
}

export async function getZoneById(zoneId: number | string): Promise<Zone> {
  return request<Zone>(`/${zoneId}`)
}

export interface ZoneCreatePayload {
  name: string
  type: string
  coordinates: Coordinate[]
  isActive: boolean
  s2Key: number
  lat: number
  lon: number
}

export async function createZone(payload: ZoneCreatePayload): Promise<{ id: number | string }>
export async function createZone(payload: ZoneCreatePayload): Promise<{ id: number | string }> {
  return request<{ id: number | string }>('/', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
}

export async function getS2Cells(coordinates: Coordinate[], maxLevel: number): Promise<S2Cell[]> {
  const response = await fetch(`${API_BASE}/polygon/s2`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ coordinates, maxLevel })
  })
  if (!response.ok) {
    const errorText = await response.text().catch(() => response.statusText)
    throw new Error(errorText || `HTTP ${response.status}`)
  }
  const text = await response.text()
  // Convert s2CellId numbers to strings to preserve full int64 precision
  const transformed = text.replace(/"s2CellId"\s*:\s*(\d+)/g, '"s2CellId":"$1"')
  return JSON.parse(transformed) as S2Cell[]
}
