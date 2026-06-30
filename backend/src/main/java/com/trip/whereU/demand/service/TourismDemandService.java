package com.trip.whereU.demand.service;

import com.trip.whereU.demand.client.TourismDemandOpenApiClient;
import com.trip.whereU.demand.config.TourismOpenApiProperties;
import com.trip.whereU.demand.dto.TourismDemandOpenApiItem;
import com.trip.whereU.demand.dto.TourismDemandOpenApiPage;
import com.trip.whereU.demand.dto.TourismDemandResponse;
import com.trip.whereU.demand.dto.TourismDemandSyncResponse;
import com.trip.whereU.demand.repository.TourismDemandRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourismDemandService {

	private static final int DEFAULT_PAGE_NO = 1;
	private static final int DEFAULT_NUM_OF_ROWS = 100;

	private final TourismDemandRepository tourismDemandRepository;
	private final TourismDemandOpenApiClient tourismDemandOpenApiClient;
	private final TourismDemandPersistenceService tourismDemandPersistenceService;
	private final TourismOpenApiProperties properties;

	public TourismDemandService(
			TourismDemandRepository tourismDemandRepository,
			TourismDemandOpenApiClient tourismDemandOpenApiClient,
			TourismDemandPersistenceService tourismDemandPersistenceService,
			TourismOpenApiProperties properties
	) {
		this.tourismDemandRepository = tourismDemandRepository;
		this.tourismDemandOpenApiClient = tourismDemandOpenApiClient;
		this.tourismDemandPersistenceService = tourismDemandPersistenceService;
		this.properties = properties;
	}

	public TourismDemandSyncResponse syncDemandData() {
		List<TourismDemandOpenApiItem> items = fetchAllDistrictDemandItems();

		if (items.isEmpty()) {
			return new TourismDemandSyncResponse(0, 0, LocalDate.now());
		}

		int savedCount = tourismDemandPersistenceService.saveDemandItems(items);

		LocalDate latestReferenceDate = items.stream()
				.map(TourismDemandOpenApiItem::referenceDate)
				.max(Comparator.naturalOrder())
				.orElse(LocalDate.now());

		return new TourismDemandSyncResponse(items.size(), savedCount, latestReferenceDate);
	}

	private List<TourismDemandOpenApiItem> fetchAllDistrictDemandItems() {
		List<TourismDemandOpenApiItem> items = new ArrayList<>();
		for (String areaCode : properties.demand().areaCodes()) {
			int pageNo = DEFAULT_PAGE_NO;
			TourismDemandOpenApiPage page;
			do {
				page = tourismDemandOpenApiClient.fetchDemandPage(areaCode, pageNo, DEFAULT_NUM_OF_ROWS);
				page.items().stream()
						.filter(item -> !item.isAreaAggregate())
						.forEach(items::add);
				pageNo++;
			} while (hasNextPage(pageNo, page.totalCount()));
		}
		return items;
	}

	private boolean hasNextPage(int nextPageNo, int totalCount) {
		return (long) (nextPageNo - 1) * DEFAULT_NUM_OF_ROWS < totalCount;
	}

	@Transactional(readOnly = true)
	public List<TourismDemandResponse> getAllDemandSignals() {
		return tourismDemandRepository.findAll().stream()
				.map(TourismDemandResponse::from)
				.toList();
	}

	public String getMaskedOpenApiRequestUri() {
		return tourismDemandOpenApiClient.buildMaskedDemandUri(DEFAULT_PAGE_NO, DEFAULT_NUM_OF_ROWS);
	}

	@Transactional(readOnly = true)
	public List<TourismDemandResponse> getDemandSignalsInBounds(
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude
	) {
		return tourismDemandRepository
				.findByLatitudeBetweenAndLongitudeBetween(minLatitude, maxLatitude, minLongitude, maxLongitude)
				.stream()
				.map(TourismDemandResponse::from)
				.toList();
	}

}
