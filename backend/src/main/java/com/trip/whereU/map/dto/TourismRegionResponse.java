package com.trip.whereU.map.dto;

import com.trip.whereU.map.entity.TourismRegion;
import java.time.LocalDateTime;

public record TourismRegionResponse(
		String regionCode,
		String regionName,
		double latitude,
		double longitude,
		LocalDateTime updatedAt
) {

	public static TourismRegionResponse from(TourismRegion region) {
		return new TourismRegionResponse(
				region.getRegionCode(),
				region.getRegionName(),
				region.getLatitude(),
				region.getLongitude(),
				region.getUpdatedAt()
		);
	}
}
