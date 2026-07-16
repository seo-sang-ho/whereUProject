import type { ApiResponse } from './stayStrength'
import type { RecommendedTourismContent } from './tourismContent'

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
  tourismContents: RecommendedTourismContent[]
}

export interface ValueRecommendationResponse {
  referenceDate: string | null
  count: number
  formula: string
  recommendations: ValueRecommendation[]
}

export type ValueRecommendationApiResponse = ApiResponse<ValueRecommendationResponse>
