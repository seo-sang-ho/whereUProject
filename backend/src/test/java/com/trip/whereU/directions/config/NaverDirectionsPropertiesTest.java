package com.trip.whereU.directions.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NaverDirectionsPropertiesTest {

	@Test
	void rejectsMonthlySafeLimitAboveAbsoluteMaximumDuringBinding() {
		contextRunner(50_001).run(context -> {
			assertThat(context).hasFailed();
			assertThat(context.getStartupFailure())
					.hasRootCauseInstanceOf(BindValidationException.class);
		});
	}

	@Test
	void acceptsAbsoluteMaximumMonthlySafeLimitDuringBinding() {
		contextRunner(50_000).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(NaverDirectionsProperties.class);
			assertThat(context.getBean(NaverDirectionsProperties.class).directionsMonthlySafeLimit())
					.isEqualTo(50_000);
		});
	}

	private ApplicationContextRunner contextRunner(long monthlySafeLimit) {
		return new ApplicationContextRunner()
				.withUserConfiguration(NaverDirectionsConfig.class)
				.withPropertyValues(
						"naver.maps.directions-base-url=https://example.com/directions",
						"naver.maps.directions-connect-timeout=3s",
						"naver.maps.directions-read-timeout=7s",
						"naver.maps.directions-monthly-safe-limit=" + monthlySafeLimit,
						"naver.maps.directions-cache-ttl-minutes=10",
						"naver.maps.directions-cache-maximum-size=10000"
				);
	}
}
