package com.trip.whereU.tourism.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.recommendation.dto.PersonalizedRecommendationItemResponse;
import com.trip.whereU.recommendation.dto.PersonalizedRecommendationResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationItemResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationResponse;
import com.trip.whereU.recommendation.service.PersonalizedRecommendationService;
import com.trip.whereU.recommendation.service.ValueRecommendationService;
import com.trip.whereU.resourcedemand.entity.TourismTheme;
import com.trip.whereU.tourism.dto.TourismContentRecommendationTopSyncResponse;
import com.trip.whereU.tourism.dto.TourismContentRecommendationImageStatusResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.entity.TourismRegionTourApiMapping;
import com.trip.whereU.tourism.repository.TourismContentRepository;
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
	private PersonalizedRecommendationService personalizedRecommendationService;
	@Mock
	private TourismContentService tourismContentService;
	@Mock
	private TourismRegionTourApiMappingRepository mappingRepository;
	@Mock
	private TourismContentRepository tourismContentRepository;

	@Test
	void syncsTourismContentsForValueRecommendationTopRegions() {
		when(valueRecommendationService.getLatestValueRecommendationsWithoutBackfill(2))
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
		when(valueRecommendationService.getLatestValueRecommendationsWithoutBackfill(1))
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

	@Test
	void syncsTourismContentsForPersonalizedRecommendationTopRegions() {
		when(personalizedRecommendationService.getLatestRecommendationsWithoutBackfill(List.of(TourismTheme.NATURE), 2))
				.thenReturn(PersonalizedRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(TourismTheme.NATURE),
						List.of(
								personalizedRecommendation(1, "28-28110", "인천광역시 중구"),
								personalizedRecommendation(2, "26-26380", "부산광역시 사하구")
						)
				));
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("28-28110", "26-26380")))
				.thenReturn(List.of());
		when(tourismContentService.sync("12", "C", "28", "110", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(24, 24, 3));
		when(tourismContentService.sync("12", "C", "26", "380", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(3, 3, 1));

		TourismContentRecommendationTopSyncResponse response = service()
				.syncPersonalizedRecommendationTopRegions(
						List.of(TourismTheme.NATURE),
						"12",
						"C",
						null,
						null,
						null,
						2,
						10
				);

		assertThat(response.requestedRegionCount()).isEqualTo(2);
		assertThat(response.successRegionCount()).isEqualTo(2);
		assertThat(response.totalFetchedCount()).isEqualTo(27);
		assertThat(response.results()).extracting(TourismContentRecommendationTopSyncResponse.RegionResult::legalDongCode)
				.containsExactly("28-110", "26-380");
	}

	@Test
	void syncsTourismContentsForRecommendationCandidateRegionsWithoutDuplicates() {
		when(valueRecommendationService.getLatestValueRecommendationsWithoutBackfill(2))
				.thenReturn(ValueRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(
								recommendation(1, "11-11680", "서울특별시 강남구"),
								recommendation(2, "28-28110", "인천광역시 중구")
						)
				));
		when(personalizedRecommendationService.getLatestRecommendationsWithoutBackfill(List.of(TourismTheme.NATURE), 2))
				.thenReturn(PersonalizedRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(TourismTheme.NATURE),
						List.of(
								personalizedRecommendation(1, "28-28110", "인천광역시 중구"),
								personalizedRecommendation(2, "26-26380", "부산광역시 사하구")
						)
				));
		when(personalizedRecommendationService.getLatestRecommendationsWithoutBackfill(List.of(TourismTheme.FOOD), 2))
				.thenReturn(PersonalizedRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(TourismTheme.FOOD),
						List.of(personalizedRecommendation(1, "47-47130", "경상북도 경주시"))
				));
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of(
				"11-11680",
				"28-28110",
				"26-26380",
				"47-47130"
		))).thenReturn(List.of());
		when(tourismContentService.sync("12", "C", "11", "680", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(40, 40, 4));
		when(tourismContentService.sync("12", "C", "28", "110", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(24, 24, 3));
		when(tourismContentService.sync("12", "C", "26", "380", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(3, 3, 1));
		when(tourismContentService.sync("12", "C", "47", "130", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(20, 20, 2));

		TourismContentRecommendationTopSyncResponse response = service()
				.syncRecommendationCandidateRegions(
						List.of(TourismTheme.NATURE, TourismTheme.FOOD),
						"12",
						"C",
						null,
						null,
						null,
						2,
						10
				);

		assertThat(response.requestedRegionCount()).isEqualTo(4);
		assertThat(response.successRegionCount()).isEqualTo(4);
		assertThat(response.totalSavedCount()).isEqualTo(87);
		assertThat(response.results()).extracting(TourismContentRecommendationTopSyncResponse.RegionResult::regionCode)
				.containsExactly("11-11680", "28-28110", "26-26380", "47-47130");
		verify(tourismContentService, times(1))
				.sync("12", "C", "28", "110", null, null, null, 10);
	}

	@Test
	void reportsRecommendationCandidateImageStatus() {
		when(valueRecommendationService.getLatestValueRecommendationsWithoutBackfill(2))
				.thenReturn(ValueRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(
								recommendation(1, "11-11680", "서울특별시 강남구"),
								recommendation(2, "28-28110", "인천광역시 중구")
						)
				));
		when(personalizedRecommendationService.getLatestRecommendationsWithoutBackfill(List.of(TourismTheme.NATURE), 2))
				.thenReturn(PersonalizedRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(TourismTheme.NATURE),
						List.of(
								personalizedRecommendation(1, "28-28110", "인천광역시 중구"),
								personalizedRecommendation(2, "26-26380", "부산광역시 사하구")
						)
				));
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of(
				"11-11680",
				"28-28110",
				"26-26380"
		))).thenReturn(List.of());
		when(tourismContentRepository.findByLegalDongCodeIn(List.of("11-680", "28-110", "26-380")))
				.thenReturn(List.of(
						content("1", "이미지 있음", "11-680", "NA040500", "20260420173053", "https://example.com/a.jpg"),
						content("2", "이미지 없음", "28-110", "NA040500", "20260420173054", null)
				));

		TourismContentRecommendationImageStatusResponse response = service()
				.findRecommendationCandidateImageStatus(List.of(TourismTheme.NATURE), 2);

		assertThat(response.checkedRegionCount()).isEqualTo(3);
		assertThat(response.imageReadyRegionCount()).isEqualTo(1);
		assertThat(response.missingImageRegionCount()).isEqualTo(1);
		assertThat(response.noContentRegionCount()).isEqualTo(1);
		assertThat(response.results())
				.extracting(TourismContentRecommendationImageStatusResponse.RegionImageStatus::status)
				.containsExactly("IMAGE_READY", "MISSING_IMAGE", "NO_CONTENT");
		assertThat(response.results())
				.extracting(TourismContentRecommendationImageStatusResponse.RegionImageStatus::legalDongCode)
				.containsExactly("11-680", "28-110", "26-380");
	}

	@Test
	void imageStatusDoesNotUseRecommendationLookupThatRequestsBackfill() {
		when(valueRecommendationService.getLatestValueRecommendationsWithoutBackfill(1))
				.thenReturn(ValueRecommendationResponse.of(
						LocalDate.of(2025, 9, 1),
						List.of(recommendation(1, "11-11680", "서울특별시 강남구"))
				));
		when(personalizedRecommendationService.getLatestRecommendationsWithoutBackfill(List.of(TourismTheme.NATURE), 1))
				.thenReturn(PersonalizedRecommendationResponse.empty(List.of(TourismTheme.NATURE)));
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("11-11680")))
				.thenReturn(List.of());
		when(tourismContentRepository.findByLegalDongCodeIn(List.of("11-680")))
				.thenReturn(List.of());

		service().findRecommendationCandidateImageStatus(List.of(TourismTheme.NATURE), 1);

		verify(valueRecommendationService, never()).getLatestValueRecommendations(1);
		verify(personalizedRecommendationService, never())
				.getLatestRecommendations(List.of(TourismTheme.NATURE), 1);
	}

	private TourismContentRecommendationSyncService service() {
		return new TourismContentRecommendationSyncService(
				valueRecommendationService,
				personalizedRecommendationService,
				tourismContentService,
				mappingRepository,
				tourismContentRepository
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

	private PersonalizedRecommendationItemResponse personalizedRecommendation(
			int rank,
			String regionCode,
			String regionName
	) {
		return new PersonalizedRecommendationItemResponse(
				rank,
				regionCode,
				regionName,
				0.8,
				50,
				0.5,
				0.71,
				71,
				"맞춤 추천 지역입니다.",
				List.of(),
				37.5,
				127.0,
				List.of()
		);
	}

	private TourismContent content(
			String contentId,
			String title,
			String legalDongCode,
			String categoryCode,
			String modifiedTime,
			String firstImage
	) {
		return new TourismContent(
				contentId,
				"12",
				title,
				"테스트 주소",
				null,
				37.5,
				127.0,
				null,
				null,
				legalDongCode,
				categoryCode,
				null,
				null,
				null,
				firstImage,
				null,
				null,
				null,
				modifiedTime
		);
	}
}
