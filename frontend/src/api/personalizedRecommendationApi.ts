import { apiClient } from './client'
import type {
  PersonalizedRecommendationApiResponse,
  PersonalizedRecommendationResponse,
  TourismTheme,
} from '../types/personalizedRecommendation'

export async function fetchPersonalizedRecommendations(
  themes: TourismTheme[],
  limit: number,
): Promise<PersonalizedRecommendationResponse> {
  const response = await apiClient.get<PersonalizedRecommendationApiResponse>(
    '/recommendations/personalized',
    { params: { themes: themes.join(','), limit } },
  )

  if (!response.data.success || !response.data.data) {
    throw new Error(response.data.message || '맞춤 추천 결과를 불러오지 못했습니다.')
  }

  return response.data.data
}
