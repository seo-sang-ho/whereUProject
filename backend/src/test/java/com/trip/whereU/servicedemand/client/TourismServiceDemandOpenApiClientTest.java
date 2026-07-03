package com.trip.whereU.servicedemand.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiPage;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TourismServiceDemandOpenApiClientTest {

	@Test
	void keepsEncodedServiceKeyAndBuildsServiceDemandParameters() {
		TourismServiceDemandOpenApiClient client = new TourismServiceDemandOpenApiClient(properties(), null);

		URI uri = client.buildUri("11", 1, 100, "Gfs%2Babc%2Fdef%3D%3D");

		assertThat(uri.toString())
				.contains("/areaTarSvcDemList?serviceKey=Gfs%2Babc%2Fdef%3D%3D&")
				.contains("baseYm=202509")
				.contains("areaCd=11")
				.contains("tarSvcDemIxCd=11")
				.doesNotContain("%252B");
	}

	@Test
	void parsesAggregateAndDistrictItems() {
		TourismServiceDemandOpenApiClient client = new TourismServiceDemandOpenApiClient(
				properties(),
				new ObjectMapper()
		);
		String responseBody = """
				{
				  "response": {
				    "header": {"resultCode": "0000", "resultMsg": "OK"},
				    "body": {
				      "items": {"item": [
				        {"baseYm":"202509","areaCd":"11","areaNm":"서울특별시","signguCd":"0","signguNm":"_","tarSvcDemIxVal":"90.1"},
				        {"baseYm":"202509","areaCd":"11","areaNm":"서울특별시","signguCd":"11110","signguNm":"종로구","tarSvcDemIxVal":"82.5"}
				      ]},
				      "pageNo": 1, "numOfRows": 100, "totalCount": 2
				    }
				  }
				}
				""";

		TourismServiceDemandOpenApiPage page = client.parsePage(responseBody);

		assertThat(page.totalCount()).isEqualTo(2);
		assertThat(page.items()).hasSize(2);
		assertThat(page.items().get(0).isAreaAggregate()).isTrue();
		assertThat(page.items().get(1).regionCode()).isEqualTo("11-11110");
		assertThat(page.items().get(1).regionName()).isEqualTo("서울특별시 종로구");
		assertThat(page.items().get(1).serviceDemand()).isEqualTo(82.5);
		assertThat(page.items().get(1).referenceDate()).isEqualTo(LocalDate.of(2025, 9, 1));
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
