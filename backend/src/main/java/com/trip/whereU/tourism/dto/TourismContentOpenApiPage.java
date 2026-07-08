package com.trip.whereU.tourism.dto;

import java.util.List;

public record TourismContentOpenApiPage(
		List<TourismContentOpenApiItem> items,
		int pageNo,
		int numOfRows,
		int totalCount
) {
}
