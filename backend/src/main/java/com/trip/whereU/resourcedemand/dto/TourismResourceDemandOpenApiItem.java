package com.trip.whereU.resourcedemand.dto;

import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import java.time.LocalDate;

public record TourismResourceDemandOpenApiItem(
		String regionCode,
		String regionName,
		String districtCode,
		ResourceDemandType resourceType,
		String indicatorCode,
		String indicatorName,
		double value,
		LocalDate referenceDate
) {

	public boolean isAreaAggregate() {
		return "0".equals(districtCode);
	}
}
