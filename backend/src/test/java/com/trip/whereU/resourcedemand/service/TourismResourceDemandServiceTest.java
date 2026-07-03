package com.trip.whereU.resourcedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismResourceDemandServiceTest {

	@Mock
	private TourismResourceDemandRepository repository;
	@Mock
	private TourismResourceDemandOpenApiClient openApiClient;
	@Mock
	private TourismResourceDemandPersistenceService persistenceService;

	@Test
	void collectsBothResourceTypesAndExcludesAreaAggregates() {
		TourismResourceDemandOpenApiItem serviceAggregate = item("11-0", "0", ResourceDemandType.SERVICE, "11");
		TourismResourceDemandOpenApiItem food = item("11-11110", "11110", ResourceDemandType.SERVICE, "1111");
		TourismResourceDemandOpenApiItem culturalAggregate = item("11-0", "0", ResourceDemandType.CULTURE, "12");
		TourismResourceDemandOpenApiItem nature = item("11-11110", "11110", ResourceDemandType.CULTURE, "1205");
		when(openApiClient.fetchPage(eq(ResourceDemandType.SERVICE), eq("11"), anyString(), eq(1), eq(100)))
				.thenAnswer(invocation -> "1111".equals(invocation.getArgument(2))
						? new TourismResourceDemandOpenApiPage(List.of(serviceAggregate, food), 1, 100, 2)
						: new TourismResourceDemandOpenApiPage(List.of(), 1, 100, 0));
		when(openApiClient.fetchPage(eq(ResourceDemandType.CULTURE), eq("11"), anyString(), eq(1), eq(100)))
				.thenAnswer(invocation -> "1205".equals(invocation.getArgument(2))
						? new TourismResourceDemandOpenApiPage(List.of(culturalAggregate, nature), 1, 100, 2)
						: new TourismResourceDemandOpenApiPage(List.of(), 1, 100, 0));
		when(persistenceService.saveItems(anyList()))
				.thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

		TourismResourceDemandSyncResponse response = service().sync();

		ArgumentCaptor<List<TourismResourceDemandOpenApiItem>> captor = ArgumentCaptor.forClass(List.class);
		verify(persistenceService, times(17)).saveItems(captor.capture());
		assertThat(captor.getAllValues()).flatMap(items -> items)
				.extracting(TourismResourceDemandOpenApiItem::indicatorCode)
				.containsExactlyInAnyOrder("1111", "1205");
		assertThat(response.serviceCollectedCount()).isEqualTo(1);
		assertThat(response.culturalCollectedCount()).isEqualTo(1);
		assertThat(response.savedCount()).isEqualTo(2);
		assertThat(response.successfulIndicatorCount()).isEqualTo(17);
		assertThat(response.failedIndicators()).isEmpty();
	}

	@Test
	void returnsNullReferenceDateWhenEveryIndicatorIsEmpty() {
		when(openApiClient.fetchPage(eq(ResourceDemandType.SERVICE), eq("11"), anyString(), eq(1), eq(100)))
				.thenReturn(new TourismResourceDemandOpenApiPage(List.of(), 1, 100, 0));
		when(openApiClient.fetchPage(eq(ResourceDemandType.CULTURE), eq("11"), anyString(), eq(1), eq(100)))
				.thenReturn(new TourismResourceDemandOpenApiPage(List.of(), 1, 100, 0));
		when(persistenceService.saveItems(List.of())).thenReturn(0);

		TourismResourceDemandSyncResponse response = service().sync();

		assertThat(response.referenceDate()).isNull();
		assertThat(response.savedCount()).isZero();
	}

	@Test
	void preservesSuccessfulIndicatorsAndReportsGatewayTimeout() {
		when(openApiClient.fetchPage(eq(ResourceDemandType.SERVICE), eq("11"), anyString(), eq(1), eq(100)))
				.thenAnswer(invocation -> {
					String indicatorCode = invocation.getArgument(2);
					if ("1101".equals(indicatorCode)) {
						throw new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT);
					}
					return "1111".equals(indicatorCode)
							? new TourismResourceDemandOpenApiPage(
									List.of(item("11-11110", "11110", ResourceDemandType.SERVICE, "1111")),
									1, 100, 1
							)
							: new TourismResourceDemandOpenApiPage(List.of(), 1, 100, 0);
				});
		when(openApiClient.fetchPage(eq(ResourceDemandType.CULTURE), eq("11"), anyString(), eq(1), eq(100)))
				.thenReturn(new TourismResourceDemandOpenApiPage(List.of(), 1, 100, 0));
		when(persistenceService.saveItems(anyList()))
				.thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

		TourismResourceDemandSyncResponse response = service().sync();

		assertThat(response.savedCount()).isEqualTo(1);
		assertThat(response.successfulIndicatorCount()).isEqualTo(16);
		assertThat(response.failedIndicators()).containsExactly("SERVICE:1101");
	}

	@Test
	void synchronizesOnlyRequestedIndicator() {
		TourismResourceDemandOpenApiItem nature = item(
				"11-11110", "11110", ResourceDemandType.CULTURE, "1205"
		);
		when(openApiClient.fetchPage(ResourceDemandType.CULTURE, "11", "1205", 1, 100))
				.thenReturn(new TourismResourceDemandOpenApiPage(List.of(nature), 1, 100, 1));
		when(persistenceService.saveItems(List.of(nature))).thenReturn(1);

		TourismResourceDemandSyncResponse response = service().sync(
				ResourceDemandType.CULTURE, "1205"
		);

		assertThat(response.culturalCollectedCount()).isEqualTo(1);
		assertThat(response.savedCount()).isEqualTo(1);
		assertThat(response.successfulIndicatorCount()).isEqualTo(1);
	}

	private TourismResourceDemandService service() {
		return new TourismResourceDemandService(
				repository,
				openApiClient,
				persistenceService,
				new TourismResourceDemandApiProperties(
						"https://apis.data.go.kr/B551011/AreaTarResDemService",
						"/areaTarSvcDemList",
						"/areaCulResDemList",
						"test-key",
						"ETC",
						"whereU",
						new TourismResourceDemandApiProperties.ServiceDemand("202509", List.of("11"), "11")
				)
		);
	}

	private TourismResourceDemandOpenApiItem item(
			String regionCode,
			String districtCode,
			ResourceDemandType resourceType,
			String indicatorCode
	) {
		return new TourismResourceDemandOpenApiItem(
				regionCode,
				"테스트 지역",
				districtCode,
				resourceType,
				indicatorCode,
				"테스트 지표",
				70,
				LocalDate.of(2025, 9, 1)
		);
	}
}
