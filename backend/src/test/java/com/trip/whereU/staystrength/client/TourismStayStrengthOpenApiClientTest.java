package com.trip.whereU.staystrength.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trip.whereU.staystrength.config.TourismOpenApiProperties;
import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiPage;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TourismStayStrengthOpenApiClientTest {

	@Test
	void preservesPreEncodedServiceKeyWithoutDoubleEncoding() {
		TourismOpenApiProperties properties = new TourismOpenApiProperties(
				"https://apis.data.go.kr/B551011/AreaTarDemDsService",
				"/areaTarSjrnDsList",
				"Gfs%2Babc%2Fdef%3D%3D",
				"ETC",
				"where U",
				new TourismOpenApiProperties.StayStrength("202509", List.of("11"), "21")
		);
		TourismStayStrengthOpenApiClient client = new TourismStayStrengthOpenApiClient(properties, null);

		URI uri = client.buildStayStrengthUri("11", 1, 10, properties.serviceKey());
		String requestUri = uri.toASCIIString();

		assertTrue(requestUri.startsWith(
				"https://apis.data.go.kr/B551011/AreaTarDemDsService/areaTarSjrnDsList?serviceKey=Gfs%2Babc%2Fdef%3D%3D&"
		));
		assertFalse(requestUri.contains("%252B"));
		assertFalse(requestUri.contains("signguCd"));
		assertTrue(requestUri.contains("MobileApp=where%20U"));
		assertEquals("Gfs+abc/def==", uri.getQuery().split("&", 2)[0].substring("serviceKey=".length()));
	}

	@Test
	void parsesPaginationAndAreaAggregate() {
		TourismOpenApiProperties properties = new TourismOpenApiProperties(
				"https://apis.data.go.kr/B551011/AreaTarDemDsService",
				"/areaTarSjrnDsList",
				"encoded-key",
				"ETC",
				"whereU",
				new TourismOpenApiProperties.StayStrength("202509", List.of("11"), "21")
		);
		TourismStayStrengthOpenApiClient client = new TourismStayStrengthOpenApiClient(properties, new ObjectMapper());
		String responseBody = """
				{
				  "response": {
				    "header": {"resultCode": "0000", "resultMsg": "OK"},
				    "body": {
				      "items": {"item": [
				        {"baseYm": "202509", "areaCd": "11", "areaNm": "서울특별시", "signguCd": "0", "signguNm": "_", "tarSjrnDsIxVal": "89.81"},
				        {"baseYm": "202509", "areaCd": "11", "areaNm": "서울특별시", "signguCd": "11110", "signguNm": "종로구", "tarSjrnDsIxVal": "84.26"}
				      ]},
				      "numOfRows": 10,
				      "pageNo": 1,
				      "totalCount": 26
				    }
				  }
				}
				""";

		TourismStayStrengthOpenApiPage page = client.parsePage(responseBody);

		assertEquals(26, page.totalCount());
		assertEquals(2, page.items().size());
		assertTrue(page.items().get(0).isAreaAggregate());
		assertFalse(page.items().get(1).isAreaAggregate());
		assertEquals("11-11110", page.items().get(1).regionCode());
	}
}
