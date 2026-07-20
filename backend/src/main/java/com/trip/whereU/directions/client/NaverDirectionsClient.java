package com.trip.whereU.directions.client;

import com.trip.whereU.directions.config.NaverDirectionsProperties;
import com.trip.whereU.directions.dto.NaverDirectionsResult;
import com.trip.whereU.map.config.NaverMapsProperties;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NaverDirectionsClient {

	private final NaverMapsProperties mapsProperties;
	private final NaverDirectionsProperties directionsProperties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public NaverDirectionsClient(
			NaverMapsProperties mapsProperties,
			NaverDirectionsProperties directionsProperties,
			ObjectMapper objectMapper
	) {
		this.mapsProperties = mapsProperties;
		this.directionsProperties = directionsProperties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public Optional<NaverDirectionsResult> getDrivingEstimate(
			double originLatitude,
			double originLongitude,
			double destinationLatitude,
			double destinationLongitude
	) {
		validateProperties();
		try {
			String responseBody = restClient.get()
					.uri(buildDirectionsUri(originLatitude, originLongitude, destinationLatitude, destinationLongitude))
					.header("x-ncp-apigw-api-key-id", mapsProperties.apiKeyId())
					.header("x-ncp-apigw-api-key", mapsProperties.apiKey())
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(String.class);
			return parseResult(responseBody);
		} catch (RestClientException exception) {
			throw new IllegalStateException("Naver Directions API 호출에 실패했습니다.");
		}
	}

	URI buildDirectionsUri(
			double originLatitude,
			double originLongitude,
			double destinationLatitude,
			double destinationLongitude
	) {
		return UriComponentsBuilder
				.fromUriString(directionsProperties.directionsBaseUrl())
				.queryParam("start", originLongitude + "," + originLatitude)
				.queryParam("goal", destinationLongitude + "," + destinationLatitude)
				.queryParam("option", "traoptimal")
				.queryParam("cartype", 1)
				.queryParam("lang", "ko")
				.build()
				.encode()
				.toUri();
	}

	Optional<NaverDirectionsResult> parseResult(String responseBody) {
		try {
			JsonNode summary = objectMapper.readTree(responseBody)
					.path("route")
					.path("traoptimal");
			if (!summary.isArray() || summary.isEmpty()) {
				return Optional.empty();
			}
			summary = summary.get(0).path("summary");
			if (summary.isMissingNode()) {
				return Optional.empty();
			}

			long durationMillis = summary.path("duration").asLong();
			return Optional.of(new NaverDirectionsResult(
					(int) ((durationMillis + 59_999) / 60_000),
					summary.path("distance").asLong(),
					summary.path("tollFare").asInt(),
					OffsetDateTime.parse(summary.path("departureTime").asText())
			));
		} catch (Exception exception) {
			throw new IllegalStateException("Naver Directions API 응답을 해석하지 못했습니다.", exception);
		}
	}

	private void validateProperties() {
		if (!StringUtils.hasText(directionsProperties.directionsBaseUrl())
				|| !StringUtils.hasText(mapsProperties.apiKeyId())
				|| !StringUtils.hasText(mapsProperties.apiKey())) {
			throw new IllegalStateException("Naver Directions 설정이 필요합니다.");
		}
	}
}
