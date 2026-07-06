package com.trip.whereU.resourcedemand.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiPage;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.ObjectMapper;

class TourismResourceDemandOpenApiClientTest {

	@Test
	void buildsUrisWithIndicatorFilterAndKeepsEncodedServiceKey() {
		TourismResourceDemandOpenApiClient client = new TourismResourceDemandOpenApiClient(properties(), null);

		URI serviceUri = client.buildUri(
				ResourceDemandType.SERVICE, "11", "1111", 1, 100, "Gfs%2Babc%2Fdef%3D%3D"
		);
		URI cultureUri = client.buildUri(
				ResourceDemandType.CULTURE, "11", "1205", 1, 100, "Gfs%2Babc%2Fdef%3D%3D"
		);

		assertThat(serviceUri.toString())
				.contains("/areaTarSvcDemList?serviceKey=Gfs%2Babc%2Fdef%3D%3D&")
				.contains("baseYm=202509")
				.contains("areaCd=11")
				.contains("tarSvcDemIxCd=1111")
				.doesNotContain("%252B");
		assertThat(cultureUri.toString())
				.contains("/areaCulResDemList?serviceKey=Gfs%2Babc%2Fdef%3D%3D&")
				.contains("culResDemIxCd=1205");
	}

	@Test
	void omitsOptionalIndicatorFilterForBulkCollection() {
		TourismResourceDemandOpenApiClient client = new TourismResourceDemandOpenApiClient(properties(), null);

		URI uri = client.buildUri(
				ResourceDemandType.SERVICE, "11", null, 1, 1000, "test-key"
		);

		assertThat(uri.toString())
				.contains("areaCd=11")
				.contains("numOfRows=1000")
				.doesNotContain("tarSvcDemIxCd");
	}

	@Test
	void parsesCulturalAggregateAndDistrictItems() {
		TourismResourceDemandOpenApiClient client = client();
		String responseBody = """
				{
				  "response": {
				    "header": {"resultCode": "0000", "resultMsg": "OK"},
				    "body": {
				      "items": {"item": [
				        {"baseYm":"202509","areaCd":"11","areaNm":"서울특별시","signguCd":"0","signguNm":"_","culResDemIxCd":"1205","culResDemIxNm":"내비게이션 자연관광 유형 검색량","culResDemIxVal":"61.64"},
				        {"baseYm":"202509","areaCd":"11","areaNm":"서울특별시","signguCd":"11110","signguNm":"종로구","culResDemIxCd":"1205","culResDemIxNm":"내비게이션 자연관광 유형 검색량","culResDemIxVal":"65.43"}
				      ]},
				      "pageNo": 1, "numOfRows": 2, "totalCount": 2
				    }
				  }
				}
				""";

		TourismResourceDemandOpenApiPage page = client.parsePage(ResourceDemandType.CULTURE, responseBody);

		assertThat(page.items()).hasSize(2);
		assertThat(page.items().getFirst().isAreaAggregate()).isTrue();
		assertThat(page.items().get(1)).satisfies(item -> {
			assertThat(item.regionCode()).isEqualTo("11-11110");
			assertThat(item.resourceType()).isEqualTo(ResourceDemandType.CULTURE);
			assertThat(item.indicatorCode()).isEqualTo("1205");
			assertThat(item.indicatorName()).isEqualTo("내비게이션 자연관광 유형 검색량");
			assertThat(item.value()).isEqualTo(65.43);
			assertThat(item.referenceDate()).isEqualTo(LocalDate.of(2025, 9, 1));
		});
	}

	@Test
	void parsesServiceDemandIndicatorFields() {
		String responseBody = """
				{
				  "response": {
				    "header": {"resultCode": "0000", "resultMsg": "OK"},
				    "body": {
				      "items": {"item": {
				        "baseYm":"202509","areaCd":"11","areaNm":"서울특별시","signguCd":"11110","signguNm":"종로구",
				        "tarSvcDemIxCd":"1111","tarSvcDemIxNm":"내비게이션 음식 유형 검색량","tarSvcDemIxVal":"72.4"
				      }},
				      "pageNo": 1, "numOfRows": 1, "totalCount": 1
				    }
				  }
				}
				""";

		TourismResourceDemandOpenApiPage page = client().parsePage(ResourceDemandType.SERVICE, responseBody);

		assertThat(page.items()).singleElement().satisfies(item -> {
			assertThat(item.resourceType()).isEqualTo(ResourceDemandType.SERVICE);
			assertThat(item.indicatorCode()).isEqualTo("1111");
			assertThat(item.value()).isEqualTo(72.4);
		});
	}

	@Test
	void retriesGatewayTimeoutAndReturnsSuccessfulResponse() {
		AtomicInteger attempts = new AtomicInteger();

		String response = client().executeWithRetry(() -> {
			if (attempts.incrementAndGet() < 3) {
				throw new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT);
			}
			return "success";
		});

		assertThat(response).isEqualTo("success");
		assertThat(attempts).hasValue(3);
	}

	private TourismResourceDemandOpenApiClient client() {
		return new TourismResourceDemandOpenApiClient(properties(), new ObjectMapper());
	}

	private TourismResourceDemandApiProperties properties() {
		return new TourismResourceDemandApiProperties(
				"https://apis.data.go.kr/B551011/AreaTarResDemService",
				"/areaTarSvcDemList",
				"/areaCulResDemList",
				"test-key",
				"ETC",
				"whereU",
				new TourismResourceDemandApiProperties.ServiceDemand("202509", List.of("11"), "11")
		);
	}
}
