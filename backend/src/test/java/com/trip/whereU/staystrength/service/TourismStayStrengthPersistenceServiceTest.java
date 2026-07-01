package com.trip.whereU.staystrength.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiItem;
import com.trip.whereU.staystrength.entity.StayStrengthLevel;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismStayStrengthPersistenceServiceTest {

	@Mock
	private TourismStayStrengthRepository tourismStayStrengthRepository;

	@Test
	void normalizesAgainstAllCollectedDistrictsBeforeSaving() {
		TourismStayStrengthPersistenceService service = new TourismStayStrengthPersistenceService(tourismStayStrengthRepository);
		when(tourismStayStrengthRepository.findAllByReferenceDateIn(anyCollection())).thenReturn(List.of());
		List<TourismStayStrengthOpenApiItem> items = List.of(
				item("11-11110", 70),
				item("11-11140", 80),
				item("11-11170", 90)
		);

		int savedCount = service.saveStayStrengthItems(items);

		ArgumentCaptor<List<TourismStayStrength>> captor = ArgumentCaptor.forClass(List.class);
		verify(tourismStayStrengthRepository).saveAll(captor.capture());
		assertThat(savedCount).isEqualTo(3);
		assertThat(captor.getValue()).extracting(TourismStayStrength::getNormalizedStayStrength)
				.containsExactly(0.0, 0.5, 1.0);
		assertThat(captor.getValue()).extracting(TourismStayStrength::getLevel)
				.containsExactly(StayStrengthLevel.LOW, StayStrengthLevel.MEDIUM, StayStrengthLevel.HIGH);
	}

	private TourismStayStrengthOpenApiItem item(String regionCode, double score) {
		return new TourismStayStrengthOpenApiItem(
				regionCode,
				"테스트 지역",
				regionCode.substring(regionCode.indexOf('-') + 1),
				score,
				LocalDate.of(2025, 9, 1)
		);
	}
}
