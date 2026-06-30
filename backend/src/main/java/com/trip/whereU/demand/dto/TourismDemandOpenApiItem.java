package com.trip.whereU.demand.dto;

import java.time.LocalDate;

public record TourismDemandOpenApiItem(
		String regionCode,
		String regionName,
		String districtCode,
		double demandScore,
		Double latitude,
		Double longitude,
		LocalDate referenceDate
) {

	public boolean isAreaAggregate() {
		return "0".equals(districtCode);
	}
}
