package com.trip.whereU.directions.dto;

import java.time.OffsetDateTime;

public record NaverDirectionsResult(
		int travelTimeMinutes,
		long distanceMeters,
		int tollFare,
		OffsetDateTime calculatedAt
) {
}
