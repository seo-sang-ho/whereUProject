package com.trip.whereU.demand.dto;

import java.time.LocalDate;

public record TourismDemandOpenApiItem(
		String regionCode,
		String regionName,
		double demandScore,
		Double latitude,
		Double longitude,
		LocalDate referenceDate
) {
}
