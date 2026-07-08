package com.trip.whereU.recommendation.dto;

import java.util.List;

public record ValueRecommendationItemResponse(
		int rank,
		String regionCode,
		String regionName,
		double rawServiceDemand,
		double normalizedServiceDemand,
		double rawStayStrength,
		double normalizedStayStrength,
		double recommendationScore,
		int recommendationScorePercent,
		String interpretation,
		Double latitude,
		Double longitude,
		List<RecommendedTourismContentResponse> tourismContents
) {
}
