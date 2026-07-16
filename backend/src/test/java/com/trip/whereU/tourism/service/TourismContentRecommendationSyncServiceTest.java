package com.trip.whereU.tourism.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.trip.whereU.recommendation.dto.ValueRecommendationItemResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationResponse;
import com.trip.whereU.recommendation.service.ValueRecommendationService;
import com.trip.whereU.tourism.dto.TourismContentRecommendationTopSyncResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.entity.TourismRegionTourApiMapping;
import com.trip.whereU.tourism.repository.TourismRegionTourApiMappingRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismContentRecommendationSyncServiceTest {

	@Mock
	private ValueRecommendationService valueRecommendationService;
	@Mock
	private TourismContentService tourismContentService;
	@Mock
	private TourismRegionTourApiMappingRepository mappingRepository;

	@Test
	void syncsTourismContentsForValueRecommendationTopRegions() {
		when(valueRecommendationService.getLatestValueRecommendations(2))
				.thenReturn(ValueRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(
								recommendation(1, "11-11680", "서울특별시 강남구"),
								recommendation(2, "50-50110", "제주특별자치도 제주시")
						)
				));
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("11-11680", "50-50110")))
				.thenReturn(List.of());
		when(tourismContentService.sync("12", "C", "11", "680", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(8, 8, 1));
		when(tourismContentService.sync("12", "C", "50", "110", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(6, 6, 1));

		TourismContentRecommendationTopSyncResponse response = service()
				.syncValueRecommendationTopRegions("12", "C", null, null, null, 2, 10);

		assertThat(response.requestedRegionCount()).isEqualTo(2);
		assertThat(response.successRegionCount()).isEqualTo(2);
		assertThat(response.failedRegionCount()).isZero();
		assertThat(response.totalFetchedCount()).isEqualTo(14);
		assertThat(response.totalSavedCount()).isEqualTo(14);
		assertThat(response.results()).extracting(TourismContentRecommendationTopSyncResponse.RegionResult::legalDongCode)
				.containsExactly("11-680", "50-110");
	}

	@Test
	void explicitMappingOverridesDerivedLegalDongCodeAndDefaultCategory() {
		when(valueRecommendationService.getLatestValueRecommendations(1))
				.thenReturn(ValueRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(recommendation(1, "26-26380", "부산광역시 사하구"))
				));
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("26-26380")))
				.thenReturn(List.of(new TourismRegionTourApiMapping(
						"26-26380",
						"부산광역시 사하구",
						"26-380",
						"12",
						"NA040500",
						true
				)));
		when(tourismContentService.sync("12", "C", "26", "380", null, null, "NA040500", 10))
				.thenReturn(new TourismContentSyncResponse(3, 3, 1));

		TourismContentRecommendationTopSyncResponse response = service()
				.syncValueRecommendationTopRegions(null, null, null, null, null, 1, 10);

		assertThat(response.results().getFirst().legalDongCode()).isEqualTo("26-380");
		assertThat(response.totalSavedCount()).isEqualTo(3);
	}

	private TourismContentRecommendationSyncService service() {
		return new TourismContentRecommendationSyncService(
				valueRecommendationService,
				tourismContentService,
				mappingRepository
		);
	}

	private ValueRecommendationItemResponse recommendation(int rank, String regionCode, String regionName) {
		return new ValueRecommendationItemResponse(
				rank,
				regionCode,
				regionName,
				100,
				1.0,
				50,
				0.5,
				0.85,
				85,
				"추천 지역입니다.",
				37.5,
				127.0,
				List.of()
		);
	}
}
