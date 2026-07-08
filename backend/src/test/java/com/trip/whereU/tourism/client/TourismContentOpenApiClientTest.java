package com.trip.whereU.tourism.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trip.whereU.tourism.config.TourismContentApiProperties;
import com.trip.whereU.tourism.dto.TourismContentOpenApiPage;
import java.net.URI;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TourismContentOpenApiClientTest {

	@Test
	void keepsEncodedServiceKeyAndBuildsAreaBasedListParameters() {
		TourismContentOpenApiClient client = new TourismContentOpenApiClient(properties(), null);

		URI uri = client.buildUri(
				"12",
				"C",
				"26",
				"380",
				"NA",
				"NA04",
				"NA040500",
				1,
				100,
				"Gfs%2Babc%2Fdef%3D%3D"
		);

		assertThat(uri.toString())
				.contains("/areaBasedList2?serviceKey=Gfs%2Babc%2Fdef%3D%3D&")
				.contains("MobileOS=ETC")
				.contains("MobileApp=whereU")
				.contains("contentTypeId=12")
				.contains("arrange=C")
				.contains("lDongRegnCd=26")
				.contains("lDongSignguCd=380")
				.contains("lclsSystm1=NA")
				.contains("lclsSystm2=NA04")
				.contains("lclsSystm3=NA040500")
				.contains("_type=json")
				.doesNotContain("%252B");
	}

	@Test
	void parsesAreaBasedItemsWithLegalDongAndCategoryFallbackFields() {
		TourismContentOpenApiClient client = new TourismContentOpenApiClient(
				properties(),
				new ObjectMapper()
		);
		String responseBody = """
				{
				  "response": {
				    "header": {"resultCode": "0000", "resultMsg": "OK"},
				    "body": {
				      "items": {"item": [{
				        "contentid": "126508",
				        "contenttypeid": "12",
				        "title": "경복궁",
				        "addr1": "서울특별시 종로구 사직로 161",
				        "addr2": "(세종로)",
				        "mapx": "126.976993",
				        "mapy": "37.578822",
				        "areacode": "",
				        "sigungucode": "",
				        "lDongRegnCd": "11",
				        "lDongSignguCd": "11110",
				        "lclsSystm1": "VE",
				        "lclsSystm2": "VE01",
				        "lclsSystm3": "VE010100",
				        "firstimage": "https://example.com/main.jpg",
				        "firstimage2": "https://example.com/thumb.jpg",
				        "tel": "02-3700-3900",
				        "zipcode": "03045",
				        "modifiedtime": "20260708103000"
				      }]},
				      "pageNo": 1, "numOfRows": 100, "totalCount": 1
				    }
				  }
				}
				""";

		TourismContentOpenApiPage page = client.parsePage(responseBody);

		assertThat(page.totalCount()).isEqualTo(1);
		assertThat(page.items()).hasSize(1);
		assertThat(page.items().getFirst().contentId()).isEqualTo("126508");
		assertThat(page.items().getFirst().title()).isEqualTo("경복궁");
		assertThat(page.items().getFirst().latitude()).isEqualTo(37.578822);
		assertThat(page.items().getFirst().longitude()).isEqualTo(126.976993);
		assertThat(page.items().getFirst().legalDongCode()).isEqualTo("11-11110");
		assertThat(page.items().getFirst().categoryCode()).isEqualTo("VE010100");
	}

	@Test
	void reportsFirstItemFieldNamesWhenRequiredMappingFails() {
		TourismContentOpenApiClient client = new TourismContentOpenApiClient(
				properties(),
				new ObjectMapper()
		);
		String responseBody = """
				{
				  "response": {
				    "header": {"resultCode": "0000", "resultMsg": "OK"},
				    "body": {
				      "items": {"item": {"unknownId": "1", "unknownTitle": "이름"}},
				      "pageNo": 1, "numOfRows": 10, "totalCount": 1
				    }
				  }
				}
				""";

		assertThatThrownBy(() -> client.parsePage(responseBody))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("TourAPI 지역기반 관광정보 응답 필드 매핑을 확인하세요.")
				.hasMessageContaining("unknownId")
				.hasMessageContaining("unknownTitle");
	}

	private TourismContentApiProperties properties() {
		return new TourismContentApiProperties(
				"https://apis.data.go.kr/B551011/KorService2",
				"/areaBasedList2",
				"test-key",
				"ETC",
				"whereU"
		);
	}
}
