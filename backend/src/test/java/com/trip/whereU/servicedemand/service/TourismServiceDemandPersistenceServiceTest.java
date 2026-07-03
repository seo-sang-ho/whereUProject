package com.trip.whereU.servicedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiItem;
import com.trip.whereU.servicedemand.entity.ServiceDemandLevel;
import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import com.trip.whereU.servicedemand.repository.TourismServiceDemandRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismServiceDemandPersistenceServiceTest {

	@Mock
	private TourismServiceDemandRepository repository;

	@Test
	void normalizesNationwideItemsBeforeSaving() {
		TourismServiceDemandPersistenceService service = new TourismServiceDemandPersistenceService(repository);
		when(repository.findAllByReferenceDateIn(anyCollection())).thenReturn(List.of());
		List<TourismServiceDemandOpenApiItem> items = List.of(
				item("11-11110", 60),
				item("11-11140", 75),
				item("11-11170", 90)
		);

		int savedCount = service.saveItems(items);

		ArgumentCaptor<List<TourismServiceDemand>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		assertThat(savedCount).isEqualTo(3);
		assertThat(captor.getValue()).extracting(TourismServiceDemand::getNormalizedServiceDemand)
				.containsExactly(0.0, 0.5, 1.0);
		assertThat(captor.getValue()).extracting(TourismServiceDemand::getLevel)
				.containsExactly(ServiceDemandLevel.LOW, ServiceDemandLevel.MEDIUM, ServiceDemandLevel.HIGH);
	}

	private TourismServiceDemandOpenApiItem item(String regionCode, double value) {
		return new TourismServiceDemandOpenApiItem(
				regionCode,
				"테스트 지역",
				regionCode.substring(regionCode.indexOf('-') + 1),
				value,
				LocalDate.of(2025, 9, 1)
		);
	}
}
