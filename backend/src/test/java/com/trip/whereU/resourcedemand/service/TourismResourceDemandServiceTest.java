package com.trip.whereU.resourcedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.resourcedemand.client.TourismResourceDemandOpenApiClient;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiItem;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiPage;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncResponse;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.repository.TourismResourceDemandRepository;
import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

@ExtendWith(MockitoExtension.class)
class TourismResourceDemandServiceTest {

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2025, 9, 1);

	@Mock
	private TourismResourceDemandRepository repository;
	@Mock
	private TourismResourceDemandOpenApiClient openApiClient;
	@Mock
	private TourismResourceDemandPersistenceService persistenceService;
	@Mock
	private TourismResourceDemandSyncStateService syncStateService;

	@Test
	void collectsAllSeventeenIndicatorsWithFilteredRequests() {
		when(openApiClient.fetchPage(
				org.mockito.ArgumentMatchers.any(), eq("11"), anyString(), eq(1), eq(100)
		)).thenAnswer(invocation -> page(List.of(item(
				invocation.getArgument(0), invocation.getArgument(2)
		))));
		when(persistenceService.saveItems(org.mockito.ArgumentMatchers.anyList()))
				.thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

		TourismResourceDemandSyncResponse response = service(Runnable::run).sync(true);

		verify(openApiClient, times(17)).fetchPage(
				org.mockito.ArgumentMatchers.any(), eq("11"), anyString(), eq(1), eq(100)
		);
		assertThat(response.serviceCollectedCount()).isEqualTo(12);
		assertThat(response.culturalCollectedCount()).isEqualTo(5);
		assertThat(response.savedCount()).isEqualTo(17);
		assertThat(response.successfulIndicatorCount()).isEqualTo(17);
		assertThat(response.collectionStrategy()).isEqualTo("PARALLEL_FILTERED");
		assertThat(response.openApiPageRequestCount()).isEqualTo(17);
		assertThat(response.failedIndicators()).isEmpty();
	}

	@Test
	void fetchesIndicatorsWithBoundedParallelism() throws Exception {
		CountDownLatch firstFourRequests = new CountDownLatch(4);
		AtomicInteger activeRequests = new AtomicInteger();
		AtomicInteger maximumActiveRequests = new AtomicInteger();
		when(openApiClient.fetchPage(
				org.mockito.ArgumentMatchers.any(), eq("11"), anyString(), eq(1), eq(100)
		)).thenAnswer(invocation -> {
			int active = activeRequests.incrementAndGet();
			maximumActiveRequests.accumulateAndGet(active, Math::max);
			firstFourRequests.countDown();
			if (!firstFourRequests.await(2, TimeUnit.SECONDS)) {
				throw new IllegalStateException("병렬 요청이 4개까지 시작되지 않았습니다.");
			}
			activeRequests.decrementAndGet();
			return page(List.of(item(invocation.getArgument(0), invocation.getArgument(2))));
		});
		when(persistenceService.saveItems(org.mockito.ArgumentMatchers.anyList())).thenReturn(1);
		ExecutorService executor = Executors.newFixedThreadPool(4);

		try {
			TourismResourceDemandSyncResponse response = service(executor).sync(true);

			assertThat(response.successfulIndicatorCount()).isEqualTo(17);
			assertThat(maximumActiveRequests).hasValue(4);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void skipsAllExternalCallsWhenReferenceMonthIsAlreadyComplete() {
		when(syncStateService.isCompleted(
				org.mockito.ArgumentMatchers.any(), anyString(), eq(REFERENCE_DATE)
		)).thenReturn(true);

		TourismResourceDemandSyncResponse response = service(Runnable::run).sync();

		verify(openApiClient, never()).fetchPage(
				org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
				org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()
		);
		assertThat(response.savedCount()).isZero();
		assertThat(response.skippedIndicatorCount()).isEqualTo(17);
		assertThat(response.openApiPageRequestCount()).isZero();
		assertThat(response.referenceDate()).isEqualTo(REFERENCE_DATE);
	}

	@Test
	void preservesSuccessfulIndicatorsWhenOneRequestFails() {
		when(openApiClient.fetchPage(
				org.mockito.ArgumentMatchers.any(), eq("11"), anyString(), eq(1), eq(100)
		)).thenAnswer(invocation -> {
			String indicatorCode = invocation.getArgument(2);
			if ("1101".equals(indicatorCode)) {
				throw new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT);
			}
			return page(List.of(item(invocation.getArgument(0), indicatorCode)));
		});
		when(persistenceService.saveItems(org.mockito.ArgumentMatchers.anyList())).thenReturn(1);

		TourismResourceDemandSyncResponse response = service(Runnable::run).sync(true);

		assertThat(response.successfulIndicatorCount()).isEqualTo(16);
		assertThat(response.failedIndicators()).containsExactly("SERVICE:1101");
		assertThat(response.openApiPageRequestCount()).isEqualTo(17);
	}

	@Test
	void reportsProgressForEveryProcessedIndicator() {
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", "1205", 1, 100))
				.thenReturn(page(List.of(item(ResourceDemandType.CULTURE, "1205"))));
		when(persistenceService.saveItems(org.mockito.ArgumentMatchers.anyList())).thenReturn(1);
		AtomicInteger processed = new AtomicInteger();
		AtomicInteger total = new AtomicInteger();

		TourismResourceDemandSyncResponse response = service(Runnable::run).sync(
				ResourceDemandType.CULTURE,
				"1205",
				true,
				(processedCount, totalCount) -> {
					processed.set(processedCount);
					total.set(totalCount);
				}
		);

		assertThat(response.successfulIndicatorCount()).isEqualTo(1);
		assertThat(processed).hasValue(1);
		assertThat(total).hasValue(1);
	}

	@Test
	void followsReturnedPageSizeWhenProviderCapsPageSize() {
		TourismResourceDemandOpenApiItem nature = item(ResourceDemandType.CULTURE, "1205");
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", "1205", 1, 100))
				.thenReturn(new TourismResourceDemandOpenApiPage(List.of(nature), 1, 1, 2));
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", "1205", 2, 100))
				.thenReturn(new TourismResourceDemandOpenApiPage(List.of(nature), 2, 1, 2));
		when(persistenceService.saveItems(org.mockito.ArgumentMatchers.anyList())).thenReturn(2);

		TourismResourceDemandSyncResponse response = service(Runnable::run).sync(
				ResourceDemandType.CULTURE, "1205", true
		);

		assertThat(response.openApiPageRequestCount()).isEqualTo(2);
		verify(openApiClient).fetchPage(ResourceDemandType.CULTURE, "11", "1205", 2, 100);
	}

	private TourismResourceDemandService service(Executor executor) {
		return new TourismResourceDemandService(
				repository,
				openApiClient,
				persistenceService,
				syncStateService,
				new TourismResourceDemandApiProperties(
						"https://apis.data.go.kr/B551011/AreaTarResDemService",
						"/areaTarSvcDemList",
						"/areaCulResDemList",
						"test-key",
						"ETC",
						"whereU",
						new TourismResourceDemandApiProperties.ServiceDemand(
								"202509", List.of("11"), "11"
						)
				),
				executor
		);
	}

	private TourismResourceDemandOpenApiPage page(
			List<TourismResourceDemandOpenApiItem> items
	) {
		return new TourismResourceDemandOpenApiPage(items, 1, 100, items.size());
	}

	private TourismResourceDemandOpenApiItem item(
			ResourceDemandType resourceType,
			String indicatorCode
	) {
		return new TourismResourceDemandOpenApiItem(
				"11-11110",
				"테스트 지역",
				"11110",
				resourceType,
				indicatorCode,
				"테스트 지표",
				70,
				REFERENCE_DATE
		);
	}
}
