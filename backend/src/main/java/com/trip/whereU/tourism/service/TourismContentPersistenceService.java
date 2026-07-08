package com.trip.whereU.tourism.service;

import com.trip.whereU.tourism.dto.TourismContentOpenApiItem;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.repository.TourismContentRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourismContentPersistenceService {

	private final TourismContentRepository repository;

	public TourismContentPersistenceService(TourismContentRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public int saveItems(List<TourismContentOpenApiItem> items) {
		if (items.isEmpty()) {
			return 0;
		}

		Map<String, TourismContent> existing = indexByContentId(repository.findByContentIdIn(
				items.stream().map(TourismContentOpenApiItem::contentId).toList()
		));
		List<TourismContent> contents = items.stream()
				.map(item -> upsert(item, existing))
				.toList();
		repository.saveAll(contents);
		return contents.size();
	}

	private TourismContent upsert(
			TourismContentOpenApiItem item,
			Map<String, TourismContent> existing
	) {
		TourismContent content = existing.get(item.contentId());
		if (content == null) {
			return new TourismContent(
					item.contentId(),
					item.contentTypeId(),
					item.title(),
					item.address(),
					item.detailAddress(),
					item.latitude(),
					item.longitude(),
					item.areaCode(),
					item.sigunguCode(),
					item.legalDongCode(),
					item.categoryCode(),
					item.cat1(),
					item.cat2(),
					item.cat3(),
					item.firstImage(),
					item.firstImage2(),
					item.tel(),
					item.zipcode(),
					item.modifiedTime()
			);
		}
		content.update(
				item.contentTypeId(),
				item.title(),
				item.address(),
				item.detailAddress(),
				item.latitude(),
				item.longitude(),
				item.areaCode(),
				item.sigunguCode(),
				item.legalDongCode(),
				item.categoryCode(),
				item.cat1(),
				item.cat2(),
				item.cat3(),
				item.firstImage(),
				item.firstImage2(),
				item.tel(),
				item.zipcode(),
				item.modifiedTime()
		);
		return content;
	}

	private Map<String, TourismContent> indexByContentId(Collection<TourismContent> contents) {
		return contents.stream()
				.collect(Collectors.toMap(TourismContent::getContentId, Function.identity()));
	}
}
