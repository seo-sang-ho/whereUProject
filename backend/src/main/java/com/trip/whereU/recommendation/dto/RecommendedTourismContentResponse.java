package com.trip.whereU.recommendation.dto;

import com.trip.whereU.tourism.entity.TourismContent;

public record RecommendedTourismContentResponse(
		String contentId,
		String contentTypeId,
		String title,
		String address,
		String firstImage,
		String firstImage2,
		Double latitude,
		Double longitude,
		String legalDongCode,
		String categoryCode
) {

	public static RecommendedTourismContentResponse from(TourismContent content) {
		return new RecommendedTourismContentResponse(
				content.getContentId(),
				content.getContentTypeId(),
				content.getTitle(),
				content.getAddress(),
				content.getFirstImage(),
				content.getFirstImage2(),
				content.getLatitude(),
				content.getLongitude(),
				content.getLegalDongCode(),
				content.getCategoryCode()
		);
	}
}
