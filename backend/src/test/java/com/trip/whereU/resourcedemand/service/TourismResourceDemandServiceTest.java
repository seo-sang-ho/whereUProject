package com.trip.whereU.resourcedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismResourceDemandServiceTest {

	private static final List<String> SERVICE_CODES = List.of(
			"1101", "1102", "1103", "1104", "1105", "1106",
			"1107", "1108", "1109", "1110", "1111", "1112"
	);
	private static final List<String> CULTURE_CODES = List.of(
			"1201", "1202", "1203", "1204", "1205"
	);
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
	void collectsEveryIndicatorWithOneBulkRequestPerResourceTypeAndArea() {
		List<TourismResourceDemandOpenApiItem> serviceItems = new ArrayList<>(items(
				ResourceDemandType.SERVICE, SERVICE_CODES
		));
		serviceItems.add(item(ResourceDemandType.SERVICE, "11"));
		List<TourismResourceDemandOpenApiItem> cultureItems = items(
				ResourceDemandType.CULTURE, CULTURE_CODES
		);
		when(openApiClient.fetchPage(ResourceDemandType.SERVICE, "11", null, 1, 1000))
				.thenReturn(page(serviceItems));
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", null, 1, 1000))
				.thenReturn(page(cultureItems));
		when(persistenceService.saveItems(anyList()))
				.thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

		TourismResourceDemandSyncResponse response = service().sync();

		verify(openApiClient, times(2)).fetchPage(
				org.mockito.ArgumentMatchers.any(), eq("11"), isNull(), eq(1), eq(1000)
		);
		verify(persistenceService, times(2)).saveItems(anyList());
		assertThat(response.serviceCollectedCount()).isEqualTo(12);
		assertThat(response.culturalCollectedCount()).isEqualTo(5);
		assertThat(response.savedCount()).isEqualTo(17);
		assertThat(response.successfulIndicatorCount()).isEqualTo(17);
		assertThat(response.skippedIndicatorCount()).isZero();
		assertThat(response.bulkCollectedResourceTypes()).containsExactly("SERVICE", "CULTURE");
		assertThat(response.fallbackResourceTypes()).isEmpty();
		assertThat(response.failedIndicators()).isEmpty();
	}

	@Test
	void fallsBackToFilteredRequestsWhenBulkResponseMissesIndicators() {
		when(openApiClient.fetchPage(ResourceDemandType.SERVICE, "11", null, 1, 1000))
				.thenReturn(page(List.of(item(ResourceDemandType.SERVICE, "1111"))));
		when(openApiClient.fetchPage(
				eq(ResourceDemandType.SERVICE), eq("11"), anyString(), eq(1), eq(100)
		)).thenAnswer(invocation -> page(List.of(item(
				ResourceDemandType.SERVICE, invocation.getArgument(2)
		))));
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", null, 1, 1000))
				.thenReturn(page(items(ResourceDemandType.CULTURE, CULTURE_CODES)));
		when(persistenceService.saveItems(anyList()))
				.thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

		TourismResourceDemandSyncResponse response = service().sync();

		verify(openApiClient, times(12)).fetchPage(
				eq(ResourceDemandType.SERVICE), eq("11"), anyString(), eq(1), eq(100)
		);
		assertThat(response.successfulIndicatorCount()).isEqualTo(17);
		assertThat(response.savedCount()).isEqualTo(17);
		assertThat(response.bulkCollectedResourceTypes()).containsExactly("CULTURE");
		assertThat(response.fallbackResourceTypes()).containsExactly("SERVICE");
	}

	@Test
	void skipsAllExternalCallsWhenReferenceMonthIsAlreadyComplete() {
		when(syncStateService.isCompleted(
				org.mockito.ArgumentMatchers.any(), anyString(), eq(REFERENCE_DATE)
		)).thenReturn(true);

		TourismResourceDemandSyncResponse response = service().sync();

		verify(openApiClient, never()).fetchPage(
				org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
				org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()
		);
		assertThat(response.savedCount()).isZero();
		assertThat(response.skippedIndicatorCount()).isEqualTo(17);
		assertThat(response.skippedIndicators()).hasSize(17);
		assertThat(response.referenceDate()).isEqualTo(REFERENCE_DATE);
	}

	@Test
	void synchronizesOnlyRequestedIndicatorAndCanForceRefresh() {
		TourismResourceDemandOpenApiItem nature = item(ResourceDemandType.CULTURE, "1205");
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", "1205", 1, 100))
				.thenReturn(page(List.of(nature)));
		when(persistenceService.saveItems(List.of(nature))).thenReturn(1);

		TourismResourceDemandSyncResponse response = service().sync(
				ResourceDemandType.CULTURE, "1205", true
		);

		assertThat(response.culturalCollectedCount()).isEqualTo(1);
		assertThat(response.savedCount()).isEqualTo(1);
		assertThat(response.successfulIndicatorCount()).isEqualTo(1);
		verify(syncStateService).markCompleted(
				ResourceDemandType.CULTURE, "1205", REFERENCE_DATE, 1
		);
	}

	@Test
	void followsReturnedPageSizeWhenProviderCapsBulkPageSize() {
		List<TourismResourceDemandOpenApiItem> firstPage = items(
				ResourceDemandType.SERVICE, SERVICE_CODES
		);
		when(openApiClient.fetchPage(ResourceDemandType.SERVICE, "11", null, 1, 1000))
				.thenReturn(new TourismResourceDemandOpenApiPage(firstPage, 1, 10, 11));
		when(openApiClient.fetchPage(ResourceDemandType.SERVICE, "11", null, 2, 1000))
				.thenReturn(new TourismResourceDemandOpenApiPage(List.of(), 2, 10, 11));
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", null, 1, 1000))
				.thenReturn(page(items(ResourceDemandType.CULTURE, CULTURE_CODES)));
		when(persistenceService.saveItems(anyList()))
				.thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

		service().sync();

		verify(openApiClient).fetchPage(ResourceDemandType.SERVICE, "11", null, 2, 1000);
	}

	private TourismResourceDemandService service() {
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
				)
		);
	}

	private List<TourismResourceDemandOpenApiItem> items(
			ResourceDemandType resourceType,
			List<String> indicatorCodes
	) {
		List<TourismResourceDemandOpenApiItem> items = new ArrayList<>();
		for (String indicatorCode : indicatorCodes) {
			items.add(item(resourceType, indicatorCode));
		}
		return List.copyOf(items);
	}

	private TourismResourceDemandOpenApiPage page(
			List<TourismResourceDemandOpenApiItem> items
	) {
		return new TourismResourceDemandOpenApiPage(items, 1, 1000, items.size());
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
