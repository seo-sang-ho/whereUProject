export type DirectionsStatus = 'AVAILABLE' | 'NAVER_MAP_REQUIRED'

export type DirectionsFallbackReason =
  | 'MONTHLY_LIMIT_REACHED'
  | 'DESTINATION_COORDINATES_MISSING'
  | 'NAVER_API_UNAVAILABLE'
  | 'ROUTE_NOT_FOUND'

export interface Coordinates {
  latitude: number
  longitude: number
}

export interface DirectionsAvailability {
  status: DirectionsStatus
}

export interface DirectionsEstimateRequest {
  originLatitude: number
  originLongitude: number
  destinationContentId: string
}

export interface DirectionsEstimate {
  status: DirectionsStatus
  destinationContentId: string
  destinationName: string
  travelTimeMinutes: number | null
  distanceMeters: number | null
  tollFare: number | null
  calculatedAt: string | null
  fallbackReason: DirectionsFallbackReason | null
}
