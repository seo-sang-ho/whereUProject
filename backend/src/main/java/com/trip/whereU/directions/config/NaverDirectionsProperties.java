package com.trip.whereU.directions.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.maps")
public record NaverDirectionsProperties(
		String directionsBaseUrl,
		long directionsMonthlySafeLimit,
		long directionsCacheTtlMinutes,
		long directionsCacheMaximumSize
) {
}
