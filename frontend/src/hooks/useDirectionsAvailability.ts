import { useQuery } from '@tanstack/react-query'
import { fetchDirectionsAvailability } from '../api/directionsApi'

export function useDirectionsAvailability(enabled: boolean) {
  return useQuery({
    queryKey: ['directions', 'availability'],
    queryFn: fetchDirectionsAvailability,
    enabled,
    staleTime: 60_000,
    retry: 1,
  })
}
