package com.trip.whereU.staystrength.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourism.open-api")
public record TourismOpenApiProperties(
		String baseUrl,
		String stayStrengthEndpoint,
		String serviceKey,
		String mobileOs,
		String mobileApp,
		StayStrength stayStrength
) {

	public record StayStrength(
			String baseYm,
			List<String> areaCodes,
			String indicatorCode
	) {
	}
}
