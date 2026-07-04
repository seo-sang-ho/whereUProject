package com.trip.whereU.recommendation.dto;

import com.trip.whereU.resourcedemand.entity.TourismTheme;
import java.time.LocalDate;
import java.util.List;

public record PersonalizedRecommendationResponse(
		LocalDate referenceDate,
		int count,
		String formula,
		List<ThemeOptionResponse> selectedThemes,
		List<PersonalizedRecommendationItemResponse> recommendations
) {

	private static final String FORMULA = "(선택 테마 자원 수요 × 0.7) + ((1 - 체류강도) × 0.3)";

	public static PersonalizedRecommendationResponse empty(List<TourismTheme> themes) {
		return new PersonalizedRecommendationResponse(
				null,
				0,
				FORMULA,
				toThemeOptions(themes),
				List.of()
		);
	}

	public static PersonalizedRecommendationResponse of(
			LocalDate referenceDate,
			List<TourismTheme> themes,
			List<PersonalizedRecommendationItemResponse> recommendations
	) {
		return new PersonalizedRecommendationResponse(
				referenceDate,
				recommendations.size(),
				FORMULA,
				toThemeOptions(themes),
				List.copyOf(recommendations)
		);
	}

	private static List<ThemeOptionResponse> toThemeOptions(List<TourismTheme> themes) {
		return themes.stream()
				.map(theme -> new ThemeOptionResponse(theme.name(), theme.getLabel()))
				.toList();
	}
}
