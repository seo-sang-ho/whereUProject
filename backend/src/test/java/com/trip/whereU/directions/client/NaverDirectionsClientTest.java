package com.trip.whereU.directions.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trip.whereU.directions.config.NaverDirectionsProperties;
import com.trip.whereU.directions.dto.NaverDirectionsResult;
import com.trip.whereU.map.config.NaverMapsProperties;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NaverDirectionsClientTest {

	@Test
	void buildsDirectionsUriWithLongitudeBeforeLatitude() {
		URI uri = client().buildDirectionsUri(37.5665, 126.9780, 35.8562, 129.2247);

		assertThat(uri.getRawQuery()).contains(
				"start=126.978,37.5665",
				"goal=129.2247,35.8562",
				"option=traoptimal",
				"cartype=1",
				"lang=ko"
		);
	}

	@Test
	void parsesOptimalSummaryAndRoundsDurationUpToNearestMinute() {
		Optional<NaverDirectionsResult> result = client().parseResult("""
				{"code":0,"route":{"traoptimal":[{"summary":{
				  "distance":84200,"duration":4470001,"tollFare":3200,
				  "departureTime":"2026-07-20T15:00:00+09:00"
				}}]}}
				""");

		assertThat(result).contains(new NaverDirectionsResult(
				75, 84200, 3200, OffsetDateTime.parse("2026-07-20T15:00:00+09:00")
		));
	}

	@Test
	void returnsEmptyWhenOptimalRouteDoesNotExist() {
		assertThat(client().parseResult("{\"code\":0,\"route\":{}}"))
				.isEmpty();
	}

	@Test
	void sanitizesExternalFailureWithoutUriCoordinatesOrCause() {
		assertThatThrownBy(() -> clientWithDirectionsBaseUrl("http://127.0.0.1:1/directions")
				.getDrivingEstimate(37.5665, 126.9780, 35.8562, 129.2247))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Naver Directions API 호출에 실패했습니다.")
				.hasNoCause();
	}

	private NaverDirectionsClient client() {
		return clientWithDirectionsBaseUrl("https://example.com/directions");
	}

	private NaverDirectionsClient clientWithDirectionsBaseUrl(String directionsBaseUrl) {
		return new NaverDirectionsClient(
				new NaverMapsProperties("https://example.com/geocode", "test-key-id", "test-key"),
				new NaverDirectionsProperties(directionsBaseUrl, 50000, 10, 10000),
				new ObjectMapper()
		);
	}
}
