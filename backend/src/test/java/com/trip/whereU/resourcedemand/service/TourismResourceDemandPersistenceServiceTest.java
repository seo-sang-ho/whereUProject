package com.trip.whereU.resourcedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiItem;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.entity.TourismResourceDemand;
import com.trip.whereU.resourcedemand.entity.TourismTheme;
import com.trip.whereU.resourcedemand.repository.TourismResourceDemandRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismResourceDemandPersistenceServiceTest {

	@Mock
	private TourismResourceDemandRepository repository;

	@Test
	void normalizesEachIndicatorSeparatelyAndAssignsTheme() {
		when(repository.findAllByReferenceDateIn(anyCollection())).thenReturn(List.of());
		List<TourismResourceDemandOpenApiItem> items = List.of(
				item("11-11110", ResourceDemandType.CULTURE, "1205", 60),
				item("11-11140", ResourceDemandType.CULTURE, "1205", 80),
				item("11-11110", ResourceDemandType.SERVICE, "1111", 20),
				item("11-11140", ResourceDemandType.SERVICE, "1111", 30)
		);

		int savedCount = new TourismResourceDemandPersistenceService(repository).saveItems(items);

		ArgumentCaptor<List<TourismResourceDemand>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		assertThat(savedCount).isEqualTo(4);
		assertThat(captor.getValue()).extracting(TourismResourceDemand::getNormalizedValue)
				.containsExactly(0.0, 1.0, 0.0, 1.0);
		assertThat(captor.getValue()).extracting(TourismResourceDemand::getTheme)
				.containsExactly(TourismTheme.NATURE, TourismTheme.NATURE, TourismTheme.FOOD, TourismTheme.FOOD);
	}

	private TourismResourceDemandOpenApiItem item(
			String regionCode,
			ResourceDemandType resourceType,
			String indicatorCode,
			double value
	) {
		return new TourismResourceDemandOpenApiItem(
				regionCode,
				"테스트 지역",
				regionCode.substring(regionCode.indexOf('-') + 1),
				resourceType,
				indicatorCode,
				"테스트 지표",
				value,
				LocalDate.of(2025, 9, 1)
		);
	}
}
