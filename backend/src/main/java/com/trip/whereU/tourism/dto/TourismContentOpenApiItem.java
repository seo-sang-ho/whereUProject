package com.trip.whereU.tourism.dto;

public record TourismContentOpenApiItem(
		String contentId,
		String contentTypeId,
		String title,
		String address,
		String detailAddress,
		Double latitude,
		Double longitude,
		String areaCode,
		String sigunguCode,
		String legalDongCode,
		String categoryCode,
		String cat1,
		String cat2,
		String cat3,
		String firstImage,
		String firstImage2,
		String tel,
		String zipcode,
		String modifiedTime
) {
}
