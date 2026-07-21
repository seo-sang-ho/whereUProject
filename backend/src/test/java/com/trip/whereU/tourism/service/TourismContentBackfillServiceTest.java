package com.trip.whereU.tourism.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.recommendation.dto.RecommendedTourismContentResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.repository.TourismRegionTourApiMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismContentBackfillServiceTest {

	@Mock
	private TourismContentService tourismContentService;
	@Mock
	private TourismRegionTourApiMappingRepository mappingRepository;

	@Test
	void syncsOnlyRegionsWithoutImageReadyTourismContents() {
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("28-28110", "26-26380")))
				.thenReturn(List.of());
		when(tourismContentService.sync("12", "C", "28", "110", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(24, 24, 3));
		when(tourismContentService.sync("12", "C", "26", "380", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(3, 3, 1));

		service().requestBackfillForMissingImages(
				List.of("11-11680", "28-28110", "26-26380"),
				Map.of(
						"11-11680",
						List.of(content("1", "서울 관광지", "https://example.com/seoul.jpg")),
						"28-28110",
						List.of(content("2", "인천 관광지", null))
				)
		);

		verify(tourismContentService, never())
				.sync("12", "C", "11", "680", null, null, null, 10);
		verify(tourismContentService)
				.sync("12", "C", "28", "110", null, null, null, 10);
		verify(tourismContentService)
				.sync("12", "C", "26", "380", null, null, null, 10);
	}

	@Test
	void skipsSameRegionWithinCooldown() {
		when(mappingRepository.findByEnabledTrueAndRegionCodeIn(List.of("28-28110")))
				.thenReturn(List.of());
		when(tourismContentService.sync("12", "C", "28", "110", null, null, null, 10))
				.thenReturn(new TourismContentSyncResponse(24, 24, 3));
		TourismContentBackfillService service = service();

		service.requestBackfillForMissingImages(List.of("28-28110"), Map.of());
		service.requestBackfillForMissingImages(List.of("28-28110"), Map.of());

		verify(tourismContentService)
				.sync("12", "C", "28", "110", null, null, null, 10);
	}

	private TourismContentBackfillService service() {
		return new TourismContentBackfillService(
				tourismContentService,
				mappingRepository,
				Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneId.of("Asia/Seoul"))
		);
	}

	private RecommendedTourismContentResponse content(String contentId, String title, String firstImage) {
		return new RecommendedTourismContentResponse(
				contentId,
				"12",
				title,
				"테스트 주소",
				firstImage,
				null,
				37.5,
				127.0,
				"11-680",
				"NA040500"
		);
	}
}
