package com.trip.whereU.staystrength.dto;

import java.time.LocalDate;
import java.util.List;

public record TourismStayStrengthLatestResponse(
		LocalDate referenceDate,
		int count,
		List<TourismStayStrengthResponse> stayStrengths
) {

	public static TourismStayStrengthLatestResponse empty() {
		return new TourismStayStrengthLatestResponse(null, 0, List.of());
	}

	public static TourismStayStrengthLatestResponse of(
			LocalDate referenceDate,
			List<TourismStayStrengthResponse> stayStrengths
	) {
		return new TourismStayStrengthLatestResponse(referenceDate, stayStrengths.size(), List.copyOf(stayStrengths));
	}
}
