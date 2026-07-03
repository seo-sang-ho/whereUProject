package com.trip.whereU.resourcedemand.dto;

import com.trip.whereU.resourcedemand.entity.TourismResourceDemand;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TourismResourceDemandResponse(
		Long id,
		String regionCode,
		String regionName,
		String resourceType,
		String resourceTypeLabel,
		String indicatorCode,
		String indicatorName,
		String theme,
		String themeLabel,
		double rawValue,
		double normalizedValue,
		LocalDate referenceDate,
		LocalDateTime updatedAt
) {

	public static TourismResourceDemandResponse from(TourismResourceDemand demand) {
		return new TourismResourceDemandResponse(
				demand.getId(),
				demand.getRegionCode(),
				demand.getRegionName(),
				demand.getResourceType().name(),
				demand.getResourceType().getLabel(),
				demand.getIndicatorCode(),
				demand.getIndicatorName(),
				demand.getTheme() == null ? null : demand.getTheme().name(),
				demand.getTheme() == null ? null : demand.getTheme().getLabel(),
				demand.getRawValue(),
				demand.getNormalizedValue(),
				demand.getReferenceDate(),
				demand.getUpdatedAt()
		);
	}
}
