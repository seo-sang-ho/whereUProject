package com.trip.whereU.resourcedemand.service;

import com.trip.whereU.resourcedemand.client.TourismResourceDemandOpenApiClient;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandLatestResponse;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiItem;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiPage;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandResponse;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncResponse;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.repository.TourismResourceDemandRepository;
import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

@Service
public class TourismResourceDemandService {

	private static final int FIRST_PAGE = 1;
	private static final int PAGE_SIZE = 100;
	private static final Map<ResourceDemandType, List<String>> INDICATOR_CODES = Map.of(
			ResourceDemandType.SERVICE,
			List.of("1101", "1102", "1103", "1104", "1105", "1106", "1107", "1108", "1109", "1110", "1111", "1112"),
			ResourceDemandType.CULTURE,
			List.of("1201", "1202", "1203", "1204", "1205")
	);

	private final TourismResourceDemandRepository repository;
	private final TourismResourceDemandOpenApiClient openApiClient;
	private final TourismResourceDemandPersistenceService persistenceService;
	private final TourismResourceDemandApiProperties properties;

	public TourismResourceDemandService(
			TourismResourceDemandRepository repository,
			TourismResourceDemandOpenApiClient openApiClient,
			TourismResourceDemandPersistenceService persistenceService,
			TourismResourceDemandApiProperties properties
	) {
		this.repository = repository;
		this.openApiClient = openApiClient;
		this.persistenceService = persistenceService;
		this.properties = properties;
	}

	public TourismResourceDemandSyncResponse sync() {
		return syncIndicators(INDICATOR_CODES);
	}

	public TourismResourceDemandSyncResponse sync(
			ResourceDemandType resourceType,
			String indicatorCode
	) {
		validateIndicator(resourceType, indicatorCode);
		return syncIndicators(Map.of(resourceType, List.of(indicatorCode)));
	}

	private TourismResourceDemandSyncResponse syncIndicators(
			Map<ResourceDemandType, List<String>> indicatorsByType
	) {
		SyncResult result = new SyncResult();
		for (Map.Entry<ResourceDemandType, List<String>> entry : indicatorsByType.entrySet()) {
			for (String indicatorCode : entry.getValue()) {
				ResourceDemandType resourceType = entry.getKey();
				try {
					List<TourismResourceDemandOpenApiItem> items = fetchIndicator(
							resourceType, indicatorCode
					);
					result.recordSuccess(
							resourceType,
							items,
							persistenceService.saveItems(items)
					);
				} catch (RestClientException exception) {
					result.recordFailure(resourceType, indicatorCode);
				}
			}
		}
		return result.toResponse();
	}

	private List<TourismResourceDemandOpenApiItem> fetchIndicator(
			ResourceDemandType resourceType,
			String indicatorCode
	) {
		List<TourismResourceDemandOpenApiItem> items = new ArrayList<>();
		for (String areaCode : properties.serviceDemand().areaCodes()) {
			int pageNo = FIRST_PAGE;
			TourismResourceDemandOpenApiPage page;
			do {
				page = openApiClient.fetchPage(
						resourceType, areaCode, indicatorCode, pageNo, PAGE_SIZE
				);
				page.items().stream()
						.filter(item -> !item.isAreaAggregate())
						.forEach(items::add);
				pageNo++;
			} while ((long) (pageNo - 1) * PAGE_SIZE < page.totalCount());
		}
		return items;
	}

	void validateIndicator(ResourceDemandType resourceType, String indicatorCode) {
		if (resourceType == null || !StringUtils.hasText(indicatorCode)) {
			throw new IllegalArgumentException("resourceType과 indicatorCode를 함께 입력하세요.");
		}
		if (!INDICATOR_CODES.get(resourceType).contains(indicatorCode)) {
			throw new IllegalArgumentException("자원 유형에 맞는 지표 코드를 입력하세요.");
		}
	}

	@Transactional(readOnly = true)
	public TourismResourceDemandLatestResponse getLatest() {
		Optional<LocalDate> latestDate = repository.findLatestReferenceDate();
		if (latestDate.isEmpty()) {
			return TourismResourceDemandLatestResponse.empty();
		}
		List<TourismResourceDemandResponse> demands = repository
				.findByReferenceDateOrderByResourceTypeAscIndicatorCodeAscRegionCodeAsc(latestDate.get())
				.stream()
				.map(TourismResourceDemandResponse::from)
				.toList();
		return TourismResourceDemandLatestResponse.of(latestDate.get(), demands);
	}

	private static class SyncResult {
		private int serviceCollectedCount;
		private int culturalCollectedCount;
		private int savedCount;
		private int successfulIndicatorCount;
		private LocalDate referenceDate;
		private final List<String> failedIndicators = new ArrayList<>();

		private void recordSuccess(
				ResourceDemandType resourceType,
				List<TourismResourceDemandOpenApiItem> items,
				int savedItems
		) {
			if (resourceType == ResourceDemandType.SERVICE) {
				serviceCollectedCount += items.size();
			} else {
				culturalCollectedCount += items.size();
			}
			savedCount += savedItems;
			successfulIndicatorCount++;
			items.stream()
					.map(TourismResourceDemandOpenApiItem::referenceDate)
					.max(LocalDate::compareTo)
					.filter(date -> referenceDate == null || date.isAfter(referenceDate))
					.ifPresent(date -> referenceDate = date);
		}

		private void recordFailure(ResourceDemandType resourceType, String indicatorCode) {
			failedIndicators.add(resourceType.name() + ":" + indicatorCode);
		}

		private TourismResourceDemandSyncResponse toResponse() {
			return new TourismResourceDemandSyncResponse(
					serviceCollectedCount,
					culturalCollectedCount,
					savedCount,
					referenceDate,
					successfulIndicatorCount,
					List.copyOf(failedIndicators)
			);
		}
	}
}
