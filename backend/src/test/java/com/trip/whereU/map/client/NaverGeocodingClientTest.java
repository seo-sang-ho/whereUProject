package com.trip.whereU.map.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.trip.whereU.map.config.NaverMapsProperties;
import com.trip.whereU.map.dto.RegionCoordinate;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NaverGeocodingClientTest {

	@Test
	void buildsEncodedGeocodingUri() {
		NaverGeocodingClient client = new NaverGeocodingClient(properties(), null);

		URI uri = client.buildGeocodeUri("서울특별시 종로구");

		assertThat(uri.toASCIIString()).contains(
				"query=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C%20%EC%A2%85%EB%A1%9C%EA%B5%AC"
		);
		assertThat(uri.toASCIIString()).contains("count=1", "language=kor");
	}

	@Test
	void parsesLongitudeAndLatitudeInCorrectOrder() {
		NaverGeocodingClient client = new NaverGeocodingClient(properties(), new ObjectMapper());
		String responseBody = """
				{
				  "status": "OK",
				  "meta": {"totalCount": 1, "page": 1, "count": 1},
				  "addresses": [
				    {"x": "126.9783882", "y": "37.5666103"}
				  ]
				}
				""";

		Optional<RegionCoordinate> coordinate = client.parseCoordinate(responseBody);

		assertThat(coordinate).contains(new RegionCoordinate(37.5666103, 126.9783882));
	}

	@Test
	void addsFallbackWhenRegionNameContainsConsecutiveDuplicates() {
		NaverGeocodingClient client = new NaverGeocodingClient(properties(), null);

		List<String> candidates = client.buildQueryCandidates("세종특별자치시 세종특별자치시");

		assertThat(candidates).containsExactly(
				"세종특별자치시 세종특별자치시",
				"세종특별자치시"
		);
	}

	private NaverMapsProperties properties() {
		return new NaverMapsProperties(
				"https://maps.apigw.ntruss.com/map-geocode/v2/geocode",
				"test-key-id",
				"test-key"
		);
	}
}
