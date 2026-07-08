package com.trip.whereU.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.trip.whereU.recommendation.dto.RecommendedTourismContentResponse;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.entity.TourismRegionTourApiMapping;
import com.trip.whereU.tourism.repository.TourismContentRepository;
import com.trip.whereU.tourism.repository.TourismRegionTourApiMappingRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendationTourismContentServiceTest {

	@Mock
	private TourismRegionTourApiMappingRepository mappingRepository;
	@Mock
	private TourismContentRepository tourismContentRepository;

	@Test
	void attachesTourismContentsUsingDerivedLegalDongCodeWhenExplicitMappingIsAbsent() {
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("26-26380")))
				.thenReturn(List.of());
		when(tourismContentRepository.findByLegalDongCodeIn(List.of("26-380")))
				.thenReturn(List.of(
						content("3027228", "다대포생태탐방로", "26-380", "NA040500", "20260420173053"),
						content("127974", "을숙도 공원", "26-380", "NA040500", "20260309093631"),
						content("2783344", "낙동강 생태탐방선", "26-380", "NA040500", "20250306164914")
				));

		Map<String, List<RecommendedTourismContentResponse>> result = service()
				.findContentsByRegionCodes(List.of("26-26380"), 3);

		assertThat(result.get("26-26380")).extracting(RecommendedTourismContentResponse::title)
				.containsExactly("다대포생태탐방로", "을숙도 공원", "낙동강 생태탐방선");
	}

	@Test
	void explicitMappingCanRestrictCategoryAndLimitCards() {
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("26-26380")))
				.thenReturn(List.of(new TourismRegionTourApiMapping(
						"26-26380",
						"부산광역시 사하구",
						"26-380",
						"12",
						"NA040500",
						true
				)));
		when(tourismContentRepository.findByLegalDongCodeIn(List.of("26-380")))
				.thenReturn(List.of(
						content("1", "첫 번째", "26-380", "NA040500", "20260420173053"),
						content("2", "다른 분류", "26-380", "NA030000", "20260420173054"),
						content("3", "두 번째", "26-380", "NA040500", "20260420173052")
				));

		Map<String, List<RecommendedTourismContentResponse>> result = service()
				.findContentsByRegionCodes(List.of("26-26380"), 1);

		assertThat(result.get("26-26380")).extracting(RecommendedTourismContentResponse::title)
				.containsExactly("첫 번째");
	}

	private RecommendationTourismContentService service() {
		return new RecommendationTourismContentService(mappingRepository, tourismContentRepository);
	}

	private TourismContent content(
			String contentId,
			String title,
			String legalDongCode,
			String categoryCode,
			String modifiedTime
	) {
		return new TourismContent(
				contentId,
				"12",
				title,
				"부산광역시 사하구",
				null,
				35.1,
				128.9,
				null,
				null,
				legalDongCode,
				categoryCode,
				null,
				null,
				null,
				"https://example.com/image.jpg",
				"https://example.com/thumb.jpg",
				null,
				null,
				modifiedTime
		);
	}
}
