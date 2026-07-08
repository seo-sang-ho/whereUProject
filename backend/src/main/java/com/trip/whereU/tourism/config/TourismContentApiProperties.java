package com.trip.whereU.tourism.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourism.content-api")
public record TourismContentApiProperties(
		String baseUrl,
		String areaBasedListEndpoint,
		String serviceKey,
		String mobileOs,
		String mobileApp
) {
}
