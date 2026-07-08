package com.trip.whereU.tourism.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.tourism.dto.TourismContentOpenApiItem;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.repository.TourismContentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismContentPersistenceServiceTest {

	@Mock
	private TourismContentRepository repository;

	@Test
	void upsertsTourismContentsByContentId() {
		TourismContent existing = new TourismContent(
				"126508",
				"12",
				"경복궁",
				"옛 주소",
				null,
				37.57,
				126.97,
				"11",
				"11110",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"20260701000000"
		);
		when(repository.findByContentIdIn(List.of("126508"))).thenReturn(List.of(existing));
		TourismContentPersistenceService service = new TourismContentPersistenceService(repository);

		int savedCount = service.saveItems(List.of(item()));

		ArgumentCaptor<List<TourismContent>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		assertThat(savedCount).isEqualTo(1);
		assertThat(captor.getValue()).hasSize(1);
		assertThat(captor.getValue().getFirst()).isSameAs(existing);
		assertThat(existing.getTitle()).isEqualTo("경복궁 야간개장");
		assertThat(existing.getAddress()).isEqualTo("서울특별시 종로구 사직로 161");
		assertThat(existing.getLegalDongCode()).isEqualTo("11-11110");
		assertThat(existing.getCategoryCode()).isEqualTo("VE010100");
	}

	private TourismContentOpenApiItem item() {
		return new TourismContentOpenApiItem(
				"126508",
				"12",
				"경복궁 야간개장",
				"서울특별시 종로구 사직로 161",
				"(세종로)",
				37.578822,
				126.976993,
				"",
				"",
				"11-11110",
				"VE010100",
				null,
				null,
				null,
				"https://example.com/main.jpg",
				"https://example.com/thumb.jpg",
				"02-3700-3900",
				"03045",
				"20260708103000"
		);
	}
}
