package com.trip.whereU.staystrength.dto;

import java.util.List;

public record TourismStayStrengthOpenApiPage(
		List<TourismStayStrengthOpenApiItem> items,
		int pageNo,
		int numOfRows,
		int totalCount
) {
}
