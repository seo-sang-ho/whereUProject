package com.trip.whereU.servicedemand.service;

import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import com.trip.whereU.servicedemand.client.TourismServiceDemandOpenApiClient;
import com.trip.whereU.servicedemand.config.TourismResourceDemandApiProperties;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandLatestResponse;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiItem;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiPage;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandResponse;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandSyncResponse;
import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import com.trip.whereU.servicedemand.repository.TourismServiceDemandRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourismServiceDemandService {

	private static final int FIRST_PAGE = 1;
	private static final int PAGE_SIZE = 100;

	private final TourismServiceDemandRepository repository;
	private final TourismServiceDemandOpenApiClient openApiClient;
	private final TourismServiceDemandPersistenceService persistenceService;
	private final TourismRegionRepository regionRepository;
	private final TourismResourceDemandApiProperties properties;

	public TourismServiceDemandService(
			TourismServiceDemandRepository repository,
			TourismServiceDemandOpenApiClient openApiClient,
			TourismServiceDemandPersistenceService persistenceService,
			TourismRegionRepository regionRepository,
			TourismResourceDemandApiProperties properties
	) {
		this.repository = repository;
		this.openApiClient = openApiClient;
		this.persistenceService = persistenceService;
		this.regionRepository = regionRepository;
		this.properties = properties;
	}

	public TourismServiceDemandSyncResponse sync() {
		List<TourismServiceDemandOpenApiItem> items = fetchAllDistrictItems();
		if (items.isEmpty()) {
			return new TourismServiceDemandSyncResponse(0, 0, LocalDate.now());
		}

		int savedCount = persistenceService.saveItems(items);
		LocalDate referenceDate = items.stream()
				.map(TourismServiceDemandOpenApiItem::referenceDate)
				.max(Comparator.naturalOrder())
				.orElse(LocalDate.now());
		return new TourismServiceDemandSyncResponse(items.size(), savedCount, referenceDate);
	}

	private List<TourismServiceDemandOpenApiItem> fetchAllDistrictItems() {
		List<TourismServiceDemandOpenApiItem> items = new ArrayList<>();
		for (String areaCode : properties.serviceDemand().areaCodes()) {
			int pageNo = FIRST_PAGE;
			TourismServiceDemandOpenApiPage page;
			do {
				page = openApiClient.fetchPage(areaCode, pageNo, PAGE_SIZE);
				page.items().stream()
						.filter(item -> !item.isAreaAggregate())
						.forEach(items::add);
				pageNo++;
			} while ((long) (pageNo - 1) * PAGE_SIZE < page.totalCount());
		}
		return items;
	}

	@Transactional(readOnly = true)
	public TourismServiceDemandLatestResponse getLatest() {
		Optional<LocalDate> latestDate = repository.findLatestReferenceDate();
		if (latestDate.isEmpty()) {
			return TourismServiceDemandLatestResponse.empty();
		}
		return TourismServiceDemandLatestResponse.of(
				latestDate.get(),
				toResponses(repository.findByReferenceDate(latestDate.get()), regionRepository.findAll())
		);
	}

	@Transactional(readOnly = true)
	public TourismServiceDemandLatestResponse getLatestInBounds(
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude
	) {
		validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);
		Optional<LocalDate> latestDate = repository.findLatestReferenceDate();
		if (latestDate.isEmpty()) {
			return TourismServiceDemandLatestResponse.empty();
		}

		List<TourismRegion> regions = regionRepository
				.findByLatitudeBetweenAndLongitudeBetween(minLatitude, maxLatitude, minLongitude, maxLongitude);
		if (regions.isEmpty()) {
			return TourismServiceDemandLatestResponse.of(latestDate.get(), List.of());
		}
		List<String> regionCodes = regions.stream().map(TourismRegion::getRegionCode).toList();
		return TourismServiceDemandLatestResponse.of(
				latestDate.get(),
				toResponses(
						repository.findByReferenceDateAndRegionCodeIn(latestDate.get(), regionCodes),
						regions
				)
		);
	}

	private List<TourismServiceDemandResponse> toResponses(
			Collection<TourismServiceDemand> demands,
			Collection<TourismRegion> regions
	) {
		Map<String, TourismRegion> regionByCode = regions.stream()
				.collect(Collectors.toMap(TourismRegion::getRegionCode, Function.identity()));
		return demands.stream()
				.map(demand -> TourismServiceDemandResponse.from(
						demand,
						regionByCode.get(demand.getRegionCode())
				))
				.toList();
	}

	private void validateBounds(
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude
	) {
		if (minLatitude < -90 || maxLatitude > 90
				|| minLongitude < -180 || maxLongitude > 180
				|| minLatitude > maxLatitude
				|| minLongitude > maxLongitude) {
			throw new IllegalArgumentException("올바른 위도·경도 범위를 입력하세요.");
		}
	}
}
