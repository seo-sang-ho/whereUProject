package com.trip.whereU.tourism.client;

import com.trip.whereU.tourism.config.TourismContentApiProperties;
import com.trip.whereU.tourism.dto.TourismContentOpenApiItem;
import com.trip.whereU.tourism.dto.TourismContentOpenApiPage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TourismContentOpenApiClient {

	private static final Logger log = LoggerFactory.getLogger(TourismContentOpenApiClient.class);

	private final TourismContentApiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public TourismContentOpenApiClient(TourismContentApiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public TourismContentOpenApiPage fetchAreaBasedPage(
			String contentTypeId,
			String arrange,
			String legalDongRegionCode,
			String legalDongSigunguCode,
			String categoryLevel1,
			String categoryLevel2,
			String categoryLevel3,
			int pageNo,
			int numOfRows
	) {
		validateProperties();
		URI uri = buildUri(
				contentTypeId,
				arrange,
				legalDongRegionCode,
				legalDongSigunguCode,
				categoryLevel1,
				categoryLevel2,
				categoryLevel3,
				pageNo,
				numOfRows,
				properties.serviceKey()
		);
		String responseBody = restClient.get()
				.uri(uri)
				.retrieve()
				.body(String.class);
		return parsePage(responseBody);
	}

	URI buildUri(
			String contentTypeId,
			String arrange,
			String legalDongRegionCode,
			String legalDongSigunguCode,
			String categoryLevel1,
			String categoryLevel2,
			String categoryLevel3,
			int pageNo,
			int numOfRows,
			String serviceKey
	) {
		UriComponentsBuilder queryBuilder = UriComponentsBuilder.newInstance()
				.queryParam("pageNo", pageNo)
				.queryParam("numOfRows", numOfRows)
				.queryParam("MobileOS", properties.mobileOs())
				.queryParam("MobileApp", properties.mobileApp())
				.queryParam("_type", "json");
		addTextQueryParam(queryBuilder, "contentTypeId", contentTypeId);
		addTextQueryParam(queryBuilder, "arrange", arrange);
		addTextQueryParam(queryBuilder, "lDongRegnCd", legalDongRegionCode);
		addTextQueryParam(queryBuilder, "lDongSignguCd", legalDongSigunguCode);
		addTextQueryParam(queryBuilder, "lclsSystm1", categoryLevel1);
		addTextQueryParam(queryBuilder, "lclsSystm2", categoryLevel2);
		addTextQueryParam(queryBuilder, "lclsSystm3", categoryLevel3);

		String encodedQuery = queryBuilder
				.build()
				.encode()
				.toUri()
				.getRawQuery();

		return URI.create(
				properties.baseUrl()
						+ properties.areaBasedListEndpoint()
						+ "?serviceKey="
						+ serviceKey
						+ "&"
						+ encodedQuery
		);
	}

	TourismContentOpenApiPage parsePage(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode response = root.path("response");
			JsonNode header = response.path("header");
			String resultCode = header.path("resultCode").asText();
			if (!"0000".equals(resultCode)) {
				throw new IllegalStateException(
						"TourAPI 지역기반 관광정보 오류: code="
								+ resultCode
								+ ", message="
								+ header.path("resultMsg").asText()
				);
			}

			JsonNode body = response.path("body");
			List<JsonNode> itemNodes = toItemNodes(body.path("items").path("item"));
			if (!itemNodes.isEmpty()) {
				log.info("TourAPI 지역기반 관광정보 첫 응답 item 필드: {}", collectFieldNames(itemNodes.get(0)));
			}

			List<TourismContentOpenApiItem> items = new ArrayList<>();
			for (JsonNode node : itemNodes) {
				TourismContentOpenApiItem item = toItem(node);
				if (item != null) {
					items.add(item);
				}
			}

			if (!itemNodes.isEmpty() && items.isEmpty()) {
				throw new IllegalStateException(
						"TourAPI 지역기반 관광정보 응답 필드 매핑을 확인하세요. 첫 응답 필드: "
								+ collectFieldNames(itemNodes.get(0))
				);
			}
			return new TourismContentOpenApiPage(
					items,
					body.path("pageNo").asInt(1),
					body.path("numOfRows").asInt(items.size()),
					body.path("totalCount").asInt(items.size())
			);
		} catch (Exception exception) {
			if (exception instanceof IllegalStateException illegalStateException) {
				throw illegalStateException;
			}
			throw new IllegalStateException("TourAPI 지역기반 관광정보 응답을 해석하지 못했습니다.", exception);
		}
	}

	private TourismContentOpenApiItem toItem(JsonNode node) {
		String contentId = firstText(node, "contentid", "contentId");
		String title = firstText(node, "title");
		Double longitude = firstDouble(node, "mapx", "longitude");
		Double latitude = firstDouble(node, "mapy", "latitude");
		if (!StringUtils.hasText(contentId) || !StringUtils.hasText(title)) {
			return null;
		}

		String cat1 = firstText(node, "cat1");
		String cat2 = firstText(node, "cat2");
		String cat3 = firstText(node, "cat3");
		String legalRegionCode = buildJoinedCode(
				firstText(node, "lDongRegnCd", "ldongRegnCd", "legalDongRegionCode"),
				firstText(node, "lDongSignguCd", "ldongSignguCd", "legalDongSigunguCode")
		);
		String categoryCode = firstText(
				node,
				"lclsSystm3",
				"lclsSystm2",
				"lclsSystm1",
				"classificationCode",
				"categoryCode"
		);
		if (!StringUtils.hasText(categoryCode)) {
			categoryCode = firstText(node, "cat3", "cat2", "cat1");
		}

		return new TourismContentOpenApiItem(
				contentId,
				firstText(node, "contenttypeid", "contentTypeId"),
				title,
				firstText(node, "addr1", "address"),
				firstText(node, "addr2", "detailAddress"),
				latitude,
				longitude,
				firstText(node, "areacode", "areaCode"),
				firstText(node, "sigungucode", "sigunguCode"),
				legalRegionCode,
				categoryCode,
				cat1,
				cat2,
				cat3,
				firstText(node, "firstimage", "firstImage"),
				firstText(node, "firstimage2", "firstImage2"),
				firstText(node, "tel"),
				firstText(node, "zipcode", "zipCode"),
				firstText(node, "modifiedtime", "modifiedTime")
		);
	}

	private void validateProperties() {
		if (!StringUtils.hasText(properties.baseUrl())
				|| !StringUtils.hasText(properties.areaBasedListEndpoint())
				|| !StringUtils.hasText(properties.serviceKey())
				|| !StringUtils.hasText(properties.mobileOs())
				|| !StringUtils.hasText(properties.mobileApp())) {
			throw new IllegalStateException(
					"TourAPI 설정이 필요합니다. TOURISM_CONTENT_API_BASE_URL, TOURISM_CONTENT_API_SERVICE_KEY를 확인하세요."
			);
		}
	}

	private void addTextQueryParam(UriComponentsBuilder builder, String name, String value) {
		if (StringUtils.hasText(value)) {
			builder.queryParam(name, value);
		}
	}

	private List<JsonNode> toItemNodes(JsonNode itemNode) {
		List<JsonNode> nodes = new ArrayList<>();
		if (itemNode.isArray()) {
			itemNode.forEach(nodes::add);
		} else if (itemNode.isObject()) {
			nodes.add(itemNode);
		}
		return nodes;
	}

	private String firstText(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (!value.isMissingNode() && StringUtils.hasText(value.asText())) {
				return value.asText();
			}
		}
		return null;
	}

	private Double firstDouble(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			JsonNode value = node.path(fieldName);
			if (value.isNumber()) {
				return value.asDouble();
			}
			if (!value.isMissingNode() && StringUtils.hasText(value.asText())) {
				try {
					return Double.parseDouble(value.asText());
				} catch (NumberFormatException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	private String buildJoinedCode(String first, String second) {
		if (StringUtils.hasText(first) && StringUtils.hasText(second)) {
			return first + "-" + second;
		}
		if (StringUtils.hasText(first)) {
			return first;
		}
		return second;
	}

	private String collectFieldNames(JsonNode node) {
		List<String> fieldNames = new ArrayList<>();
		fieldNames.addAll(node.propertyNames());
		return String.join(", ", fieldNames);
	}
}
