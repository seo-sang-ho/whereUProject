package com.trip.whereU.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import com.trip.whereU.recommendation.dto.RecommendedTourismContentResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationResponse;
import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import com.trip.whereU.servicedemand.repository.TourismServiceDemandRepository;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValueRecommendationServiceTest {

	@Mock
	private TourismServiceDemandRepository serviceDemandRepository;
	@Mock
	private TourismStayStrengthRepository stayStrengthRepository;
	@Mock
	private TourismRegionRepository regionRepository;
	@Mock
	private RecommendationTourismContentService recommendationTourismContentService;

	@Test
	void ranksHighServiceDemandAndLowStayStrengthFirstUsingLatestCommonMonth() {
		LocalDate august = LocalDate.of(2025, 8, 1);
		LocalDate september = LocalDate.of(2025, 9, 1);
		LocalDate october = LocalDate.of(2025, 10, 1);
		when(serviceDemandRepository.findReferenceDatesDescending()).thenReturn(List.of(september, august));
		when(stayStrengthRepository.findReferenceDatesDescending()).thenReturn(List.of(october, september));
		when(serviceDemandRepository.findByReferenceDate(september)).thenReturn(List.of(
				serviceDemand("11-11110", "종로구", 0.9, september),
				serviceDemand("11-11140", "중구", 0.8, september)
		));
		when(stayStrengthRepository.findByReferenceDate(september)).thenReturn(List.of(
				stayStrength("11-11110", "종로구", 0.2, september),
				stayStrength("11-11140", "중구", 0.9, september)
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

		ValueRecommendationResponse response = service().getLatestValueRecommendations(10);

		assertThat(response.referenceDate()).isEqualTo(september);
		assertThat(response.count()).isEqualTo(2);
		assertThat(response.recommendations()).extracting(item -> item.regionCode())
				.containsExactly("11-11110", "11-11140");
		assertThat(response.recommendations().getFirst().recommendationScore()).isEqualTo(0.87);
		assertThat(response.recommendations().getFirst().recommendationScorePercent()).isEqualTo(87);
		assertThat(response.recommendations().getFirst().rank()).isEqualTo(1);
		assertThat(response.recommendations().getFirst().tourismContents())
				.extracting(RecommendedTourismContentResponse::title)
				.containsExactly("을숙도 공원");
	}

	@Test
	void excludesRegionWithoutMatchingStayStrength() {
		LocalDate referenceDate = LocalDate.of(2025, 9, 1);
		when(serviceDemandRepository.findReferenceDatesDescending()).thenReturn(List.of(referenceDate));
		when(stayStrengthRepository.findReferenceDatesDescending()).thenReturn(List.of(referenceDate));
		when(serviceDemandRepository.findByReferenceDate(referenceDate)).thenReturn(List.of(
				serviceDemand("11-11110", "종로구", 0.9, referenceDate)
		));
		when(stayStrengthRepository.findByReferenceDate(referenceDate)).thenReturn(List.of());
		when(regionRepository.findAll()).thenReturn(List.of());

		ValueRecommendationResponse response = service().getLatestValueRecommendations(10);

		assertThat(response.referenceDate()).isEqualTo(referenceDate);
		assertThat(response.recommendations()).isEmpty();
	}

	@Test
	void returnsEmptyWhenThereIsNoCommonReferenceMonth() {
		when(serviceDemandRepository.findReferenceDatesDescending())
				.thenReturn(List.of(LocalDate.of(2025, 9, 1)));
		when(stayStrengthRepository.findReferenceDatesDescending())
				.thenReturn(List.of(LocalDate.of(2025, 10, 1)));

		assertThat(service().getLatestValueRecommendations(10).count()).isZero();
	}

	@Test
	void rejectsInvalidLimit() {
		assertThatThrownBy(() -> service().getLatestValueRecommendations(0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("limit은 1 이상 100 이하로 입력하세요.");
	}

	private ValueRecommendationService service() {
		return new ValueRecommendationService(
				serviceDemandRepository,
				stayStrengthRepository,
				regionRepository,
				recommendationTourismContentService
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

	private TourismServiceDemand serviceDemand(
			String regionCode,
			String regionName,
			double normalizedScore,
			LocalDate referenceDate
	) {
		return new TourismServiceDemand(regionCode, regionName, 80, normalizedScore, referenceDate);
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
