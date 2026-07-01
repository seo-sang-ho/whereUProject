package com.trip.whereU.map.dto;

import java.util.List;

public record TourismRegionSyncResponse(
		int targetCount,
		int savedCount,
		int skippedCount,
		int failedCount,
		List<String> failedRegionCodes
) {
}
