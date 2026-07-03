package com.trip.whereU.servicedemand.service;

import com.trip.whereU.servicedemand.dto.TourismServiceDemandOpenApiItem;
import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import com.trip.whereU.servicedemand.repository.TourismServiceDemandRepository;
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
public class TourismServiceDemandPersistenceService {

	private final TourismServiceDemandRepository repository;

	public TourismServiceDemandPersistenceService(TourismServiceDemandRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public int saveItems(List<TourismServiceDemandOpenApiItem> items) {
		if (items.isEmpty()) {
			return 0;
		}

		double min = items.stream().mapToDouble(TourismServiceDemandOpenApiItem::serviceDemand).min().orElse(0);
		double max = items.stream().mapToDouble(TourismServiceDemandOpenApiItem::serviceDemand).max().orElse(0);
		Set<LocalDate> referenceDates = items.stream()
				.map(TourismServiceDemandOpenApiItem::referenceDate)
				.collect(Collectors.toSet());
		Map<ServiceDemandKey, TourismServiceDemand> existing = indexByKey(
				repository.findAllByReferenceDateIn(referenceDates)
		);

		List<TourismServiceDemand> demands = items.stream()
				.map(item -> upsert(item, min, max, existing))
				.toList();
		repository.saveAll(demands);
		return demands.size();
	}

	private TourismServiceDemand upsert(
			TourismServiceDemandOpenApiItem item,
			double min,
			double max,
			Map<ServiceDemandKey, TourismServiceDemand> existing
	) {
		double normalized = normalize(item.serviceDemand(), min, max);
		ServiceDemandKey key = new ServiceDemandKey(item.regionCode(), item.referenceDate());
		TourismServiceDemand demand = existing.get(key);
		if (demand == null) {
			demand = new TourismServiceDemand(
					item.regionCode(),
					item.regionName(),
					item.serviceDemand(),
					normalized,
					item.referenceDate()
			);
		}
		demand.update(item.serviceDemand(), normalized, item.referenceDate());
		return demand;
	}

	private Map<ServiceDemandKey, TourismServiceDemand> indexByKey(
			Collection<TourismServiceDemand> demands
	) {
		Map<ServiceDemandKey, TourismServiceDemand> indexed = new HashMap<>();
		for (TourismServiceDemand demand : demands) {
			indexed.put(new ServiceDemandKey(demand.getRegionCode(), demand.getReferenceDate()), demand);
		}
		return indexed;
	}

	double normalize(double value, double min, double max) {
		if (Double.compare(max, min) == 0) {
			return 0;
		}
		return (value - min) / (max - min);
	}

	private record ServiceDemandKey(String regionCode, LocalDate referenceDate) {
	}
}
