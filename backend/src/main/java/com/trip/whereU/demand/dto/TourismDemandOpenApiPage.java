package com.trip.whereU.demand.dto;

import java.util.List;

public record TourismDemandOpenApiPage(
		List<TourismDemandOpenApiItem> items,
		int pageNo,
		int numOfRows,
		int totalCount
) {
}
