package com.trip.whereU.recommendation.dto;

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
		Double longitude
) {
}
