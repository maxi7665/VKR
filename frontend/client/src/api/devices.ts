export interface DeviceDto {
  id: number
  name: string
  registrationNumber: string
  deviceId: string
  typeId: number
  departmentId: number
  createdAt: string
}

const API_BASE = '/api/devices'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
  if (!response.ok) {
    const errorText = await response.text().catch(() => response.statusText)
    throw new Error(errorText || `HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function getDevices(): Promise<DeviceDto[]> {
  return request<DeviceDto[]>('')
}

export async function getDeviceById(id: number | string): Promise<DeviceDto> {
  return request<DeviceDto>(`/${id}`)
}

export interface DeviceCreatePayload {
  name: string
  registrationNumber: string
  deviceId: string
  typeId: number
  departmentId: number
}

export async function createDevice(payload: DeviceCreatePayload): Promise<DeviceDto> {
  return request<DeviceDto>('', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
}

export async function updateDevice(id: number | string, payload: Partial<DeviceCreatePayload>): Promise<DeviceDto> {
  return request<DeviceDto>(`/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  })
}

export async function deleteDevice(id: number | string): Promise<void> {
  await request(`/${id}`, {
    method: 'DELETE'
  })
}