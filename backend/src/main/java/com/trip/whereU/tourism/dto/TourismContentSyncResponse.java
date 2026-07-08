package com.trip.whereU.tourism.dto;

public record TourismContentSyncResponse(
		int fetchedCount,
		int savedCount,
		int pageRequestCount
) {
}
