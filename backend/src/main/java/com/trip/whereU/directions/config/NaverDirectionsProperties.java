package com.trip.whereU.directions.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "naver.maps")
public record NaverDirectionsProperties(
		String directionsBaseUrl,
		@NotNull
		Duration directionsConnectTimeout,
		@NotNull
		Duration directionsReadTimeout,
		@Min(1)
		@Max(50_000)
		long directionsMonthlySafeLimit,
		long directionsCacheTtlMinutes,
		long directionsCacheMaximumSize
) {
}
