package com.trip.whereU.tourism.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.tourism.client.TourismContentOpenApiClient;
import com.trip.whereU.tourism.dto.TourismContentOpenApiItem;
import com.trip.whereU.tourism.dto.TourismContentOpenApiPage;
import com.trip.whereU.tourism.dto.TourismContentResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.repository.TourismContentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TourismContentServiceTest {

	@Mock
	private TourismContentOpenApiClient openApiClient;
	@Mock
	private TourismContentPersistenceService persistenceService;
	@Mock
	private TourismContentRepository repository;

	@Test
	void syncFetchesAllPagesAndSavesItems() {
		when(openApiClient.fetchAreaBasedPage("12", "C", "26", "380", "NA", "NA04", "NA040500", 1, 2))
				.thenReturn(new TourismContentOpenApiPage(List.of(item("1"), item("2")), 1, 2, 3));
		when(openApiClient.fetchAreaBasedPage("12", "C", "26", "380", "NA", "NA04", "NA040500", 2, 2))
				.thenReturn(new TourismContentOpenApiPage(List.of(item("3")), 2, 2, 3));
		when(persistenceService.saveItems(any())).thenReturn(3);

		TourismContentSyncResponse response = service().sync(
				"12",
				"C",
				"26",
				"380",
				"NA",
				"NA04",
				"NA040500",
				2
		);

		assertThat(response.fetchedCount()).isEqualTo(3);
		assertThat(response.savedCount()).isEqualTo(3);
		assertThat(response.pageRequestCount()).isEqualTo(2);
		verify(persistenceService).saveItems(List.of(item("1"), item("2"), item("3")));
	}

	@Test
	void returnsStoredContentPage() {
		TourismContent content = content("126508", "경복궁");
		when(repository.findAll(any(Specification.class), any(PageRequest.class)))
				.thenReturn(new PageImpl<>(List.of(content), PageRequest.of(0, 10), 1));

		Page<TourismContentResponse> response = service()
				.getContents("11", "11110", "11-11110", "VE010100", "12", PageRequest.of(0, 10));

		assertThat(response.getTotalElements()).isEqualTo(1);
		assertThat(response.getContent().getFirst().contentId()).isEqualTo("126508");
		assertThat(response.getContent().getFirst().title()).isEqualTo("경복궁");
	}

	@Test
	void returnsStoredContentDetailByContentId() {
		when(repository.findByContentId("126508")).thenReturn(Optional.of(content("126508", "경복궁")));

		TourismContentResponse response = service().getContent("126508");

		assertThat(response.contentId()).isEqualTo("126508");
		assertThat(response.title()).isEqualTo("경복궁");
	}

	@Test
	void rejectsInvalidPageSize() {
		assertThatThrownBy(() -> service().sync(null, null, null, null, null, null, null, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("pageSize는 1 이상 1000 이하로 입력하세요.");
	}

	private TourismContentService service() {
		return new TourismContentService(openApiClient, persistenceService, repository);
	}

	private TourismContentOpenApiItem item(String contentId) {
		return new TourismContentOpenApiItem(
				contentId,
				"12",
				"관광지 " + contentId,
				"주소",
				null,
				37.5,
				126.9,
				"11",
				"11110",
				"11-11110",
				"VE010100",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"20260708103000"
		);
	}

	private TourismContent content(String contentId, String title) {
		return new TourismContent(
				contentId,
				"12",
				title,
				"서울특별시 종로구 사직로 161",
				null,
				37.578822,
				126.976993,
				"11",
				"11110",
				"11-11110",
				"VE010100",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"20260708103000"
		);
	}
}
