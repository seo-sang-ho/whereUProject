package com.trip.whereU.servicedemand.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tourism.resource-demand-api")
public record TourismResourceDemandApiProperties(
		String baseUrl,
		String serviceDemandEndpoint,
		String culturalResourceDemandEndpoint,
		String serviceKey,
		String mobileOs,
		String mobileApp,
		ServiceDemand serviceDemand
) {

	public record ServiceDemand(
			String baseYm,
			List<String> areaCodes,
			String indicatorCode
	) {
	}
}
