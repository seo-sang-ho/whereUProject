import { apiClient } from './client'
import type { ApiResponse } from '../types/stayStrength'
import type {
  DirectionsAvailability,
  DirectionsEstimate,
  DirectionsEstimateRequest,
} from '../types/directions'

export async function fetchDirectionsAvailability(): Promise<DirectionsAvailability> {
  const response = await apiClient.get<ApiResponse<DirectionsAvailability>>(
    '/directions/availability',
  )

  if (!response.data.success || !response.data.data) {
    throw new Error(response.data.message || '길찾기 가능 여부를 확인하지 못했습니다.')
  }

  return response.data.data
}

export async function fetchDrivingEstimate(
  request: DirectionsEstimateRequest,
): Promise<DirectionsEstimate> {
  const response = await apiClient.post<ApiResponse<DirectionsEstimate>>(
    '/directions/estimate',
    request,
  )

  if (!response.data.success || !response.data.data) {
    throw new Error(response.data.message || '자동차 예상 시간을 불러오지 못했습니다.')
  }

  return response.data.data
}
