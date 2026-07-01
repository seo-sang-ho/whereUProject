package com.trip.whereU.staystrength.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.staystrength.client.TourismStayStrengthOpenApiClient;
import com.trip.whereU.staystrength.config.TourismOpenApiProperties;
import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiItem;
import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiPage;
import com.trip.whereU.staystrength.dto.TourismStayStrengthLatestResponse;
import com.trip.whereU.staystrength.dto.TourismStayStrengthResponse;
import com.trip.whereU.staystrength.dto.TourismStayStrengthSyncResponse;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismStayStrengthServiceTest {

	@Mock
	private TourismStayStrengthRepository tourismStayStrengthRepository;

	@Mock
	private TourismStayStrengthOpenApiClient tourismStayStrengthOpenApiClient;

	@Mock
	private TourismStayStrengthPersistenceService tourismStayStrengthPersistenceService;

	@Mock
	private TourismRegionRepository tourismRegionRepository;

	@Test
	void collectsEveryPageAndExcludesAreaAggregate() {
		TourismOpenApiProperties properties = properties(List.of("11"));
		TourismStayStrengthService service = new TourismStayStrengthService(
				tourismStayStrengthRepository,
				tourismStayStrengthOpenApiClient,
				tourismStayStrengthPersistenceService,
				tourismRegionRepository,
				properties
		);
		TourismStayStrengthOpenApiItem aggregate = item("11-0", "0", 89.81);
		TourismStayStrengthOpenApiItem jongno = item("11-11110", "11110", 84.26);
		TourismStayStrengthOpenApiItem jung = item("11-11140", "11140", 81.33);
		when(tourismStayStrengthOpenApiClient.fetchStayStrengthPage("11", 1, 100))
				.thenReturn(new TourismStayStrengthOpenApiPage(List.of(aggregate, jongno), 1, 100, 101));
		when(tourismStayStrengthOpenApiClient.fetchStayStrengthPage("11", 2, 100))
				.thenReturn(new TourismStayStrengthOpenApiPage(List.of(jung), 2, 1, 101));
		when(tourismStayStrengthPersistenceService.saveStayStrengthItems(anyList())).thenReturn(2);

		TourismStayStrengthSyncResponse response = service.syncStayStrengthData();

		ArgumentCaptor<List<TourismStayStrengthOpenApiItem>> captor = ArgumentCaptor.forClass(List.class);
		verify(tourismStayStrengthPersistenceService).saveStayStrengthItems(captor.capture());
		assertThat(captor.getValue()).extracting(TourismStayStrengthOpenApiItem::regionCode)
				.containsExactly("11-11110", "11-11140");
		assertThat(response.collectedCount()).isEqualTo(2);
		assertThat(response.savedCount()).isEqualTo(2);
	}

	@Test
	void findsStayStrengthLevelsUsingRegionCoordinateBounds() {
		TourismStayStrengthService service = new TourismStayStrengthService(
				tourismStayStrengthRepository,
				tourismStayStrengthOpenApiClient,
				tourismStayStrengthPersistenceService,
				tourismRegionRepository,
				properties(List.of("11"))
		);
		TourismRegion region = new TourismRegion("11-11110", "서울특별시 종로구", 37.57, 126.98);
		TourismStayStrength stayStrength = new TourismStayStrength(
				"11-11110",
				"서울특별시 종로구",
				84.26,
				0.8,
				LocalDate.of(2025, 9, 1)
		);
		when(tourismRegionRepository.findByLatitudeBetweenAndLongitudeBetween(37, 38, 126, 128))
				.thenReturn(List.of(region));
		when(tourismStayStrengthRepository.findByRegionCodeIn(List.of("11-11110"))).thenReturn(List.of(stayStrength));

		List<TourismStayStrengthResponse> responses = service.getStayStrengthLevelsInBounds(37, 38, 126, 128);

		assertThat(responses).singleElement().satisfies(response -> {
			assertThat(response.regionCode()).isEqualTo("11-11110");
			assertThat(response.latitude()).isEqualTo(37.57);
			assertThat(response.longitude()).isEqualTo(126.98);
		});
	}

	@Test
	void returnsOnlyLatestReferenceDateStayStrengthLevels() {
		TourismStayStrengthService service = service();
		LocalDate latestDate = LocalDate.of(2025, 10, 1);
		TourismRegion region = new TourismRegion("11-11110", "서울특별시 종로구", 37.57, 126.98);
		TourismStayStrength latestStayStrength = stayStrength("11-11110", latestDate, 91.2, 0.9);
		when(tourismStayStrengthRepository.findLatestReferenceDate()).thenReturn(Optional.of(latestDate));
		when(tourismStayStrengthRepository.findByReferenceDate(latestDate)).thenReturn(List.of(latestStayStrength));
		when(tourismRegionRepository.findAll()).thenReturn(List.of(region));

		TourismStayStrengthLatestResponse response = service.getLatestStayStrengthLevels();

		assertThat(response.referenceDate()).isEqualTo(latestDate);
		assertThat(response.count()).isEqualTo(1);
		assertThat(response.stayStrengths()).singleElement().satisfies(item -> {
			assertThat(item.regionCode()).isEqualTo("11-11110");
			assertThat(item.latitude()).isEqualTo(37.57);
		});
	}

	@Test
	void returnsLatestStayStrengthLevelsWithinBounds() {
		TourismStayStrengthService service = service();
		LocalDate latestDate = LocalDate.of(2025, 10, 1);
		TourismRegion region = new TourismRegion("11-11110", "서울특별시 종로구", 37.57, 126.98);
		TourismStayStrength latestStayStrength = stayStrength("11-11110", latestDate, 91.2, 0.9);
		when(tourismStayStrengthRepository.findLatestReferenceDate()).thenReturn(Optional.of(latestDate));
		when(tourismRegionRepository.findByLatitudeBetweenAndLongitudeBetween(37, 38, 126, 128))
				.thenReturn(List.of(region));
		when(tourismStayStrengthRepository.findByReferenceDateAndRegionCodeIn(
				latestDate,
				List.of("11-11110")
		)).thenReturn(List.of(latestStayStrength));

		TourismStayStrengthLatestResponse response = service.getLatestStayStrengthLevelsInBounds(37, 38, 126, 128);

		assertThat(response.referenceDate()).isEqualTo(latestDate);
		assertThat(response.count()).isEqualTo(1);
		assertThat(response.stayStrengths()).extracting(TourismStayStrengthResponse::regionCode)
				.containsExactly("11-11110");
	}

	private TourismStayStrengthOpenApiItem item(String regionCode, String districtCode, double score) {
		return new TourismStayStrengthOpenApiItem(
				regionCode,
				"서울특별시",
				districtCode,
				score,
				LocalDate.of(2025, 9, 1)
		);
	}

	private TourismStayStrengthService service() {
		return new TourismStayStrengthService(
				tourismStayStrengthRepository,
				tourismStayStrengthOpenApiClient,
				tourismStayStrengthPersistenceService,
				tourismRegionRepository,
				properties(List.of("11"))
		);
	}

	private TourismStayStrength stayStrength(String regionCode, LocalDate referenceDate, double rawScore, double normalizedScore) {
		return new TourismStayStrength(
				regionCode,
				"서울특별시 종로구",
				rawScore,
				normalizedScore,
				referenceDate
		);
	}

	private TourismOpenApiProperties properties(List<String> areaCodes) {
		return new TourismOpenApiProperties(
				"https://apis.data.go.kr/B551011/AreaTarDemDsService",
				"/areaTarSjrnDsList",
				"encoded-key",
				"ETC",
				"whereU",
				new TourismOpenApiProperties.StayStrength("202509", areaCodes, "21")
		);
	}
}
