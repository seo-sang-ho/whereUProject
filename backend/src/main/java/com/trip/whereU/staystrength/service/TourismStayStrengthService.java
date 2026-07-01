package com.trip.whereU.staystrength.service;

import com.trip.whereU.staystrength.client.TourismStayStrengthOpenApiClient;
import com.trip.whereU.staystrength.config.TourismOpenApiProperties;
import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiItem;
import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiPage;
import com.trip.whereU.staystrength.dto.TourismStayStrengthLatestResponse;
import com.trip.whereU.staystrength.dto.TourismStayStrengthResponse;
import com.trip.whereU.staystrength.dto.TourismStayStrengthSyncResponse;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
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
public class TourismStayStrengthService {

	private static final int DEFAULT_PAGE_NO = 1;
	private static final int DEFAULT_NUM_OF_ROWS = 100;

	private final TourismStayStrengthRepository tourismStayStrengthRepository;
	private final TourismStayStrengthOpenApiClient tourismStayStrengthOpenApiClient;
	private final TourismStayStrengthPersistenceService tourismStayStrengthPersistenceService;
	private final TourismRegionRepository tourismRegionRepository;
	private final TourismOpenApiProperties properties;

	public TourismStayStrengthService(
			TourismStayStrengthRepository tourismStayStrengthRepository,
			TourismStayStrengthOpenApiClient tourismStayStrengthOpenApiClient,
			TourismStayStrengthPersistenceService tourismStayStrengthPersistenceService,
			TourismRegionRepository tourismRegionRepository,
			TourismOpenApiProperties properties
	) {
		this.tourismStayStrengthRepository = tourismStayStrengthRepository;
		this.tourismStayStrengthOpenApiClient = tourismStayStrengthOpenApiClient;
		this.tourismStayStrengthPersistenceService = tourismStayStrengthPersistenceService;
		this.tourismRegionRepository = tourismRegionRepository;
		this.properties = properties;
	}

	public TourismStayStrengthSyncResponse syncStayStrengthData() {
		List<TourismStayStrengthOpenApiItem> items = fetchAllDistrictStayStrengthItems();

		if (items.isEmpty()) {
			return new TourismStayStrengthSyncResponse(0, 0, LocalDate.now());
		}

		int savedCount = tourismStayStrengthPersistenceService.saveStayStrengthItems(items);

		LocalDate latestReferenceDate = items.stream()
				.map(TourismStayStrengthOpenApiItem::referenceDate)
				.max(Comparator.naturalOrder())
				.orElse(LocalDate.now());

		return new TourismStayStrengthSyncResponse(items.size(), savedCount, latestReferenceDate);
	}

	private List<TourismStayStrengthOpenApiItem> fetchAllDistrictStayStrengthItems() {
		List<TourismStayStrengthOpenApiItem> items = new ArrayList<>();
		for (String areaCode : properties.stayStrength().areaCodes()) {
			int pageNo = DEFAULT_PAGE_NO;
			TourismStayStrengthOpenApiPage page;
			do {
				page = tourismStayStrengthOpenApiClient.fetchStayStrengthPage(areaCode, pageNo, DEFAULT_NUM_OF_ROWS);
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
	public List<TourismStayStrengthResponse> getAllStayStrengthLevels() {
		return toResponses(tourismStayStrengthRepository.findAll(), tourismRegionRepository.findAll());
	}

	@Transactional(readOnly = true)
	public TourismStayStrengthLatestResponse getLatestStayStrengthLevels() {
		Optional<LocalDate> latestReferenceDate = tourismStayStrengthRepository.findLatestReferenceDate();
		if (latestReferenceDate.isEmpty()) {
			return TourismStayStrengthLatestResponse.empty();
		}

		LocalDate referenceDate = latestReferenceDate.get();
		List<TourismStayStrengthResponse> responses = toResponses(
				tourismStayStrengthRepository.findByReferenceDate(referenceDate),
				tourismRegionRepository.findAll()
		);
		return TourismStayStrengthLatestResponse.of(referenceDate, responses);
	}

	public String getMaskedOpenApiRequestUri() {
		return tourismStayStrengthOpenApiClient.buildMaskedStayStrengthUri(DEFAULT_PAGE_NO, DEFAULT_NUM_OF_ROWS);
	}

	@Transactional(readOnly = true)
	public List<TourismStayStrengthResponse> getStayStrengthLevelsInBounds(
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude
	) {
		validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);
		List<TourismRegion> regions = tourismRegionRepository
				.findByLatitudeBetweenAndLongitudeBetween(minLatitude, maxLatitude, minLongitude, maxLongitude);
		List<String> regionCodes = regions.stream().map(TourismRegion::getRegionCode).toList();
		if (regionCodes.isEmpty()) {
			return List.of();
		}
		return toResponses(tourismStayStrengthRepository.findByRegionCodeIn(regionCodes), regions);
	}

	@Transactional(readOnly = true)
	public TourismStayStrengthLatestResponse getLatestStayStrengthLevelsInBounds(
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude
	) {
		validateBounds(minLatitude, maxLatitude, minLongitude, maxLongitude);
		Optional<LocalDate> latestReferenceDate = tourismStayStrengthRepository.findLatestReferenceDate();
		if (latestReferenceDate.isEmpty()) {
			return TourismStayStrengthLatestResponse.empty();
		}

		List<TourismRegion> regions = tourismRegionRepository
				.findByLatitudeBetweenAndLongitudeBetween(minLatitude, maxLatitude, minLongitude, maxLongitude);
		if (regions.isEmpty()) {
			return TourismStayStrengthLatestResponse.of(latestReferenceDate.get(), List.of());
		}

		List<String> regionCodes = regions.stream().map(TourismRegion::getRegionCode).toList();
		List<TourismStayStrengthResponse> responses = toResponses(
				tourismStayStrengthRepository.findByReferenceDateAndRegionCodeIn(latestReferenceDate.get(), regionCodes),
				regions
		);
		return TourismStayStrengthLatestResponse.of(latestReferenceDate.get(), responses);
	}

	private List<TourismStayStrengthResponse> toResponses(
			Collection<TourismStayStrength> stayStrengths,
			Collection<TourismRegion> regions
	) {
		Map<String, TourismRegion> regionByCode = regions.stream()
				.collect(Collectors.toMap(TourismRegion::getRegionCode, Function.identity()));
		return stayStrengths.stream()
				.map(stayStrength -> TourismStayStrengthResponse.from(stayStrength, regionByCode.get(stayStrength.getRegionCode())))
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
