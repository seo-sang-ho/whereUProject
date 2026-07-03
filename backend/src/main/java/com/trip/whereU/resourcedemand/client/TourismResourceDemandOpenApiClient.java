package com.trip.whereU.resourcedemand.client;

import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiItem;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiPage;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TourismResourceDemandOpenApiClient {
	private static final int MAX_ATTEMPTS = 3;
	private static final long INITIAL_RETRY_DELAY_MILLIS = 300;

	private final TourismResourceDemandApiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public TourismResourceDemandOpenApiClient(
			TourismResourceDemandApiProperties properties,
			ObjectMapper objectMapper
	) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.create();
	}

	public TourismResourceDemandOpenApiPage fetchPage(
			ResourceDemandType resourceType,
			String areaCode,
			String indicatorCode,
			int pageNo,
			int numOfRows
	) {
		validateProperties();
		URI uri = buildUri(
				resourceType, areaCode, indicatorCode, pageNo, numOfRows, properties.serviceKey()
		);
		String responseBody = executeWithRetry(
				() -> restClient.get().uri(uri).retrieve().body(String.class)
		);
		return parsePage(resourceType, responseBody);
	}

	String executeWithRetry(Supplier<String> request) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return request.get();
			} catch (RestClientResponseException exception) {
				if (!isRetryable(exception) || attempt == MAX_ATTEMPTS) {
					throw exception;
				}
				waitBeforeRetry(attempt);
			}
		}
		throw new IllegalStateException("관광 자원 수요 OpenAPI 재시도 상태를 확인하세요.");
	}

	private boolean isRetryable(RestClientResponseException exception) {
		int statusCode = exception.getStatusCode().value();
		return statusCode == 502 || statusCode == 503 || statusCode == 504;
	}

	private void waitBeforeRetry(int attempt) {
		try {
			Thread.sleep(INITIAL_RETRY_DELAY_MILLIS * attempt);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("관광 자원 수요 OpenAPI 재시도가 중단되었습니다.", exception);
		}
	}

	URI buildUri(
			ResourceDemandType resourceType,
			String areaCode,
			String indicatorCode,
			int pageNo,
			int numOfRows,
			String serviceKey
	) {
		String encodedQuery = UriComponentsBuilder
				.newInstance()
				.queryParam("pageNo", pageNo)
				.queryParam("numOfRows", numOfRows)
				.queryParam("MobileOS", properties.mobileOs())
				.queryParam("MobileApp", properties.mobileApp())
				.queryParam("baseYm", properties.serviceDemand().baseYm())
				.queryParam("areaCd", areaCode)
				.queryParam(indicatorParameter(resourceType), indicatorCode)
				.queryParam("_type", "json")
				.build()
				.encode()
				.toUri()
				.getRawQuery();

		return URI.create(
				properties.baseUrl()
						+ endpoint(resourceType)
						+ "?serviceKey="
						+ serviceKey
						+ "&"
						+ encodedQuery
		);
	}

	TourismResourceDemandOpenApiPage parsePage(
			ResourceDemandType resourceType,
			String responseBody
	) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode response = root.path("response");
			JsonNode header = response.path("header");
			String resultCode = header.path("resultCode").asText();
			if (!"0000".equals(resultCode)) {
				throw new IllegalStateException(
						resourceType.getLabel() + " 수요 OpenAPI 오류: code=" + resultCode
								+ ", message=" + header.path("resultMsg").asText()
				);
			}

			JsonNode body = response.path("body");
			List<JsonNode> itemNodes = toItemNodes(body.path("items").path("item"));
			List<TourismResourceDemandOpenApiItem> items = new ArrayList<>();
			for (JsonNode node : itemNodes) {
				TourismResourceDemandOpenApiItem item = toItem(resourceType, node);
				if (item != null) {
					items.add(item);
				}
			}

			if (!itemNodes.isEmpty() && items.isEmpty()) {
				throw new IllegalStateException(resourceType.getLabel() + " 수요 OpenAPI 응답 필드 매핑을 확인하세요.");
			}
			return new TourismResourceDemandOpenApiPage(
					items,
					body.path("pageNo").asInt(1),
					body.path("numOfRows").asInt(items.size()),
					body.path("totalCount").asInt(items.size())
			);
		} catch (Exception exception) {
			if (exception instanceof IllegalStateException illegalStateException) {
				throw illegalStateException;
			}
			throw new IllegalStateException(resourceType.getLabel() + " 수요 OpenAPI 응답을 해석하지 못했습니다.", exception);
		}
	}

	private TourismResourceDemandOpenApiItem toItem(
			ResourceDemandType resourceType,
			JsonNode node
	) {
		String areaCode = firstText(node, "areaCd");
		String districtCode = firstText(node, "signguCd", "signguCode", "sigunguCode");
		String areaName = firstText(node, "areaNm");
		String districtName = firstText(node, "signguNm", "signguName", "sigunguName");
		String indicatorCode = resourceType == ResourceDemandType.SERVICE
				? firstText(node, "tarSvcDemIxCd")
				: firstText(node, "culResDemIxCd");
		String indicatorName = resourceType == ResourceDemandType.SERVICE
				? firstText(node, "tarSvcDemIxNm")
				: firstText(node, "culResDemIxNm");
		Double value = resourceType == ResourceDemandType.SERVICE
				? firstDouble(node, "tarSvcDemIxVal")
				: firstDouble(node, "culResDemIxVal");

		if (!StringUtils.hasText(areaCode)
				|| !StringUtils.hasText(districtCode)
				|| !StringUtils.hasText(areaName)
				|| !StringUtils.hasText(districtName)
				|| !StringUtils.hasText(indicatorCode)
				|| !StringUtils.hasText(indicatorName)
				|| value == null) {
			return null;
		}

		return new TourismResourceDemandOpenApiItem(
				areaCode + "-" + districtCode,
				areaName + " " + districtName,
				districtCode,
				resourceType,
				indicatorCode,
				indicatorName,
				value,
				parseReferenceDate(firstText(node, "baseYm"))
		);
	}

	private String endpoint(ResourceDemandType resourceType) {
		return resourceType == ResourceDemandType.SERVICE
				? properties.serviceDemandEndpoint()
				: properties.culturalResourceDemandEndpoint();
	}

	private String indicatorParameter(ResourceDemandType resourceType) {
		return resourceType == ResourceDemandType.SERVICE
				? "tarSvcDemIxCd"
				: "culResDemIxCd";
	}

	private void validateProperties() {
		if (!StringUtils.hasText(properties.baseUrl())
				|| !StringUtils.hasText(properties.serviceDemandEndpoint())
				|| !StringUtils.hasText(properties.culturalResourceDemandEndpoint())
				|| !StringUtils.hasText(properties.serviceKey())
				|| properties.serviceDemand() == null
				|| !StringUtils.hasText(properties.serviceDemand().baseYm())
				|| properties.serviceDemand().areaCodes() == null
				|| properties.serviceDemand().areaCodes().isEmpty()) {
			throw new IllegalStateException(
					"관광 자원 수요 OpenAPI 설정이 필요합니다. Base URL, endpoint, serviceKey, 기준월, 지역 코드를 확인하세요."
			);
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
