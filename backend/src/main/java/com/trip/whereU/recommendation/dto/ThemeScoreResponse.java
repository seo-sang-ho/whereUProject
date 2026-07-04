package com.trip.whereU.recommendation.dto;

public record ThemeScoreResponse(
		String theme,
		String themeLabel,
		double normalizedScore,
		int indicatorCount
) {
}
