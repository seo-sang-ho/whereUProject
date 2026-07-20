package com.trip.whereU.directions.dto;

import java.time.OffsetDateTime;

public record DirectionsEstimateResponse(
		DirectionsStatus status,
		String destinationContentId,
		String destinationName,
		Integer travelTimeMinutes,
		Long distanceMeters,
		Integer tollFare,
		OffsetDateTime calculatedAt,
		DirectionsFallbackReason fallbackReason
) {

	public static DirectionsEstimateResponse available(
			String destinationContentId,
			String destinationName,
			int travelTimeMinutes,
			long distanceMeters,
			int tollFare,
			OffsetDateTime calculatedAt
	) {
		return new DirectionsEstimateResponse(
				DirectionsStatus.AVAILABLE,
				destinationContentId,
				destinationName,
				travelTimeMinutes,
				distanceMeters,
				tollFare,
				calculatedAt,
				null
		);
	}

	public static DirectionsEstimateResponse naverMapRequired(
			String destinationContentId,
			String destinationName,
			DirectionsFallbackReason fallbackReason
	) {
		return new DirectionsEstimateResponse(
				DirectionsStatus.NAVER_MAP_REQUIRED,
				destinationContentId,
				destinationName,
				null,
				null,
				null,
				null,
				fallbackReason
		);
	}
}
