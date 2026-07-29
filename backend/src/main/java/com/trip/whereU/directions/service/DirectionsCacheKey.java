package com.trip.whereU.directions.service;

import java.math.BigDecimal;

record DirectionsCacheKey(
		BigDecimal originLatitude,
		BigDecimal originLongitude,
		String destinationContentId,
		String option
) {
}
