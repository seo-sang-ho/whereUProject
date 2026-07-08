package com.trip.whereU.tourism.dto;

import com.trip.whereU.tourism.entity.TourismContent;

public record TourismContentResponse(
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

	public static TourismContentResponse from(TourismContent content) {
		return new TourismContentResponse(
				content.getContentId(),
				content.getContentTypeId(),
				content.getTitle(),
				content.getAddress(),
				content.getDetailAddress(),
				content.getLatitude(),
				content.getLongitude(),
				content.getAreaCode(),
				content.getSigunguCode(),
				content.getLegalDongCode(),
				content.getCategoryCode(),
				content.getCat1(),
				content.getCat2(),
				content.getCat3(),
				content.getFirstImage(),
				content.getFirstImage2(),
				content.getTel(),
				content.getZipcode(),
				content.getModifiedTime()
		);
	}
}
