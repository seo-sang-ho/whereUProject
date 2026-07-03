package com.trip.whereU.servicedemand.dto;

import java.time.LocalDate;

public record TourismServiceDemandOpenApiItem(
		String regionCode,
		String regionName,
		String districtCode,
		double serviceDemand,
		LocalDate referenceDate
) {

	public boolean isAreaAggregate() {
		return "0".equals(districtCode);
	}
}
