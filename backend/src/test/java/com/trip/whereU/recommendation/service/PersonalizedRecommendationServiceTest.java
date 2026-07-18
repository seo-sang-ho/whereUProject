package com.trip.whereU.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import com.trip.whereU.recommendation.dto.PersonalizedRecommendationResponse;
import com.trip.whereU.recommendation.dto.RecommendedTourismContentResponse;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.entity.TourismResourceDemand;
import com.trip.whereU.resourcedemand.entity.TourismTheme;
import com.trip.whereU.resourcedemand.repository.TourismResourceDemandRepository;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import com.trip.whereU.tourism.service.TourismContentBackfillService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonalizedRecommendationServiceTest {

	@Mock
	private TourismResourceDemandRepository resourceDemandRepository;
	@Mock
	private TourismStayStrengthRepository stayStrengthRepository;
	@Mock
	private TourismRegionRepository regionRepository;
	@Mock
	private RecommendationTourismContentService recommendationTourismContentService;
	@Mock
	private TourismContentBackfillService tourismContentBackfillService;

	@Test
	void givesSelectedThemesEqualWeightAndRanksLowStayStrengthFirst() {
		LocalDate referenceDate = LocalDate.of(2025, 9, 1);
		LocalDate partialLatestDate = LocalDate.of(2025, 10, 1);
		when(resourceDemandRepository.findReferenceDatesByThemeDescending(TourismTheme.NATURE))
				.thenReturn(List.of(partialLatestDate, referenceDate));
		when(resourceDemandRepository.findReferenceDatesByThemeDescending(TourismTheme.FOOD))
				.thenReturn(List.of(referenceDate));
		when(stayStrengthRepository.findReferenceDatesDescending())
				.thenReturn(List.of(partialLatestDate, referenceDate));
		when(resourceDemandRepository.findByReferenceDateAndThemeIn(
				referenceDate,
				List.of(TourismTheme.NATURE, TourismTheme.FOOD)
		)).thenReturn(List.of(
				demand("11-11110", "종로구", "1205", 0.8, referenceDate),
				demand("11-11110", "종로구", "1103", 0.4, referenceDate),
				demand("11-11110", "종로구", "1111", 0.6, referenceDate),
				demand("11-11140", "중구", "1205", 0.9, referenceDate),
				demand("11-11140", "중구", "1103", 0.8, referenceDate)
		));
		when(stayStrengthRepository.findByReferenceDate(referenceDate)).thenReturn(List.of(
				stayStrength("11-11110", "종로구", 0.2, referenceDate),
				stayStrength("11-11140", "중구", 0.9, referenceDate)
		));
		when(regionRepository.findAll()).thenReturn(List.of(
				new TourismRegion("11-11110", "종로구", 37.57, 126.98),
				new TourismRegion("11-11140", "중구", 37.56, 126.99)
		));
		when(recommendationTourismContentService.findContentsByRegionCodes(
				List.of("11-11110", "11-11140"),
				3
		)).thenReturn(java.util.Map.of(
				"11-11110",
				List.of(tourismContent("127974", "을숙도 공원"))
		));

		PersonalizedRecommendationResponse response = service().getLatestRecommendations(
				List.of(TourismTheme.FOOD, TourismTheme.NATURE, TourismTheme.FOOD),
				10
		);

		assertThat(response.referenceDate()).isEqualTo(referenceDate);
		assertThat(response.selectedThemes()).extracting(theme -> theme.theme())
				.containsExactly("NATURE", "FOOD");
		assertThat(response.recommendations()).extracting(item -> item.regionCode())
				.containsExactly("11-11110", "11-11140");
		assertThat(response.recommendations().getFirst().normalizedThemeDemand()).isEqualTo(0.65);
		assertThat(response.recommendations().getFirst().recommendationScore()).isEqualTo(0.695);
		assertThat(response.recommendations().getFirst().recommendationScorePercent()).isEqualTo(70);
		assertThat(response.recommendations().getFirst().themeScores()).hasSize(2);
		assertThat(response.recommendations().getFirst().tourismContents())
				.extracting(RecommendedTourismContentResponse::title)
				.containsExactly("을숙도 공원");
		verify(tourismContentBackfillService).requestBackfillForMissingImages(
				List.of("11-11110", "11-11140"),
				java.util.Map.of(
						"11-11110",
						List.of(tourismContent("127974", "을숙도 공원"))
				)
		);
	}

	@Test
	void excludesRegionMissingOneOfSelectedThemes() {
		LocalDate referenceDate = LocalDate.of(2025, 9, 1);
		when(resourceDemandRepository.findReferenceDatesByThemeDescending(TourismTheme.NATURE))
				.thenReturn(List.of(referenceDate));
		when(resourceDemandRepository.findReferenceDatesByThemeDescending(TourismTheme.FOOD))
				.thenReturn(List.of(referenceDate));
		when(stayStrengthRepository.findReferenceDatesDescending()).thenReturn(List.of(referenceDate));
		when(resourceDemandRepository.findByReferenceDateAndThemeIn(
				referenceDate,
				List.of(TourismTheme.NATURE, TourismTheme.FOOD)
		)).thenReturn(List.of(
				demand("11-11110", "종로구", "1205", 0.8, referenceDate)
		));
		when(stayStrengthRepository.findByReferenceDate(referenceDate)).thenReturn(List.of(
				stayStrength("11-11110", "종로구", 0.2, referenceDate)
		));
		when(regionRepository.findAll()).thenReturn(List.of());

		PersonalizedRecommendationResponse response = service().getLatestRecommendations(
				List.of(TourismTheme.NATURE, TourismTheme.FOOD),
				10
		);

		assertThat(response.referenceDate()).isEqualTo(referenceDate);
		assertThat(response.recommendations()).isEmpty();
	}

	@Test
	void returnsEmptyWhenThereIsNoCommonReferenceMonth() {
		when(resourceDemandRepository.findReferenceDatesByThemeDescending(TourismTheme.NATURE))
				.thenReturn(List.of(LocalDate.of(2025, 9, 1)));
		when(stayStrengthRepository.findReferenceDatesDescending())
				.thenReturn(List.of(LocalDate.of(2025, 10, 1)));

		PersonalizedRecommendationResponse response = service().getLatestRecommendations(
				List.of(TourismTheme.NATURE),
				10
		);

		assertThat(response.count()).isZero();
		assertThat(response.selectedThemes()).extracting(theme -> theme.theme())
				.containsExactly("NATURE");
	}

	@Test
	void rejectsEmptyThemesAndInvalidLimit() {
		assertThatThrownBy(() -> service().getLatestRecommendations(List.of(), 10))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("테마를 하나 이상 선택하세요.");
		assertThatThrownBy(() -> service().getLatestRecommendations(List.of(TourismTheme.NATURE), 101))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("limit은 1 이상 100 이하로 입력하세요.");
	}

	private PersonalizedRecommendationService service() {
		return new PersonalizedRecommendationService(
				resourceDemandRepository,
				stayStrengthRepository,
				regionRepository,
				recommendationTourismContentService,
				tourismContentBackfillService
		);
	}

	private RecommendedTourismContentResponse tourismContent(String contentId, String title) {
		return new RecommendedTourismContentResponse(
				contentId,
				"12",
				title,
				"부산광역시 사하구",
				"https://example.com/image.jpg",
				"https://example.com/thumb.jpg",
				35.1,
				128.9,
				"26-380",
				"NA040500"
		);
	}

	private TourismResourceDemand demand(
			String regionCode,
			String regionName,
			String indicatorCode,
			double normalizedValue,
			LocalDate referenceDate
	) {
		ResourceDemandType resourceType = indicatorCode.startsWith("12")
				? ResourceDemandType.CULTURE
				: ResourceDemandType.SERVICE;
		return new TourismResourceDemand(
				regionCode,
				regionName,
				resourceType,
				indicatorCode,
				"테스트 지표",
				80,
				normalizedValue,
				referenceDate
		);
	}

	private TourismStayStrength stayStrength(
			String regionCode,
			String regionName,
			double normalizedScore,
			LocalDate referenceDate
	) {
		return new TourismStayStrength(regionCode, regionName, 80, normalizedScore, referenceDate);
	}
}
