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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TourismResourceDemandService {

	private static final int FIRST_PAGE = 1;
	private static final int PAGE_SIZE = 100;
	private static final int TOTAL_INDICATOR_COUNT = 17;
	private static final String COLLECTION_STRATEGY = "PARALLEL_FILTERED";
	private static final BiConsumer<Integer, Integer> NO_OP_PROGRESS = (processed, total) -> { };
	private static final Map<ResourceDemandType, List<String>> INDICATOR_CODES = Map.of(
			ResourceDemandType.SERVICE,
			List.of("1101", "1102", "1103", "1104", "1105", "1106", "1107", "1108", "1109", "1110", "1111", "1112"),
			ResourceDemandType.CULTURE,
			List.of("1201", "1202", "1203", "1204", "1205")
	);

	private final TourismResourceDemandRepository repository;
	private final TourismResourceDemandOpenApiClient openApiClient;
	private final TourismResourceDemandPersistenceService persistenceService;
	private final TourismResourceDemandSyncStateService syncStateService;
	private final TourismResourceDemandApiProperties properties;
	private final Executor fetchExecutor;

	public TourismResourceDemandService(
			TourismResourceDemandRepository repository,
			TourismResourceDemandOpenApiClient openApiClient,
			TourismResourceDemandPersistenceService persistenceService,
			TourismResourceDemandSyncStateService syncStateService,
			TourismResourceDemandApiProperties properties,
			@Qualifier("resourceDemandFetchExecutor") Executor fetchExecutor
	) {
		this.repository = repository;
		this.openApiClient = openApiClient;
		this.persistenceService = persistenceService;
		this.syncStateService = syncStateService;
		this.properties = properties;
		this.fetchExecutor = fetchExecutor;
	}

	public TourismResourceDemandSyncResponse sync() {
		return sync(false);
	}

	public TourismResourceDemandSyncResponse sync(boolean force) {
		return sync(force, NO_OP_PROGRESS);
	}

	public TourismResourceDemandSyncResponse sync(
			boolean force,
			BiConsumer<Integer, Integer> progressListener
	) {
		SyncResult result = new SyncResult();
		LocalDate referenceDate = configuredReferenceDate();
		AtomicInteger processedCount = new AtomicInteger();
		AtomicInteger requestCount = new AtomicInteger();
		List<IndicatorKey> pendingIndicators = new ArrayList<>();

		for (ResourceDemandType resourceType : ResourceDemandType.values()) {
			for (String indicatorCode : INDICATOR_CODES.get(resourceType)) {
				if (!force && syncStateService.isCompleted(resourceType, indicatorCode, referenceDate)) {
					result.recordSkipped(resourceType, indicatorCode, referenceDate);
					notifyProgress(progressListener, processedCount.incrementAndGet(), TOTAL_INDICATOR_COUNT);
				} else {
					pendingIndicators.add(new IndicatorKey(resourceType, indicatorCode));
				}
			}
		}

		syncIndicators(
				pendingIndicators,
				result,
				referenceDate,
				requestCount,
				processedCount,
				TOTAL_INDICATOR_COUNT,
				progressListener
		);
		return result.toResponse(requestCount.get());
	}

	public TourismResourceDemandSyncResponse sync(
			ResourceDemandType resourceType,
			String indicatorCode
	) {
		return sync(resourceType, indicatorCode, false);
	}

	public TourismResourceDemandSyncResponse sync(
			ResourceDemandType resourceType,
			String indicatorCode,
			boolean force
	) {
		return sync(resourceType, indicatorCode, force, NO_OP_PROGRESS);
	}

	public TourismResourceDemandSyncResponse sync(
			ResourceDemandType resourceType,
			String indicatorCode,
			boolean force,
			BiConsumer<Integer, Integer> progressListener
	) {
		validateIndicator(resourceType, indicatorCode);
		SyncResult result = new SyncResult();
		LocalDate referenceDate = configuredReferenceDate();
		AtomicInteger requestCount = new AtomicInteger();
		if (!force && syncStateService.isCompleted(resourceType, indicatorCode, referenceDate)) {
			result.recordSkipped(resourceType, indicatorCode, referenceDate);
			notifyProgress(progressListener, 1, 1);
			return result.toResponse(requestCount.get());
		}

		syncIndicators(
				List.of(new IndicatorKey(resourceType, indicatorCode)),
				result,
				referenceDate,
				requestCount,
				new AtomicInteger(),
				1,
				progressListener
		);
		return result.toResponse(requestCount.get());
	}

	private void syncIndicators(
			List<IndicatorKey> indicators,
			SyncResult result,
			LocalDate referenceDate,
			AtomicInteger requestCount,
			AtomicInteger processedCount,
			int totalCount,
			BiConsumer<Integer, Integer> progressListener
	) {
		List<CompletableFuture<IndicatorFetchResult>> futures = indicators.stream()
				.map(indicator -> CompletableFuture.supplyAsync(
						() -> new IndicatorFetchResult(fetchIndicator(indicator, requestCount)),
						fetchExecutor
				))
				.toList();

		for (int index = 0; index < futures.size(); index++) {
			IndicatorKey indicator = indicators.get(index);
			try {
				IndicatorFetchResult fetchResult = futures.get(index).join();
				List<TourismResourceDemandOpenApiItem> items = fetchResult.items();
				result.recordSuccess(
						indicator.resourceType(),
						items,
						persistenceService.saveItems(items)
				);
				syncStateService.markCompleted(
						indicator.resourceType(),
						indicator.indicatorCode(),
						referenceDate,
						items.size()
				);
			} catch (CompletionException exception) {
				result.recordFailure(indicator.resourceType(), indicator.indicatorCode());
			} finally {
				notifyProgress(progressListener, processedCount.incrementAndGet(), totalCount);
			}
		}
	}

	private List<TourismResourceDemandOpenApiItem> fetchIndicator(
			IndicatorKey indicator,
			AtomicInteger requestCount
	) {
		List<TourismResourceDemandOpenApiItem> items = new ArrayList<>();
		for (String areaCode : properties.serviceDemand().areaCodes()) {
			fetchPages(indicator, areaCode, items, requestCount);
		}
		return items;
	}

	private void fetchPages(
			IndicatorKey indicator,
			String areaCode,
			List<TourismResourceDemandOpenApiItem> target,
			AtomicInteger requestCount
	) {
		int pageNo = FIRST_PAGE;
		TourismResourceDemandOpenApiPage page;
		do {
			requestCount.incrementAndGet();
			page = openApiClient.fetchPage(
					indicator.resourceType(),
					areaCode,
					indicator.indicatorCode(),
					pageNo,
					PAGE_SIZE
			);
			page.items().stream()
					.filter(item -> !item.isAreaAggregate())
					.forEach(target::add);
			pageNo++;
		} while (hasNextPage(pageNo, page));
	}

	private boolean hasNextPage(int nextPageNo, TourismResourceDemandOpenApiPage page) {
		int effectivePageSize = page.numOfRows() > 0 ? page.numOfRows() : PAGE_SIZE;
		return (long) (nextPageNo - 1) * effectivePageSize < page.totalCount();
	}

	private void notifyProgress(
			BiConsumer<Integer, Integer> progressListener,
			int processedCount,
			int totalCount
	) {
		progressListener.accept(processedCount, totalCount);
	}

	private LocalDate configuredReferenceDate() {
		return YearMonth.parse(
				properties.serviceDemand().baseYm(),
				DateTimeFormatter.ofPattern("yyyyMM")
		).atDay(1);
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
		private final List<String> skippedIndicators = new ArrayList<>();
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

		private void recordSkipped(
				ResourceDemandType resourceType,
				String indicatorCode,
				LocalDate skippedReferenceDate
		) {
			skippedIndicators.add(resourceType.name() + ":" + indicatorCode);
			if (referenceDate == null || skippedReferenceDate.isAfter(referenceDate)) {
				referenceDate = skippedReferenceDate;
			}
		}

		private TourismResourceDemandSyncResponse toResponse(int openApiPageRequestCount) {
			return new TourismResourceDemandSyncResponse(
					serviceCollectedCount,
					culturalCollectedCount,
					savedCount,
					referenceDate,
					successfulIndicatorCount,
					skippedIndicators.size(),
					List.copyOf(skippedIndicators),
					COLLECTION_STRATEGY,
					openApiPageRequestCount,
					List.copyOf(failedIndicators)
			);
		}
	}

	private record IndicatorKey(
			ResourceDemandType resourceType,
			String indicatorCode
	) {
	}

	private record IndicatorFetchResult(
			List<TourismResourceDemandOpenApiItem> items
	) {
	}
}
