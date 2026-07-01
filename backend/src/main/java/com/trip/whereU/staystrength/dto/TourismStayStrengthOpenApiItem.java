package com.trip.whereU.staystrength.dto;

import java.time.LocalDate;

public record TourismStayStrengthOpenApiItem(
		String regionCode,
		String regionName,
		String districtCode,
		double stayStrength,
		LocalDate referenceDate
) {

	public boolean isAreaAggregate() {
		return "0".equals(districtCode);
	}
}
