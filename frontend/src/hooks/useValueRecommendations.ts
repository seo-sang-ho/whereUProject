import { useQuery } from '@tanstack/react-query'
import { fetchValueRecommendations } from '../api/valueRecommendationApi'

const DEFAULT_RECOMMENDATION_LIMIT = 10

export function useValueRecommendations(enabled: boolean) {
  return useQuery({
    queryKey: ['recommendations', 'value', DEFAULT_RECOMMENDATION_LIMIT],
    queryFn: () => fetchValueRecommendations(DEFAULT_RECOMMENDATION_LIMIT),
    enabled,
    staleTime: 5 * 60_000,
    retry: 1,
  })
}
