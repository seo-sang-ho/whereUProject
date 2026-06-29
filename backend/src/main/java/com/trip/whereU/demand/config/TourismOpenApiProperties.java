package com.trip.whereU.demand.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourism.open-api")
public record TourismOpenApiProperties(
		String baseUrl,
		String demandEndpoint,
		String serviceKey,
		String mobileOs,
		String mobileApp,
		Demand demand
) {

	public record Demand(
			String baseYm,
			String areaCd,
			String signguCd,
			String indicatorCode
	) {
	}
}
