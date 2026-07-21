import { useMutation } from '@tanstack/react-query'
import { fetchDrivingEstimate } from '../api/directionsApi'

export function useDrivingEstimate() {
  return useMutation({ mutationFn: fetchDrivingEstimate })
}
