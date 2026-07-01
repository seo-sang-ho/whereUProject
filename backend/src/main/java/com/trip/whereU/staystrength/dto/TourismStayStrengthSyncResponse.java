package com.trip.whereU.staystrength.dto;

import java.time.LocalDate;

public record TourismStayStrengthSyncResponse(
		int collectedCount,
		int savedCount,
		LocalDate referenceDate
) {
}
