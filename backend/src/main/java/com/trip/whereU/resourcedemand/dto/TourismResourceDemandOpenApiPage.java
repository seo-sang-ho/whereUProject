package com.trip.whereU.resourcedemand.dto;

import java.util.List;

public record TourismResourceDemandOpenApiPage(
		List<TourismResourceDemandOpenApiItem> items,
		int pageNo,
		int numOfRows,
		int totalCount
) {
}
