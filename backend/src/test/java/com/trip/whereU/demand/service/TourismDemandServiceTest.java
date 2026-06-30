package com.trip.whereU.demand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.demand.client.TourismDemandOpenApiClient;
import com.trip.whereU.demand.config.TourismOpenApiProperties;
import com.trip.whereU.demand.dto.TourismDemandOpenApiItem;
import com.trip.whereU.demand.dto.TourismDemandOpenApiPage;
import com.trip.whereU.demand.dto.TourismDemandSyncResponse;
import com.trip.whereU.demand.repository.TourismDemandRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismDemandServiceTest {

	@Mock
	private TourismDemandRepository tourismDemandRepository;

	@Mock
	private TourismDemandOpenApiClient tourismDemandOpenApiClient;

	@Mock
	private TourismDemandPersistenceService tourismDemandPersistenceService;

	@Test
	void collectsEveryPageAndExcludesAreaAggregate() {
		TourismOpenApiProperties properties = properties(List.of("11"));
		TourismDemandService service = new TourismDemandService(
				tourismDemandRepository,
				tourismDemandOpenApiClient,
				tourismDemandPersistenceService,
				properties
		);
		TourismDemandOpenApiItem aggregate = item("11-0", "0", 89.81);
		TourismDemandOpenApiItem jongno = item("11-11110", "11110", 84.26);
		TourismDemandOpenApiItem jung = item("11-11140", "11140", 81.33);
		when(tourismDemandOpenApiClient.fetchDemandPage("11", 1, 100))
				.thenReturn(new TourismDemandOpenApiPage(List.of(aggregate, jongno), 1, 100, 101));
		when(tourismDemandOpenApiClient.fetchDemandPage("11", 2, 100))
				.thenReturn(new TourismDemandOpenApiPage(List.of(jung), 2, 1, 101));
		when(tourismDemandPersistenceService.saveDemandItems(anyList())).thenReturn(2);

		TourismDemandSyncResponse response = service.syncDemandData();

		ArgumentCaptor<List<TourismDemandOpenApiItem>> captor = ArgumentCaptor.forClass(List.class);
		verify(tourismDemandPersistenceService).saveDemandItems(captor.capture());
		assertThat(captor.getValue()).extracting(TourismDemandOpenApiItem::regionCode)
				.containsExactly("11-11110", "11-11140");
		assertThat(response.collectedCount()).isEqualTo(2);
		assertThat(response.savedCount()).isEqualTo(2);
	}

	private TourismDemandOpenApiItem item(String regionCode, String districtCode, double score) {
		return new TourismDemandOpenApiItem(
				regionCode,
				"서울특별시",
				districtCode,
				score,
				null,
				null,
				LocalDate.of(2025, 9, 1)
		);
	}

	private TourismOpenApiProperties properties(List<String> areaCodes) {
		return new TourismOpenApiProperties(
				"https://apis.data.go.kr/B551011/AreaTarDemDsService",
				"/areaTarSjrnDsList",
				"encoded-key",
				"ETC",
				"whereU",
				new TourismOpenApiProperties.Demand("202509", areaCodes, "21")
		);
	}
}
