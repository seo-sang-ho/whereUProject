package com.trip.whereU.demand.dto;

import com.trip.whereU.demand.entity.TourismDemand;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TourismDemandResponse(
		Long id,
		String regionCode,
		String regionName,
		double rawDemandScore,
		double normalizedDemandScore,
		String signal,
		String signalLabel,
		String interpretation,
		Double latitude,
		Double longitude,
		LocalDate referenceDate,
		LocalDateTime updatedAt
) {

	public static TourismDemandResponse from(TourismDemand demand) {
		return new TourismDemandResponse(
				demand.getId(),
				demand.getRegionCode(),
				demand.getRegionName(),
				demand.getRawDemandScore(),
				demand.getNormalizedDemandScore(),
				demand.getDemandSignal().name(),
				demand.getDemandSignal().getLabel(),
				demand.getDemandSignal().getDescription(),
				demand.getLatitude(),
				demand.getLongitude(),
				demand.getReferenceDate(),
				demand.getUpdatedAt()
		);
	}
}
