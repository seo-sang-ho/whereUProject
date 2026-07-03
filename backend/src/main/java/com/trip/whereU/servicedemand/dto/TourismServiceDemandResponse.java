package com.trip.whereU.servicedemand.dto;

import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TourismServiceDemandResponse(
		Long id,
		String regionCode,
		String regionName,
		double rawServiceDemand,
		double normalizedServiceDemand,
		String level,
		String levelLabel,
		String interpretation,
		Double latitude,
		Double longitude,
		LocalDate referenceDate,
		LocalDateTime updatedAt
) {

	public static TourismServiceDemandResponse from(
			TourismServiceDemand serviceDemand,
			TourismRegion region
	) {
		return new TourismServiceDemandResponse(
				serviceDemand.getId(),
				serviceDemand.getRegionCode(),
				serviceDemand.getRegionName(),
				serviceDemand.getRawServiceDemand(),
				serviceDemand.getNormalizedServiceDemand(),
				serviceDemand.getLevel().name(),
				serviceDemand.getLevel().getLabel(),
				serviceDemand.getLevel().getDescription(),
				region == null ? null : region.getLatitude(),
				region == null ? null : region.getLongitude(),
				serviceDemand.getReferenceDate(),
				serviceDemand.getUpdatedAt()
		);
	}
}
