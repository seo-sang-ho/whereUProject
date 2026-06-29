package com.trip.whereU.demand.dto;

import java.time.LocalDate;

public record TourismDemandSyncResponse(
		int collectedCount,
		int savedCount,
		LocalDate referenceDate
) {
}
