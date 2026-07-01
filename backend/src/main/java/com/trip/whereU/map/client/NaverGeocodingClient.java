package com.trip.whereU.map.client;

import com.trip.whereU.map.config.NaverMapsProperties;
import com.trip.whereU.map.dto.RegionCoordinate;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class NaverGeocodingClient {

	private final NaverMapsProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public NaverGeocodingClient(NaverMapsProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public Optional<RegionCoordinate> geocode(String regionName) {
		validateProperties();
		for (String query : buildQueryCandidates(regionName)) {
			Optional<RegionCoordinate> coordinate = requestCoordinate(query);
			if (coordinate.isPresent()) {
				return coordinate;
			}
		}
		return Optional.empty();
	}

	private Optional<RegionCoordinate> requestCoordinate(String query) {
		String responseBody = restClient.get()
				.uri(buildGeocodeUri(query))
				.header("x-ncp-apigw-api-key-id", properties.apiKeyId())
				.header("x-ncp-apigw-api-key", properties.apiKey())
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(String.class);
		return parseCoordinate(responseBody);
	}

	List<String> buildQueryCandidates(String regionName) {
		List<String> candidates = new ArrayList<>();
		candidates.add(regionName);

		String[] words = regionName.trim().split("\\s+");
		List<String> deduplicated = new ArrayList<>();
		for (String word : words) {
			if (deduplicated.isEmpty() || !deduplicated.get(deduplicated.size() - 1).equals(word)) {
				deduplicated.add(word);
			}
		}
		String fallback = String.join(" ", deduplicated);
		if (!fallback.equals(regionName)) {
			candidates.add(fallback);
		}
		return List.copyOf(candidates);
	}

	URI buildGeocodeUri(String regionName) {
		return UriComponentsBuilder
				.fromUriString(properties.geocodingBaseUrl())
				.queryParam("query", regionName)
				.queryParam("count", 1)
				.queryParam("language", "kor")
				.build()
				.encode()
				.toUri();
	}

	Optional<RegionCoordinate> parseCoordinate(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			if (!"OK".equals(root.path("status").asText())) {
				throw new IllegalStateException("Naver Geocoding API 응답 상태가 올바르지 않습니다.");
			}
			JsonNode addresses = root.path("addresses");
			if (!addresses.isArray() || addresses.isEmpty()) {
				return Optional.empty();
			}
			JsonNode address = addresses.get(0);
			return Optional.of(new RegionCoordinate(
					Double.parseDouble(address.path("y").asText()),
					Double.parseDouble(address.path("x").asText())
			));
		} catch (NumberFormatException exception) {
			throw new IllegalStateException("Naver Geocoding API 좌표 형식을 해석하지 못했습니다.", exception);
		} catch (Exception exception) {
			if (exception instanceof IllegalStateException illegalStateException) {
				throw illegalStateException;
			}
			throw new IllegalStateException("Naver Geocoding API 응답을 해석하지 못했습니다.", exception);
		}
	}

	private void validateProperties() {
		if (!StringUtils.hasText(properties.geocodingBaseUrl())
				|| !StringUtils.hasText(properties.apiKeyId())
				|| !StringUtils.hasText(properties.apiKey())) {
			throw new IllegalStateException(
					"Naver Maps 설정이 필요합니다. NAVER_MAPS_API_KEY_ID와 NAVER_MAPS_API_KEY를 확인하세요."
			);
		}
	}
}
