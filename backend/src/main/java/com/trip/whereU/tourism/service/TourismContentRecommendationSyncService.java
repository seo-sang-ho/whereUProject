package com.trip.whereU.tourism.service;

import com.trip.whereU.recommendation.dto.ValueRecommendationItemResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationResponse;
import com.trip.whereU.recommendation.service.ValueRecommendationService;
import com.trip.whereU.tourism.dto.TourismContentRecommendationTopSyncResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.entity.TourismRegionTourApiMapping;
import com.trip.whereU.tourism.repository.TourismRegionTourApiMappingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TourismContentRecommendationSyncService {

	private static final String DEFAULT_CONTENT_TYPE_ID = "12";
	private static final String DEFAULT_ARRANGE = "C";

	private final ValueRecommendationService valueRecommendationService;
	private final TourismContentService tourismContentService;
	private final TourismRegionTourApiMappingRepository mappingRepository;

	public TourismContentRecommendationSyncService(
			ValueRecommendationService valueRecommendationService,
			TourismContentService tourismContentService,
			TourismRegionTourApiMappingRepository mappingRepository
	) {
		this.valueRecommendationService = valueRecommendationService;
		this.tourismContentService = tourismContentService;
		this.mappingRepository = mappingRepository;
	}

	public TourismContentRecommendationTopSyncResponse syncValueRecommendationTopRegions(
			String contentTypeId,
			String arrange,
			String categoryLevel1,
			String categoryLevel2,
			String categoryLevel3,
			int limit,
			int pageSize
	) {
		ValueRecommendationResponse recommendations = valueRecommendationService.getLatestValueRecommendations(limit);
		Map<String, SyncRule> syncRuleByRegion = findSyncRules(recommendations.recommendations());
		List<TourismContentRecommendationTopSyncResponse.RegionResult> results = new ArrayList<>();
		for (ValueRecommendationItemResponse recommendation : recommendations.recommendations()) {
			SyncRule syncRule = syncRuleByRegion.get(recommendation.regionCode());
			if (syncRule == null) {
				results.add(TourismContentRecommendationTopSyncResponse.RegionResult.failure(
						recommendation.rank(),
						recommendation.regionCode(),
						recommendation.regionName(),
						null,
						"추천 지역 코드를 TourAPI 법정동 코드로 변환할 수 없습니다."
				));
				continue;
			}

			try {
				TourismContentSyncResponse syncResponse = tourismContentService.sync(
						defaultIfBlank(contentTypeId, syncRule.contentTypeId()),
						defaultIfBlank(arrange, DEFAULT_ARRANGE),
						syncRule.legalDongCode().regionCode(),
						syncRule.legalDongCode().sigunguCode(),
						categoryLevel1,
						categoryLevel2,
						defaultIfBlank(categoryLevel3, syncRule.categoryCode()),
						pageSize
				);
				results.add(TourismContentRecommendationTopSyncResponse.RegionResult.success(
						recommendation.rank(),
						recommendation.regionCode(),
						recommendation.regionName(),
						syncRule.legalDongCode().value(),
						syncResponse
				));
			} catch (RuntimeException exception) {
				results.add(TourismContentRecommendationTopSyncResponse.RegionResult.failure(
						recommendation.rank(),
						recommendation.regionCode(),
						recommendation.regionName(),
						syncRule.legalDongCode().value(),
						exception.getMessage()
				));
			}
		}
		return TourismContentRecommendationTopSyncResponse.of(results);
	}

	private Map<String, SyncRule> findSyncRules(List<ValueRecommendationItemResponse> recommendations) {
		List<String> regionCodes = recommendations.stream()
				.map(ValueRecommendationItemResponse::regionCode)
				.filter(StringUtils::hasText)
				.distinct()
				.toList();
		Map<String, SyncRule> syncRuleByRegion = new java.util.HashMap<>();
		for (TourismRegionTourApiMapping mapping : mappingRepository.findByEnabledTrueAndRegionCodeIn(regionCodes)) {
			SyncRule.from(mapping).ifPresent(syncRule -> syncRuleByRegion.putIfAbsent(mapping.getRegionCode(), syncRule));
		}

		for (String regionCode : regionCodes) {
			if (syncRuleByRegion.containsKey(regionCode)) {
				continue;
			}
			deriveLegalDongCode(regionCode)
					.map(SyncRule::defaultRule)
					.ifPresent(syncRule -> syncRuleByRegion.put(regionCode, syncRule));
		}
		return syncRuleByRegion;
	}

	private Optional<LegalDongCode> deriveLegalDongCode(String regionCode) {
		if (!StringUtils.hasText(regionCode)) {
			return Optional.empty();
		}
		String[] parts = regionCode.split("-");
		if (parts.length != 2 || parts[0].length() != 2 || parts[1].length() < 3) {
			return Optional.empty();
		}
		String sigunguCode = parts[1].substring(parts[1].length() - 3);
		return Optional.of(new LegalDongCode(parts[0], sigunguCode));
	}

	private String defaultIfBlank(String value, String defaultValue) {
		return StringUtils.hasText(value) ? value : defaultValue;
	}

	private record LegalDongCode(String regionCode, String sigunguCode) {

		private String value() {
			return regionCode + "-" + sigunguCode;
		}
	}

	private record SyncRule(LegalDongCode legalDongCode, String contentTypeId, String categoryCode) {

		private static Optional<SyncRule> from(TourismRegionTourApiMapping mapping) {
			String[] parts = mapping.getLegalDongCode().split("-");
			if (parts.length != 2 || !StringUtils.hasText(parts[0]) || !StringUtils.hasText(parts[1])) {
				return Optional.empty();
			}
			return Optional.of(new SyncRule(
					new LegalDongCode(parts[0], parts[1]),
					defaultIfBlank(mapping.getDefaultContentTypeId(), DEFAULT_CONTENT_TYPE_ID),
					mapping.getDefaultCategoryCode()
			));
		}

		private static SyncRule defaultRule(LegalDongCode legalDongCode) {
			return new SyncRule(legalDongCode, DEFAULT_CONTENT_TYPE_ID, null);
		}

		private static String defaultIfBlank(String value, String defaultValue) {
			return StringUtils.hasText(value) ? value : defaultValue;
		}
	}
}
