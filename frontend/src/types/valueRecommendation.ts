import type { ApiResponse } from './stayStrength'

export interface ValueRecommendation {
  rank: number
  regionCode: string
  regionName: string
  rawServiceDemand: number
  normalizedServiceDemand: number
  rawStayStrength: number
  normalizedStayStrength: number
  recommendationScore: number
  recommendationScorePercent: number
  interpretation: string
  latitude: number | null
  longitude: number | null
}

export interface ValueRecommendationResponse {
  referenceDate: string | null
  count: number
  formula: string
  recommendations: ValueRecommendation[]
}

export type ValueRecommendationApiResponse = ApiResponse<ValueRecommendationResponse>
