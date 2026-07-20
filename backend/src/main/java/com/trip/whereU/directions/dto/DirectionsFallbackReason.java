package com.trip.whereU.directions.dto;

public enum DirectionsFallbackReason {
	MONTHLY_LIMIT_REACHED,
	DESTINATION_COORDINATES_MISSING,
	NAVER_API_UNAVAILABLE,
	ROUTE_NOT_FOUND
}
