package com.trip.whereU.resourcedemand.dto;

import java.time.LocalDate;
import java.util.List;

public record TourismResourceDemandSyncResponse(
		int serviceCollectedCount,
		int culturalCollectedCount,
		int savedCount,
		LocalDate referenceDate,
		int successfulIndicatorCount,
		int skippedIndicatorCount,
		List<String> skippedIndicators,
		String collectionStrategy,
		int openApiPageRequestCount,
		List<String> failedIndicators
) {
}
