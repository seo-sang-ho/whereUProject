package com.trip.whereU.demand.service;

import com.trip.whereU.demand.client.TourismDemandOpenApiClient;
import com.trip.whereU.demand.dto.TourismDemandOpenApiItem;
import com.trip.whereU.demand.dto.TourismDemandResponse;
import com.trip.whereU.demand.dto.TourismDemandSyncResponse;
import com.trip.whereU.demand.entity.TourismDemand;
import com.trip.whereU.demand.repository.TourismDemandRepository;
import java.time.LocalDate;
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

	public TourismDemandService(
			TourismDemandRepository tourismDemandRepository,
			TourismDemandOpenApiClient tourismDemandOpenApiClient
	) {
		this.tourismDemandRepository = tourismDemandRepository;
		this.tourismDemandOpenApiClient = tourismDemandOpenApiClient;
	}

	@Transactional
	public TourismDemandSyncResponse syncDemandData() {
		List<TourismDemandOpenApiItem> items = tourismDemandOpenApiClient.fetchDemandItems(
				DEFAULT_PAGE_NO,
				DEFAULT_NUM_OF_ROWS
		);

		if (items.isEmpty()) {
			return new TourismDemandSyncResponse(0, 0, LocalDate.now());
		}

		double minScore = items.stream()
				.mapToDouble(TourismDemandOpenApiItem::demandScore)
				.min()
				.orElse(0);
		double maxScore = items.stream()
				.mapToDouble(TourismDemandOpenApiItem::demandScore)
				.max()
				.orElse(0);

		int savedCount = 0;
		for (TourismDemandOpenApiItem item : items) {
			double normalizedScore = normalize(item.demandScore(), minScore, maxScore);
			TourismDemand demand = tourismDemandRepository
					.findByRegionCodeAndReferenceDate(item.regionCode(), item.referenceDate())
					.orElseGet(() -> new TourismDemand(
							item.regionCode(),
							item.regionName(),
							item.demandScore(),
							normalizedScore,
							item.latitude(),
							item.longitude(),
							item.referenceDate()
					));

			demand.updateDemand(
					item.demandScore(),
					normalizedScore,
					item.latitude(),
					item.longitude(),
					item.referenceDate()
			);
			tourismDemandRepository.save(demand);
			savedCount++;
		}

		LocalDate latestReferenceDate = items.stream()
				.map(TourismDemandOpenApiItem::referenceDate)
				.max(Comparator.naturalOrder())
				.orElse(LocalDate.now());

		return new TourismDemandSyncResponse(items.size(), savedCount, latestReferenceDate);
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

	double normalize(double score, double minScore, double maxScore) {
		if (Double.compare(maxScore, minScore) == 0) {
			return 0;
		}
		return (score - minScore) / (maxScore - minScore);
	}
}
