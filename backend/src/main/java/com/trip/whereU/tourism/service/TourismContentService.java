package com.trip.whereU.tourism.service;

import com.trip.whereU.tourism.client.TourismContentOpenApiClient;
import com.trip.whereU.tourism.dto.TourismContentOpenApiItem;
import com.trip.whereU.tourism.dto.TourismContentOpenApiPage;
import com.trip.whereU.tourism.dto.TourismContentResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.repository.TourismContentRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TourismContentService {

	private static final int FIRST_PAGE = 1;
	private static final int MAX_PAGE_SIZE = 1000;

	private final TourismContentOpenApiClient openApiClient;
	private final TourismContentPersistenceService persistenceService;
	private final TourismContentRepository repository;

	public TourismContentService(
			TourismContentOpenApiClient openApiClient,
			TourismContentPersistenceService persistenceService,
			TourismContentRepository repository
	) {
		this.openApiClient = openApiClient;
		this.persistenceService = persistenceService;
		this.repository = repository;
	}

	public TourismContentSyncResponse sync(
			String areaCode,
			String sigunguCode,
			String contentTypeId,
			int pageSize
	) {
		validatePageSize(pageSize);
		List<TourismContentOpenApiItem> items = new ArrayList<>();
		int pageNo = FIRST_PAGE;
		TourismContentOpenApiPage page;
		do {
			page = openApiClient.fetchAreaBasedPage(areaCode, sigunguCode, contentTypeId, pageNo, pageSize);
			items.addAll(page.items());
			pageNo++;
		} while ((long) (pageNo - 1) * pageSize < page.totalCount());

		int savedCount = persistenceService.saveItems(items);
		return new TourismContentSyncResponse(items.size(), savedCount, pageNo - FIRST_PAGE);
	}

	@Transactional(readOnly = true)
	public Page<TourismContentResponse> getContents(
			String areaCode,
			String sigunguCode,
			String legalDongCode,
			String categoryCode,
			String contentTypeId,
			Pageable pageable
	) {
		return repository.findAll(
				matches(areaCode, sigunguCode, legalDongCode, categoryCode, contentTypeId),
				pageable
		).map(TourismContentResponse::from);
	}

	@Transactional(readOnly = true)
	public TourismContentResponse getContent(String contentId) {
		return repository.findByContentId(contentId)
				.map(TourismContentResponse::from)
				.orElseThrow(() -> new IllegalArgumentException("저장된 관광정보를 찾을 수 없습니다."));
	}

	private Specification<TourismContent> matches(
			String areaCode,
			String sigunguCode,
			String legalDongCode,
			String categoryCode,
			String contentTypeId
	) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			addEquals(predicates, criteriaBuilder, root.get("areaCode"), areaCode);
			addEquals(predicates, criteriaBuilder, root.get("sigunguCode"), sigunguCode);
			addEquals(predicates, criteriaBuilder, root.get("legalDongCode"), legalDongCode);
			addEquals(predicates, criteriaBuilder, root.get("categoryCode"), categoryCode);
			addEquals(predicates, criteriaBuilder, root.get("contentTypeId"), contentTypeId);
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private void addEquals(
			List<Predicate> predicates,
			jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
			jakarta.persistence.criteria.Path<String> path,
			String value
	) {
		if (StringUtils.hasText(value)) {
			predicates.add(criteriaBuilder.equal(path, value));
		}
	}

	private void validatePageSize(int pageSize) {
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("pageSize는 1 이상 1000 이하로 입력하세요.");
		}
	}
}
