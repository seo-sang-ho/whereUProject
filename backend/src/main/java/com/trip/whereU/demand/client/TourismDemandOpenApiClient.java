package com.trip.whereU.demand.client;

import com.trip.whereU.demand.config.TourismOpenApiProperties;
import com.trip.whereU.demand.dto.TourismDemandOpenApiItem;
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
public class TourismDemandOpenApiClient {

	private final TourismOpenApiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public TourismDemandOpenApiClient(TourismOpenApiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public List<TourismDemandOpenApiItem> fetchDemandItems(int pageNo, int numOfRows) {
		validateProperties();
		URI uri = buildDemandUri(pageNo, numOfRows, properties.serviceKey());

		String responseBody = restClient.get()
				.uri(uri)
				.retrieve()
				.body(String.class);

		return parseItems(responseBody);
	}

	public String buildMaskedDemandUri(int pageNo, int numOfRows) {
		validateProperties();
		return buildDemandUri(pageNo, numOfRows, "***").toString();
	}

	URI buildDemandUri(int pageNo, int numOfRows, String serviceKey) {
		String encodedQuery = UriComponentsBuilder
				.newInstance()
				.queryParam("pageNo", pageNo)
				.queryParam("numOfRows", numOfRows)
				.queryParam("MobileOS", properties.mobileOs())
				.queryParam("MobileApp", properties.mobileApp())
				.queryParam("baseYm", properties.demand().baseYm())
				.queryParam("areaCd", properties.demand().areaCd())
				.queryParam("signguCd", properties.demand().signguCd())
				.queryParam("tarSjrnDsIxCd", properties.demand().indicatorCode())
				.queryParam("_type", "json")
				.build()
				.encode()
				.toUri()
				.getRawQuery();

		String requestUrl = properties.baseUrl()
				+ properties.demandEndpoint()
				+ "?serviceKey="
				+ serviceKey
				+ "&"
				+ encodedQuery;
		return URI.create(requestUrl);
	}

	private void validateProperties() {
		if (!StringUtils.hasText(properties.baseUrl())
				|| !StringUtils.hasText(properties.demandEndpoint())
				|| !StringUtils.hasText(properties.serviceKey())
				|| properties.demand() == null
				|| !StringUtils.hasText(properties.demand().baseYm())
				|| !StringUtils.hasText(properties.demand().areaCd())
				|| !StringUtils.hasText(properties.demand().signguCd())
				|| !StringUtils.hasText(properties.demand().indicatorCode())) {
			throw new IllegalStateException(
					"한국관광공사 OpenAPI 설정이 필요합니다. TOURISM_OPEN_API_BASE_URL, TOURISM_OPEN_API_SERVICE_KEY, TOURISM_DEMAND_BASE_YM, TOURISM_DEMAND_AREA_CD, TOURISM_DEMAND_SIGNGU_CD, TOURISM_DEMAND_INDICATOR_CODE를 확인하세요."
			);
		}
	}

	private List<TourismDemandOpenApiItem> parseItems(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode itemNode = root.path("response").path("body").path("items").path("item");
			List<JsonNode> itemNodes = toItemNodes(itemNode);

			List<TourismDemandOpenApiItem> items = new ArrayList<>();
			for (JsonNode node : itemNodes) {
				String regionCode = buildRegionCode(node);
				String regionName = buildRegionName(node);
				Double demandScore = firstDouble(
						node,
                        "tarSjrnDsIxVal",
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

				if (!StringUtils.hasText(regionCode) || !StringUtils.hasText(regionName) || demandScore == null) {
					continue;
				}

				items.add(new TourismDemandOpenApiItem(
						regionCode,
						regionName,
						demandScore,
						firstDouble(node, "latitude", "lat", "mapY"),
						firstDouble(node, "longitude", "lng", "lon", "mapX"),
						firstDate(node, "referenceDate", "baseDate", "stdDate", "date", "baseYm")
				));
			}
			if (!itemNodes.isEmpty() && items.isEmpty()) {
				throw new IllegalStateException(
						"관광 수요 강도 OpenAPI 응답 필드명 매핑이 필요합니다. 첫 응답 필드: " + collectFieldNames(itemNodes.get(0))
				);
			}
			return items;
		} catch (Exception exception) {
			if (exception instanceof IllegalStateException illegalStateException) {
				throw illegalStateException;
			}
			throw new IllegalStateException("관광 수요 강도 OpenAPI 응답을 해석하지 못했습니다.", exception);
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
