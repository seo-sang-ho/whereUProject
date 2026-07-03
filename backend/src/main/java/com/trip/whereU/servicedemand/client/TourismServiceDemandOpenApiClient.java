package com.trip.whereU.servicedemand.client;

import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiItem;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiPage;
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
public class TourismServiceDemandOpenApiClient {

	private final TourismResourceDemandApiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public TourismServiceDemandOpenApiClient(
			TourismResourceDemandApiProperties properties,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public TourismServiceDemandOpenApiPage fetchPage(String areaCode, int pageNo, int numOfRows) {
		validateProperties();
		URI uri = buildUri(areaCode, pageNo, numOfRows, properties.serviceKey());
		String responseBody = restClient.get().uri(uri).retrieve().body(String.class);
		return parsePage(responseBody);
	}

	URI buildUri(String areaCode, int pageNo, int numOfRows, String serviceKey) {
		String encodedQuery = UriComponentsBuilder
				.newInstance()
				.queryParam("pageNo", pageNo)
				.queryParam("numOfRows", numOfRows)
				.queryParam("MobileOS", properties.mobileOs())
				.queryParam("MobileApp", properties.mobileApp())
				.queryParam("baseYm", properties.serviceDemand().baseYm())
				.queryParam("areaCd", areaCode)
				.queryParam("tarSvcDemIxCd", properties.serviceDemand().indicatorCode())
				.queryParam("_type", "json")
				.build()
				.encode()
				.toUri()
				.getRawQuery();

		return URI.create(
				properties.baseUrl()
						+ properties.serviceDemandEndpoint()
						+ "?serviceKey="
						+ serviceKey
						+ "&"
						+ encodedQuery
		);
	}

	private void validateProperties() {
		if (!StringUtils.hasText(properties.baseUrl())
				|| !StringUtils.hasText(properties.serviceDemandEndpoint())
				|| !StringUtils.hasText(properties.serviceKey())
				|| properties.serviceDemand() == null
				|| !StringUtils.hasText(properties.serviceDemand().baseYm())
				|| properties.serviceDemand().areaCodes() == null
				|| properties.serviceDemand().areaCodes().isEmpty()
				|| properties.serviceDemand().areaCodes().stream().anyMatch(code -> !StringUtils.hasText(code))
				|| !StringUtils.hasText(properties.serviceDemand().indicatorCode())) {
			throw new IllegalStateException(
					"관광 서비스 수요 OpenAPI 설정이 필요합니다. TOURISM_RESOURCE_DEMAND_BASE_URL, TOURISM_OPEN_API_SERVICE_KEY, TOURISM_SERVICE_DEMAND_BASE_YM, TOURISM_SERVICE_DEMAND_AREA_CODES, TOURISM_SERVICE_DEMAND_INDICATOR_CODE를 확인하세요."
			);
		}
	}

	TourismServiceDemandOpenApiPage parsePage(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode response = root.path("response");
			JsonNode header = response.path("header");
			String resultCode = header.path("resultCode").asText();
			if (!"0000".equals(resultCode)) {
				throw new IllegalStateException(
						"관광 서비스 수요 OpenAPI 오류: code=" + resultCode
								+ ", message=" + header.path("resultMsg").asText()
				);
			}

			JsonNode body = response.path("body");
			List<JsonNode> itemNodes = toItemNodes(body.path("items").path("item"));
			List<TourismServiceDemandOpenApiItem> items = new ArrayList<>();
			for (JsonNode node : itemNodes) {
				String areaCode = firstText(node, "areaCd");
				String districtCode = firstText(node, "signguCd", "signguCode", "sigunguCode");
				String areaName = firstText(node, "areaNm");
				String districtName = firstText(node, "signguNm", "signguName", "sigunguName");
				Double serviceDemand = firstDouble(node, "tarSvcDemIxVal", "serviceDemand", "value");
				if (!StringUtils.hasText(areaCode)
						|| !StringUtils.hasText(districtCode)
						|| !StringUtils.hasText(areaName)
						|| !StringUtils.hasText(districtName)
						|| serviceDemand == null) {
					continue;
				}

				items.add(new TourismServiceDemandOpenApiItem(
						areaCode + "-" + districtCode,
						areaName + " " + districtName,
						districtCode,
						serviceDemand,
						parseReferenceDate(firstText(node, "baseYm"))
				));
			}

			if (!itemNodes.isEmpty() && items.isEmpty()) {
				throw new IllegalStateException("관광 서비스 수요 OpenAPI 응답 필드 매핑을 확인하세요.");
			}
			return new TourismServiceDemandOpenApiPage(
					items,
					body.path("pageNo").asInt(1),
					body.path("numOfRows").asInt(items.size()),
					body.path("totalCount").asInt(items.size())
			);
		} catch (Exception exception) {
			if (exception instanceof IllegalStateException illegalStateException) {
				throw illegalStateException;
			}
			throw new IllegalStateException("관광 서비스 수요 OpenAPI 응답을 해석하지 못했습니다.", exception);
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

	private LocalDate parseReferenceDate(String baseYm) {
		if (!StringUtils.hasText(baseYm)) {
			return LocalDate.now();
		}
		try {
			return YearMonth.parse(baseYm, DateTimeFormatter.ofPattern("yyyyMM")).atDay(1);
		} catch (DateTimeParseException ignored) {
			return LocalDate.now();
		}
	}
}
