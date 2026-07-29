package com.trip.whereU.directions.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "directions.rate-limit")
public record DirectionsRateLimitProperties(
		boolean enabled,
		@Min(1) long minuteCapacity,
		@Min(1) long minuteRefillTokens,
		@Min(1) long dailyCapacity,
		@Min(1) @Max(100_000) long cacheMaximumSize,
		@Min(1) long cacheExpireAfterHours
) {
}
