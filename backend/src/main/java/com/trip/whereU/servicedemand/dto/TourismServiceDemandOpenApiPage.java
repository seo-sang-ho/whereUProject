package com.trip.whereU.servicedemand.dto;

import java.util.List;

public record TourismServiceDemandOpenApiPage(
		List<TourismServiceDemandOpenApiItem> items,
		int pageNo,
		int numOfRows,
		int totalCount
) {
}
