import { apiClient } from './client'
import type {
  ApiResponse,
  GeoBounds,
  LatestStayStrengthResponse,
} from '../types/stayStrength'

export async function fetchLatestStayStrengthsInBounds(
  bounds: GeoBounds,
): Promise<LatestStayStrengthResponse> {
  const response = await apiClient.get<ApiResponse<LatestStayStrengthResponse>>(
    '/stay-strengths/latest/bounds',
    { params: bounds },
  )

  if (!response.data.success || !response.data.data) {
    throw new Error(response.data.message || '체류강도 데이터를 불러오지 못했습니다.')
  }

  return response.data.data
}
