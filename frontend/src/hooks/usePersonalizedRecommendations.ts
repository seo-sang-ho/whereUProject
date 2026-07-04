import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchPersonalizedRecommendations } from '../api/personalizedRecommendationApi'
import type { TourismTheme } from '../types/personalizedRecommendation'

const DEFAULT_RECOMMENDATION_LIMIT = 10

export function usePersonalizedRecommendations(
  themes: TourismTheme[],
  enabled: boolean,
) {
  const stableThemes = [...themes].sort()

  return useQuery({
    queryKey: ['recommendations', 'personalized', stableThemes, DEFAULT_RECOMMENDATION_LIMIT],
    queryFn: () => fetchPersonalizedRecommendations(stableThemes, DEFAULT_RECOMMENDATION_LIMIT),
    enabled: enabled && stableThemes.length > 0,
    placeholderData: keepPreviousData,
    staleTime: 5 * 60_000,
    retry: 1,
  })
}
