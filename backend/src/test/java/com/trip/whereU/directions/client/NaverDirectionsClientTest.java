package com.trip.whereU.directions.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.trip.whereU.directions.config.NaverDirectionsProperties;
import com.trip.whereU.directions.dto.NaverDirectionsResult;
import com.trip.whereU.map.config.NaverMapsProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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

	@Test
	void convertsStalledResponseToSanitizedFailureWithinConfiguredReadTimeout() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		CountDownLatch releaseResponse = new CountDownLatch(1);
		var executor = Executors.newVirtualThreadPerTaskExecutor();
		try {
			server.setExecutor(executor);
			server.createContext("/directions", exchange -> {
				try {
					releaseResponse.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				} finally {
					exchange.close();
				}
			});
			server.start();

			NaverDirectionsClient client = clientWithTimeouts(
					"http://127.0.0.1:" + server.getAddress().getPort() + "/directions",
					Duration.ofMillis(200),
					Duration.ofMillis(100)
			);

			assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
					assertThatThrownBy(() -> client.getDrivingEstimate(37.5665, 126.9780, 35.8562, 129.2247))
							.isInstanceOf(IllegalStateException.class)
							.hasMessage("Naver Directions API 호출에 실패했습니다.")
							.hasMessageNotContaining("127.0.0.1")
							.hasMessageNotContaining("37.5665")
							.hasMessageNotContaining("126.978")
							.hasNoCause()
			);
		} finally {
			releaseResponse.countDown();
			server.stop(0);
			executor.close();
		}
	}

	private NaverDirectionsClient client() {
		return clientWithDirectionsBaseUrl("https://example.com/directions");
	}

	private NaverDirectionsClient clientWithDirectionsBaseUrl(String directionsBaseUrl) {
		return clientWithTimeouts(directionsBaseUrl, Duration.ofSeconds(3), Duration.ofSeconds(7));
	}

	private NaverDirectionsClient clientWithTimeouts(
			String directionsBaseUrl,
			Duration connectTimeout,
			Duration readTimeout
	) {
		return new NaverDirectionsClient(
				new NaverMapsProperties("https://example.com/geocode", "test-key-id", "test-key"),
				new NaverDirectionsProperties(
						directionsBaseUrl,
						connectTimeout,
						readTimeout,
						50000,
						10,
						10000
				),
				new ObjectMapper()
		);
	}
}
