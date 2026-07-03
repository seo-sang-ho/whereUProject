package com.trip.whereU.servicedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import com.trip.whereU.servicedemand.client.TourismServiceDemandOpenApiClient;
import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandLatestResponse;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiItem;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiPage;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandSyncResponse;
import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import com.trip.whereU.servicedemand.repository.TourismServiceDemandRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismServiceDemandServiceTest {

	@Mock
	private TourismServiceDemandRepository repository;
	@Mock
	private TourismServiceDemandOpenApiClient openApiClient;
	@Mock
	private TourismServiceDemandPersistenceService persistenceService;
	@Mock
	private TourismRegionRepository regionRepository;

	@Test
	void collectsAllPagesAndExcludesAreaAggregate() {
		TourismServiceDemandService service = service();
		TourismServiceDemandOpenApiItem aggregate = item("11-0", "0", 90);
		TourismServiceDemandOpenApiItem jongno = item("11-11110", "11110", 82);
		TourismServiceDemandOpenApiItem jung = item("11-11140", "11140", 78);
		when(openApiClient.fetchPage("11", 1, 100))
				.thenReturn(new TourismServiceDemandOpenApiPage(List.of(aggregate, jongno), 1, 100, 101));
		when(openApiClient.fetchPage("11", 2, 100))
				.thenReturn(new TourismServiceDemandOpenApiPage(List.of(jung), 2, 1, 101));
		when(persistenceService.saveItems(anyList())).thenReturn(2);

		TourismServiceDemandSyncResponse response = service.sync();

		ArgumentCaptor<List<TourismServiceDemandOpenApiItem>> captor = ArgumentCaptor.forClass(List.class);
		verify(persistenceService).saveItems(captor.capture());
		assertThat(captor.getValue()).extracting(TourismServiceDemandOpenApiItem::regionCode)
				.containsExactly("11-11110", "11-11140");
		assertThat(response.collectedCount()).isEqualTo(2);
		assertThat(response.savedCount()).isEqualTo(2);
	}

	@Test
	void returnsLatestServiceDemandWithinMapBounds() {
		TourismServiceDemandService service = service();
		LocalDate latestDate = LocalDate.of(2025, 9, 1);
		TourismRegion region = new TourismRegion("11-11110", "서울특별시 종로구", 37.57, 126.98);
		TourismServiceDemand demand = new TourismServiceDemand(
				"11-11110", "서울특별시 종로구", 82.5, 0.8, latestDate
		);
		when(repository.findLatestReferenceDate()).thenReturn(Optional.of(latestDate));
		when(regionRepository.findByLatitudeBetweenAndLongitudeBetween(37, 38, 126, 128))
				.thenReturn(List.of(region));
		when(repository.findByReferenceDateAndRegionCodeIn(latestDate, List.of("11-11110")))
				.thenReturn(List.of(demand));

		TourismServiceDemandLatestResponse response = service.getLatestInBounds(37, 38, 126, 128);

		assertThat(response.referenceDate()).isEqualTo(latestDate);
		assertThat(response.count()).isEqualTo(1);
		assertThat(response.serviceDemands()).singleElement().satisfies(item -> {
			assertThat(item.regionCode()).isEqualTo("11-11110");
			assertThat(item.level()).isEqualTo("HIGH");
			assertThat(item.latitude()).isEqualTo(37.57);
		});
	}

	private TourismServiceDemandService service() {
		TourismResourceDemandApiProperties properties = new TourismResourceDemandApiProperties(
				"https://apis.data.go.kr/B551011/AreaTarResDemService",
				"/areaTarSvcDemList",
				"/areaCulResDemList",
				"test-key",
				"ETC",
				"whereU",
				new TourismResourceDemandApiProperties.ServiceDemand("202509", List.of("11"), "11")
		);
		return new TourismServiceDemandService(
				repository,
				openApiClient,
				persistenceService,
				regionRepository,
				properties
		);
	}

	private TourismServiceDemandOpenApiItem item(String regionCode, String districtCode, double value) {
		return new TourismServiceDemandOpenApiItem(
				regionCode,
				"서울특별시",
				districtCode,
				value,
				LocalDate.of(2025, 9, 1)
		);
	}
}
