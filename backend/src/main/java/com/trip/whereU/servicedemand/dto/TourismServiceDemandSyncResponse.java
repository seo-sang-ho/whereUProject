package com.trip.whereU.servicedemand.dto;

import java.time.LocalDate;

public record TourismServiceDemandSyncResponse(
		int collectedCount,
		int savedCount,
		LocalDate referenceDate
) {
}
