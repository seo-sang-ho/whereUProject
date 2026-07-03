import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { fetchLatestStayStrengthsInBounds } from '../api/stayStrengthApi'
import type { GeoBounds } from '../types/stayStrength'

function roundBounds(bounds: GeoBounds): GeoBounds {
  return {
    minLatitude: Number(bounds.minLatitude.toFixed(4)),
    maxLatitude: Number(bounds.maxLatitude.toFixed(4)),
    minLongitude: Number(bounds.minLongitude.toFixed(4)),
    maxLongitude: Number(bounds.maxLongitude.toFixed(4)),
  }
}

export function useLatestStayStrengths(bounds: GeoBounds | null) {
  const queryBounds = bounds ? roundBounds(bounds) : null

  return useQuery({
    queryKey: ['stay-strengths', 'latest', 'bounds', queryBounds],
    queryFn: () => fetchLatestStayStrengthsInBounds(queryBounds!),
    enabled: queryBounds !== null,
    placeholderData: keepPreviousData,
    staleTime: 60_000,
    retry: 1,
  })
}
