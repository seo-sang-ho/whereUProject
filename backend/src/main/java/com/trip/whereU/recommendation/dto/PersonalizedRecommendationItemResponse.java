package com.trip.whereU.recommendation.dto;

import java.util.List;

public record PersonalizedRecommendationItemResponse(
		int rank,
		String regionCode,
		String regionName,
		double normalizedThemeDemand,
		double rawStayStrength,
		double normalizedStayStrength,
		double recommendationScore,
		int recommendationScorePercent,
		String interpretation,
		List<ThemeScoreResponse> themeScores,
		Double latitude,
		Double longitude
) {
}
