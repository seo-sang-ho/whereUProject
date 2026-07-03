package com.trip.whereU.servicedemand.dto;

import java.time.LocalDate;
import java.util.List;

public record TourismServiceDemandLatestResponse(
		LocalDate referenceDate,
		int count,
		List<TourismServiceDemandResponse> serviceDemands
) {

	public static TourismServiceDemandLatestResponse empty() {
		return new TourismServiceDemandLatestResponse(null, 0, List.of());
	}

	public static TourismServiceDemandLatestResponse of(
			LocalDate referenceDate,
			List<TourismServiceDemandResponse> serviceDemands
	) {
		return new TourismServiceDemandLatestResponse(
				referenceDate,
				serviceDemands.size(),
				List.copyOf(serviceDemands)
		);
	}
}
