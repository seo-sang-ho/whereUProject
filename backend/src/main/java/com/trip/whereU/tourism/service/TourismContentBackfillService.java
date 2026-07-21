package com.trip.whereU.tourism.service;

import com.trip.whereU.recommendation.dto.RecommendedTourismContentResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.entity.TourismRegionTourApiMapping;
import com.trip.whereU.tourism.repository.TourismRegionTourApiMappingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class TourismContentBackfillService {

	private static final int MAX_BACKFILL_REGION_COUNT = 5;
	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final String DEFAULT_CONTENT_TYPE_ID = "12";
	private static final String DEFAULT_ARRANGE = "C";
	private static final Duration BACKFILL_COOLDOWN = Duration.ofHours(6);

	private final TourismContentService tourismContentService;
	private final TourismRegionTourApiMappingRepository mappingRepository;
	private final Clock clock;
	private final Map<String, Instant> lastRequestedAtByRegion = new ConcurrentHashMap<>();

	@Autowired
	public TourismContentBackfillService(
			TourismContentService tourismContentService,
			TourismRegionTourApiMappingRepository mappingRepository
	) {
		this(tourismContentService, mappingRepository, Clock.systemDefaultZone());
	}

	TourismContentBackfillService(
			TourismContentService tourismContentService,
			TourismRegionTourApiMappingRepository mappingRepository,
			Clock clock
	) {
		this.tourismContentService = tourismContentService;
		this.mappingRepository = mappingRepository;
		this.clock = clock;
	}

	@Async("tourismContentBackfillExecutor")
	public void requestBackfillForMissingImages(
			List<String> regionCodes,
			Map<String, List<RecommendedTourismContentResponse>> tourismContentsByRegion
	) {
		List<String> targetRegionCodes = findBackfillTargetRegionCodes(regionCodes, tourismContentsByRegion);
		if (targetRegionCodes.isEmpty()) {
			return;
		}

		Map<String, SyncRule> syncRuleByRegion = findSyncRules(targetRegionCodes);
		for (String regionCode : targetRegionCodes) {
			SyncRule syncRule = syncRuleByRegion.get(regionCode);
			if (syncRule == null) {
				log.info("TourAPI backfill skipped. regionCode={}, reason=mapping_missing", regionCode);
				continue;
			}

			try {
				TourismContentSyncResponse response = tourismContentService.sync(
						syncRule.contentTypeId(),
						DEFAULT_ARRANGE,
						syncRule.legalDongCode().regionCode(),
						syncRule.legalDongCode().sigunguCode(),
						null,
						null,
						syncRule.categoryCode(),
						DEFAULT_PAGE_SIZE
				);
				log.info(
						"TourAPI backfill completed. regionCode={}, legalDongCode={}, fetchedCount={}, savedCount={}",
						regionCode,
						syncRule.legalDongCode().value(),
						response.fetchedCount(),
						response.savedCount()
				);
			} catch (RuntimeException exception) {
				log.warn(
						"TourAPI backfill failed. regionCode={}, legalDongCode={}, message={}",
						regionCode,
						syncRule.legalDongCode().value(),
						exception.getMessage()
				);
			}
		}
	}

	private List<String> findBackfillTargetRegionCodes(
			List<String> regionCodes,
			Map<String, List<RecommendedTourismContentResponse>> tourismContentsByRegion
	) {
		if (regionCodes == null || regionCodes.isEmpty()) {
			return List.of();
		}
		Map<String, String> uniqueRegionCodes = new LinkedHashMap<>();
		for (String regionCode : regionCodes) {
			if (StringUtils.hasText(regionCode) && needsBackfill(regionCode, tourismContentsByRegion)) {
				uniqueRegionCodes.putIfAbsent(regionCode, regionCode);
			}
		}
		Instant now = Instant.now(clock);
		return uniqueRegionCodes.keySet().stream()
				.filter(regionCode -> markIfCooldownPassed(regionCode, now))
				.limit(MAX_BACKFILL_REGION_COUNT)
				.toList();
	}

	private boolean needsBackfill(
			String regionCode,
			Map<String, List<RecommendedTourismContentResponse>> tourismContentsByRegion
	) {
		List<RecommendedTourismContentResponse> contents = tourismContentsByRegion == null
				? List.of()
				: tourismContentsByRegion.getOrDefault(regionCode, List.of());
		return contents.isEmpty() || contents.stream().noneMatch(this::hasAnyImage);
	}

	private boolean hasAnyImage(RecommendedTourismContentResponse content) {
		return StringUtils.hasText(content.firstImage()) || StringUtils.hasText(content.firstImage2());
	}

	private boolean markIfCooldownPassed(String regionCode, Instant now) {
		Instant lastRequestedAt = lastRequestedAtByRegion.get(regionCode);
		if (lastRequestedAt != null && lastRequestedAt.plus(BACKFILL_COOLDOWN).isAfter(now)) {
			return false;
		}
		lastRequestedAtByRegion.put(regionCode, now);
		return true;
	}

	private Map<String, SyncRule> findSyncRules(List<String> regionCodes) {
		Map<String, SyncRule> syncRuleByRegion = new LinkedHashMap<>();
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
