export type StayStrengthLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface GeoBounds {
  minLatitude: number
  maxLatitude: number
  minLongitude: number
  maxLongitude: number
}

export interface StayStrengthRegion {
  id: number
  regionCode: string
  regionName: string
  rawStayStrength: number
  normalizedStayStrength: number
  level: StayStrengthLevel
  levelLabel: string
  interpretation: string
  latitude: number | null
  longitude: number | null
  referenceDate: string
  updatedAt: string
}

export interface LatestStayStrengthResponse {
  referenceDate: string | null
  count: number
  stayStrengths: StayStrengthRegion[]
}

export interface ApiResponse<T> {
  success: boolean
  data: T | null
  message: string | null
}
