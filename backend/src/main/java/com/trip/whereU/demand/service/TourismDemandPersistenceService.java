package com.trip.whereU.demand.service;

import com.trip.whereU.demand.dto.TourismDemandOpenApiItem;
import com.trip.whereU.demand.entity.TourismDemand;
import com.trip.whereU.demand.repository.TourismDemandRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourismDemandPersistenceService {

	private final TourismDemandRepository tourismDemandRepository;

	public TourismDemandPersistenceService(TourismDemandRepository tourismDemandRepository) {
		this.tourismDemandRepository = tourismDemandRepository;
	}

	@Transactional
	public int saveDemandItems(List<TourismDemandOpenApiItem> items) {
		if (items.isEmpty()) {
			return 0;
		}

		double minScore = items.stream()
				.mapToDouble(TourismDemandOpenApiItem::demandScore)
				.min()
				.orElse(0);
		double maxScore = items.stream()
				.mapToDouble(TourismDemandOpenApiItem::demandScore)
				.max()
				.orElse(0);

		Set<LocalDate> referenceDates = items.stream()
				.map(TourismDemandOpenApiItem::referenceDate)
				.collect(Collectors.toSet());
		Map<DemandKey, TourismDemand> existingDemands = indexByKey(
				tourismDemandRepository.findAllByReferenceDateIn(referenceDates)
		);

		List<TourismDemand> demands = items.stream()
				.map(item -> upsert(item, minScore, maxScore, existingDemands))
				.toList();
		tourismDemandRepository.saveAll(demands);
		return demands.size();
	}

	private TourismDemand upsert(
			TourismDemandOpenApiItem item,
			double minScore,
			double maxScore,
			Map<DemandKey, TourismDemand> existingDemands
	) {
		double normalizedScore = normalize(item.demandScore(), minScore, maxScore);
		DemandKey key = new DemandKey(item.regionCode(), item.referenceDate());
		TourismDemand demand = existingDemands.get(key);
		if (demand == null) {
			demand = new TourismDemand(
						item.regionCode(),
						item.regionName(),
						item.demandScore(),
						normalizedScore,
						item.latitude(),
						item.longitude(),
						item.referenceDate()
				);
		}
		demand.updateDemand(
				item.demandScore(),
				normalizedScore,
				item.latitude(),
				item.longitude(),
				item.referenceDate()
		);
		return demand;
	}

	private Map<DemandKey, TourismDemand> indexByKey(Collection<TourismDemand> demands) {
		Map<DemandKey, TourismDemand> indexed = new HashMap<>();
		for (TourismDemand demand : demands) {
			indexed.put(new DemandKey(demand.getRegionCode(), demand.getReferenceDate()), demand);
		}
		return indexed;
	}

	double normalize(double score, double minScore, double maxScore) {
		if (Double.compare(maxScore, minScore) == 0) {
			return 0;
		}
		return (score - minScore) / (maxScore - minScore);
	}

	private record DemandKey(String regionCode, LocalDate referenceDate) {
	}
}
