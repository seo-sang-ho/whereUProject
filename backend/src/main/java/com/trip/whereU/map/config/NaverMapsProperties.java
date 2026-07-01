package com.trip.whereU.map.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.maps")
public record NaverMapsProperties(
		String geocodingBaseUrl,
		String apiKeyId,
		String apiKey
) {
}
