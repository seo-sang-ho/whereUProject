import type { ApiResponse } from './stayStrength'

export type TourismTheme =
  | 'NATURE'
  | 'CULTURE_HISTORY'
  | 'ACTIVITY'
  | 'FOOD'
  | 'SHOPPING'
  | 'HEALING_STAY'

export interface ThemeOption {
  theme: TourismTheme
  label: string
}

export interface ThemeScore {
  theme: TourismTheme
  themeLabel: string
  normalizedScore: number
  indicatorCount: number
}

export interface PersonalizedRecommendation {
  rank: number
  regionCode: string
  regionName: string
  normalizedThemeDemand: number
  rawStayStrength: number
  normalizedStayStrength: number
  recommendationScore: number
  recommendationScorePercent: number
  interpretation: string
  themeScores: ThemeScore[]
  latitude: number | null
  longitude: number | null
}

export interface PersonalizedRecommendationResponse {
  referenceDate: string | null
  count: number
  formula: string
  selectedThemes: ThemeOption[]
  recommendations: PersonalizedRecommendation[]
}

export type PersonalizedRecommendationApiResponse =
  ApiResponse<PersonalizedRecommendationResponse>
