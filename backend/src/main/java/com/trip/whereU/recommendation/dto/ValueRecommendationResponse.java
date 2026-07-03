package com.trip.whereU.recommendation.dto;

import java.time.LocalDate;
import java.util.List;

public record ValueRecommendationResponse(
		LocalDate referenceDate,
		int count,
		String formula,
		List<ValueRecommendationItemResponse> recommendations
) {

	private static final String FORMULA = "(서비스 수요 × 0.7) + ((1 - 체류강도) × 0.3)";

	public static ValueRecommendationResponse empty() {
		return new ValueRecommendationResponse(null, 0, FORMULA, List.of());
	}

	public static ValueRecommendationResponse of(
			LocalDate referenceDate,
			List<ValueRecommendationItemResponse> recommendations
	) {
		return new ValueRecommendationResponse(
				referenceDate,
				recommendations.size(),
				FORMULA,
				List.copyOf(recommendations)
		);
	}
}
