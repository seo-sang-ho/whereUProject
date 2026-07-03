package com.trip.whereU.resourcedemand.dto;

import java.time.LocalDate;
import java.util.List;

public record TourismResourceDemandLatestResponse(
		LocalDate referenceDate,
		int count,
		List<TourismResourceDemandResponse> resourceDemands
) {

	public static TourismResourceDemandLatestResponse empty() {
		return new TourismResourceDemandLatestResponse(null, 0, List.of());
	}

	public static TourismResourceDemandLatestResponse of(
			LocalDate referenceDate,
			List<TourismResourceDemandResponse> resourceDemands
	) {
		return new TourismResourceDemandLatestResponse(
				referenceDate,
				resourceDemands.size(),
				List.copyOf(resourceDemands)
		);
	}
}
