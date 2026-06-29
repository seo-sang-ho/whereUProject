package com.trip.whereU.demand.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.trip.whereU.demand.config.TourismOpenApiProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;

class TourismDemandOpenApiClientTest {

	@Test
	void preservesPreEncodedServiceKeyWithoutDoubleEncoding() {
		TourismOpenApiProperties properties = new TourismOpenApiProperties(
				"https://apis.data.go.kr/B551011/AreaTarDemDsService",
				"/areaTarSjrnDsList",
				"Gfs%2Babc%2Fdef%3D%3D",
				"ETC",
				"where U",
				new TourismOpenApiProperties.Demand("202509", "11", "11530", "21")
		);
		TourismDemandOpenApiClient client = new TourismDemandOpenApiClient(properties, null);

		URI uri = client.buildDemandUri(1, 10, properties.serviceKey());
		String requestUri = uri.toASCIIString();

		assertTrue(requestUri.startsWith(
				"https://apis.data.go.kr/B551011/AreaTarDemDsService/areaTarSjrnDsList?serviceKey=Gfs%2Babc%2Fdef%3D%3D&"
		));
		assertFalse(requestUri.contains("%252B"));
		assertTrue(requestUri.contains("MobileApp=where%20U"));
		assertEquals("Gfs+abc/def==", uri.getQuery().split("&", 2)[0].substring("serviceKey=".length()));
	}
}
