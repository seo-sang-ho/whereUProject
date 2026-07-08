package com.trip.whereU.recommendation.service;

import com.trip.whereU.recommendation.dto.RecommendedTourismContentResponse;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.entity.TourismRegionTourApiMapping;
import com.trip.whereU.tourism.repository.TourismContentRepository;
import com.trip.whereU.tourism.repository.TourismRegionTourApiMappingRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationTourismContentService {

	private static final String DEFAULT_CONTENT_TYPE_ID = "12";

	private final TourismRegionTourApiMappingRepository mappingRepository;
	private final TourismContentRepository tourismContentRepository;

	public RecommendationTourismContentService(
			TourismRegionTourApiMappingRepository mappingRepository,
			TourismContentRepository tourismContentRepository
	) {
		this.mappingRepository = mappingRepository;
		this.tourismContentRepository = tourismContentRepository;
	}

	@Transactional(readOnly = true)
	public Map<String, List<RecommendedTourismContentResponse>> findContentsByRegionCodes(
			List<String> regionCodes,
			int limitPerRegion
	) {
		if (regionCodes == null || regionCodes.isEmpty() || limitPerRegion < 1) {
			return Map.of();
		}

		List<String> distinctRegionCodes = regionCodes.stream()
				.filter(this::hasText)
				.distinct()
				.toList();
		if (distinctRegionCodes.isEmpty()) {
			return Map.of();
		}

		Map<String, MappingRule> mappingByRegion = findMappingRules(distinctRegionCodes);
		List<String> legalDongCodes = mappingByRegion.values().stream()
				.map(MappingRule::legalDongCode)
				.distinct()
				.toList();
		if (legalDongCodes.isEmpty()) {
			return Map.of();
		}

		Map<String, List<TourismContent>> contentByLegalDong = tourismContentRepository
				.findByLegalDongCodeIn(legalDongCodes)
				.stream()
				.collect(Collectors.groupingBy(TourismContent::getLegalDongCode));

		Map<String, List<RecommendedTourismContentResponse>> result = new LinkedHashMap<>();
		for (String regionCode : distinctRegionCodes) {
			MappingRule mapping = mappingByRegion.get(regionCode);
			if (mapping == null) {
				continue;
			}
			List<RecommendedTourismContentResponse> contents = contentByLegalDong
					.getOrDefault(mapping.legalDongCode(), List.of())
					.stream()
					.filter(content -> matches(content, mapping))
					.sorted(Comparator
							.comparing(this::hasAnyImage).reversed()
							.thenComparing(TourismContent::getModifiedTime, Comparator.nullsLast(Comparator.reverseOrder()))
							.thenComparing(TourismContent::getContentId))
					.limit(limitPerRegion)
					.map(RecommendedTourismContentResponse::from)
					.toList();
			result.put(regionCode, contents);
		}
		return result;
	}

	private Map<String, MappingRule> findMappingRules(List<String> regionCodes) {
		Map<String, MappingRule> mappingByRegion = mappingRepository
				.findByEnabledTrueAndRegionCodeIn(regionCodes)
				.stream()
				.collect(Collectors.toMap(
						TourismRegionTourApiMapping::getRegionCode,
						MappingRule::from,
						(first, ignored) -> first,
						HashMap::new
				));

		for (String regionCode : regionCodes) {
			if (mappingByRegion.containsKey(regionCode)) {
				continue;
			}
			deriveLegalDongCode(regionCode)
					.map(legalDongCode -> MappingRule.defaultRule(regionCode, legalDongCode))
					.ifPresent(mapping -> mappingByRegion.put(regionCode, mapping));
		}
		return mappingByRegion;
	}

	private Optional<String> deriveLegalDongCode(String regionCode) {
		String[] parts = regionCode.split("-");
		if (parts.length != 2 || parts[0].length() != 2 || parts[1].length() < 3) {
			return Optional.empty();
		}
		String sigunguCode = parts[1];
		return Optional.of(parts[0] + "-" + sigunguCode.substring(sigunguCode.length() - 3));
	}

	private boolean matches(TourismContent content, MappingRule mapping) {
		return matchesValue(content.getContentTypeId(), mapping.contentTypeId())
				&& matchesValue(content.getCategoryCode(), mapping.categoryCode());
	}

	private boolean matchesValue(String actualValue, String expectedValue) {
		return !hasText(expectedValue) || expectedValue.equals(actualValue);
	}

	private boolean hasAnyImage(TourismContent content) {
		return hasText(content.getFirstImage()) || hasText(content.getFirstImage2());
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private record MappingRule(
			String regionCode,
			String legalDongCode,
			String contentTypeId,
			String categoryCode
	) {

		private static MappingRule from(TourismRegionTourApiMapping mapping) {
			return new MappingRule(
					mapping.getRegionCode(),
					mapping.getLegalDongCode(),
					defaultIfBlank(mapping.getDefaultContentTypeId(), DEFAULT_CONTENT_TYPE_ID),
					mapping.getDefaultCategoryCode()
			);
		}

		private static MappingRule defaultRule(String regionCode, String legalDongCode) {
			return new MappingRule(regionCode, legalDongCode, DEFAULT_CONTENT_TYPE_ID, null);
		}

		private static String defaultIfBlank(String value, String defaultValue) {
			return value == null || value.isBlank() ? defaultValue : value;
		}
	}
}
