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
  return request<S2Cell[]>('/polygon/s2', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ coordinates, maxLevel })
  })
}
