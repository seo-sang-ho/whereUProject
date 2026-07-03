import { apiClient } from './client'
import type {
  ValueRecommendationApiResponse,
  ValueRecommendationResponse,
} from '../types/valueRecommendation'

export async function fetchValueRecommendations(
  limit: number,
): Promise<ValueRecommendationResponse> {
  const response = await apiClient.get<ValueRecommendationApiResponse>(
    '/recommendations/value',
    { params: { limit } },
  )

  if (!response.data.success || !response.data.data) {
    throw new Error(response.data.message || '추천 결과를 불러오지 못했습니다.')
  }

  return response.data.data
}
