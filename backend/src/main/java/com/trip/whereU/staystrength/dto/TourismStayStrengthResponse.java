package com.trip.whereU.staystrength.dto;

import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.map.entity.TourismRegion;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TourismStayStrengthResponse(
		Long id,
		String regionCode,
		String regionName,
		double rawStayStrength,
		double normalizedStayStrength,
		String level,
		String levelLabel,
		String interpretation,
		Double latitude,
		Double longitude,
		LocalDate referenceDate,
		LocalDateTime updatedAt
) {

	public static TourismStayStrengthResponse from(TourismStayStrength stayStrength, TourismRegion region) {
		return new TourismStayStrengthResponse(
				stayStrength.getId(),
				stayStrength.getRegionCode(),
				stayStrength.getRegionName(),
				stayStrength.getRawStayStrength(),
				stayStrength.getNormalizedStayStrength(),
				stayStrength.getLevel().name(),
				stayStrength.getLevel().getLabel(),
				stayStrength.getLevel().getDescription(),
				region == null ? null : region.getLatitude(),
				region == null ? null : region.getLongitude(),
				stayStrength.getReferenceDate(),
				stayStrength.getUpdatedAt()
		);
	}
}
