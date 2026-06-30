package com.trip.whereU.demand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.demand.dto.TourismDemandOpenApiItem;
import com.trip.whereU.demand.entity.DemandSignal;
import com.trip.whereU.demand.entity.TourismDemand;
import com.trip.whereU.demand.repository.TourismDemandRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismDemandPersistenceServiceTest {

	@Mock
	private TourismDemandRepository tourismDemandRepository;

	@Test
	void normalizesAgainstAllCollectedDistrictsBeforeSaving() {
		TourismDemandPersistenceService service = new TourismDemandPersistenceService(tourismDemandRepository);
		when(tourismDemandRepository.findAllByReferenceDateIn(anyCollection())).thenReturn(List.of());
		List<TourismDemandOpenApiItem> items = List.of(
				item("11-11110", 70),
				item("11-11140", 80),
				item("11-11170", 90)
		);

		int savedCount = service.saveDemandItems(items);

		ArgumentCaptor<List<TourismDemand>> captor = ArgumentCaptor.forClass(List.class);
		verify(tourismDemandRepository).saveAll(captor.capture());
		assertThat(savedCount).isEqualTo(3);
		assertThat(captor.getValue()).extracting(TourismDemand::getNormalizedDemandScore)
				.containsExactly(0.0, 0.5, 1.0);
		assertThat(captor.getValue()).extracting(TourismDemand::getDemandSignal)
				.containsExactly(DemandSignal.SMOOTH, DemandSignal.NORMAL, DemandSignal.CROWDED);
	}

	private TourismDemandOpenApiItem item(String regionCode, double score) {
		return new TourismDemandOpenApiItem(
				regionCode,
				"테스트 지역",
				regionCode.substring(regionCode.indexOf('-') + 1),
				score,
				null,
				null,
				LocalDate.of(2025, 9, 1)
		);
	}
}
