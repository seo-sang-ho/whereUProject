package com.trip.whereU.directions.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DirectionsRateLimitPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(NaverDirectionsConfig.class)
			.withPropertyValues(
					"naver.maps.directions-base-url=https://example.com/directions",
					"naver.maps.directions-connect-timeout=3s",
					"naver.maps.directions-read-timeout=7s",
					"naver.maps.directions-monthly-safe-limit=50000",
					"naver.maps.directions-cache-ttl-minutes=10",
					"naver.maps.directions-cache-maximum-size=10000",
					"directions.rate-limit.enabled=true",
					"directions.rate-limit.minute-capacity=10",
					"directions.rate-limit.minute-refill-tokens=10",
					"directions.rate-limit.daily-capacity=100",
					"directions.rate-limit.cache-maximum-size=10000",
					"directions.rate-limit.cache-expire-after-hours=24"
			);

	@Test
	void bindsApprovedValues() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(DirectionsRateLimitProperties.class);
			assertThat(context.getBean(DirectionsRateLimitProperties.class))
					.isEqualTo(new DirectionsRateLimitProperties(true, 10, 10, 100, 10_000, 24));
		});
	}

	@Test
	void rejectsCacheMaximumSizeAboveAbsoluteMaximum() {
		contextRunner
				.withPropertyValues("directions.rate-limit.cache-maximum-size=100001")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsNonPositiveCapacity() {
		contextRunner
				.withPropertyValues("directions.rate-limit.minute-capacity=0")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsNonPositiveExpirationHours() {
		contextRunner
				.withPropertyValues("directions.rate-limit.cache-expire-after-hours=0")
				.run(context -> assertThat(context).hasFailed());
	}
}
