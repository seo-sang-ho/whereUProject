package com.trip.whereU.staystrength.client;

import com.trip.whereU.staystrength.config.TourismOpenApiProperties;
import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiItem;
import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiPage;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TourismStayStrengthOpenApiClient {

	private final TourismOpenApiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public TourismStayStrengthOpenApiClient(TourismOpenApiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public TourismStayStrengthOpenApiPage fetchStayStrengthPage(String areaCode, int pageNo, int numOfRows) {
		validateProperties();
		URI uri = buildStayStrengthUri(areaCode, pageNo, numOfRows, properties.serviceKey());

		String responseBody = restClient.get()
				.uri(uri)
				.retrieve()
				.body(String.class);

		return parsePage(responseBody);
	}

	public String buildMaskedStayStrengthUri(int pageNo, int numOfRows) {
		validateProperties();
		return buildStayStrengthUri(properties.stayStrength().areaCodes().get(0), pageNo, numOfRows, "***").toString();
	}

	URI buildStayStrengthUri(String areaCode, int pageNo, int numOfRows, String serviceKey) {
		String encodedQuery = UriComponentsBuilder
				.newInstance()
				.queryParam("pageNo", pageNo)
				.queryParam("numOfRows", numOfRows)
				.queryParam("MobileOS", properties.mobileOs())
				.queryParam("MobileApp", properties.mobileApp())
				.queryParam("baseYm", properties.stayStrength().baseYm())
				.queryParam("areaCd", areaCode)
				.queryParam("tarSjrnDsIxCd", properties.stayStrength().indicatorCode())
				.queryParam("_type", "json")
				.build()
				.encode()
				.toUri()
				.getRawQuery();

		String requestUrl = properties.baseUrl()
				+ properties.stayStrengthEndpoint()
				+ "?serviceKey="
				+ serviceKey
				+ "&"
				+ encodedQuery;
		return URI.create(requestUrl);
	}

	private void validateProperties() {
		if (!StringUtils.hasText(properties.baseUrl())
				|| !StringUtils.hasText(properties.stayStrengthEndpoint())
				|| !StringUtils.hasText(properties.serviceKey())
				|| properties.stayStrength() == null
				|| !StringUtils.hasText(properties.stayStrength().baseYm())
				|| properties.stayStrength().areaCodes() == null
				|| properties.stayStrength().areaCodes().isEmpty()
				|| properties.stayStrength().areaCodes().stream().anyMatch(areaCode -> !StringUtils.hasText(areaCode))
				|| !StringUtils.hasText(properties.stayStrength().indicatorCode())) {
			throw new IllegalStateException(
					"한국관광공사 OpenAPI 설정이 필요합니다. TOURISM_OPEN_API_BASE_URL, TOURISM_OPEN_API_SERVICE_KEY, TOURISM_STAY_STRENGTH_BASE_YM, TOURISM_STAY_STRENGTH_AREA_CODES, TOURISM_STAY_STRENGTH_INDICATOR_CODE를 확인하세요."
			);
		}
	}

	TourismStayStrengthOpenApiPage parsePage(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode response = root.path("response");
			JsonNode header = response.path("header");
			String resultCode = header.path("resultCode").asText();
			if (!"0000".equals(resultCode)) {
				throw new IllegalStateException(
						"관광 체류강도 OpenAPI 오류: code=" + resultCode + ", message=" + header.path("resultMsg").asText()
				);
			}

			JsonNode body = response.path("body");
			JsonNode itemNode = body.path("items").path("item");
			List<JsonNode> itemNodes = toItemNodes(itemNode);

			List<TourismStayStrengthOpenApiItem> items = new ArrayList<>();
			for (JsonNode node : itemNodes) {
				String regionCode = buildRegionCode(node);
				String regionName = buildRegionName(node);
				String districtCode = firstText(node, "signguCd", "signguCode", "sigunguCode");
				Double stayStrength = firstDouble(
						node,
						"tarSjrnDsIxVal",
						"stayStrength",
						"stayStrengthScore",
						"demandScore",
						"demandStrength",
						"tourDemandScore",
						"tarSjrnDsIx",
						"tarSjrnDsIxVl",
						"tarSjrnDsIxValue",
						"tarSjrnDsScore",
						"touRlteTarDsIx",
						"score",
						"value"
				);

				if (!StringUtils.hasText(regionCode) || !StringUtils.hasText(regionName) || stayStrength == null) {
					continue;
				}

				items.add(new TourismStayStrengthOpenApiItem(
						regionCode,
						regionName,
						districtCode,
						stayStrength,
						firstDate(node, "referenceDate", "baseDate", "stdDate", "date", "baseYm")
				));
			}
			if (!itemNodes.isEmpty() && items.isEmpty()) {
				throw new IllegalStateException(
						"관광 체류강도 OpenAPI 응답 필드명 매핑이 필요합니다. 첫 응답 필드: " + collectFieldNames(itemNodes.get(0))
				);
			}
			return new TourismStayStrengthOpenApiPage(
					items,
					body.path("pageNo").asInt(1),
					body.path("numOfRows").asInt(items.size()),
					body.path("totalCount").asInt(items.size())
			);
		} catch (Exception exception) {
			if (exception instanceof IllegalStateException illegalStateException) {
				throw illegalStateException;
			}
			throw new IllegalStateException("관광 체류강도 OpenAPI 응답을 해석하지 못했습니다.", exception);
		}
	}

	private String buildRegionCode(JsonNode node) {
		String regionCode = firstText(node, "regionCode", "areaCode", "sigunguCode");
		if (StringUtils.hasText(regionCode)) {
			return regionCode;
		}

		String areaCode = firstText(node, "areaCd");
		String signguCode = firstText(node, "signguCd", "signguCode");
		if (StringUtils.hasText(areaCode) && StringUtils.hasText(signguCode)) {
			return areaCode + "-" + signguCode;
		}
		return areaCode;
	}

	private String buildRegionName(JsonNode node) {
		String regionName = firstText(node, "regionName", "areaName", "sigunguName");
		if (StringUtils.hasText(regionName)) {
			return regionName;
		}

		String areaName = firstText(node, "areaNm");
		String signguName = firstText(node, "signguNm", "signguName");
		if (StringUtils.hasText(areaName) && StringUtils.hasText(signguName)) {
			return areaName + " " + signguName;
		}
		return areaName;
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

	private LocalDate firstDate(JsonNode node, String... fieldNames) {
		String value = firstText(node, fieldNames);
		if (!StringUtils.hasText(value)) {
			return LocalDate.now();
		}
		for (DateTimeFormatter formatter : List.of(DateTimeFormatter.BASIC_ISO_DATE, DateTimeFormatter.ISO_LOCAL_DATE)) {
			try {
				return LocalDate.parse(value, formatter);
			} catch (DateTimeParseException ignored) {
			}
		}
		try {
			return YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyyMM")).atDay(1);
		} catch (DateTimeParseException ignored) {
		}
		return LocalDate.now();
	}

	private String collectFieldNames(JsonNode node) {
		List<String> fieldNames = new ArrayList<>();
		fieldNames.addAll(node.propertyNames());
		return String.join(", ", fieldNames);
	}
}
