package com.trip.whereU.resourcedemand.service;

import com.trip.whereU.resourcedemand.dto.TourismResourceDemandOpenApiItem;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.entity.TourismResourceDemand;
import com.trip.whereU.resourcedemand.repository.TourismResourceDemandRepository;
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
public class TourismResourceDemandPersistenceService {

	private final TourismResourceDemandRepository repository;

	public TourismResourceDemandPersistenceService(TourismResourceDemandRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public int saveItems(List<TourismResourceDemandOpenApiItem> items) {
		if (items.isEmpty()) {
			return 0;
		}

		Map<NormalizationKey, ScoreRange> ranges = scoreRanges(items);
		Set<LocalDate> referenceDates = items.stream()
				.map(TourismResourceDemandOpenApiItem::referenceDate)
				.collect(Collectors.toSet());
		Map<ResourceDemandKey, TourismResourceDemand> existing = indexByKey(
				repository.findAllByReferenceDateIn(referenceDates)
		);

		List<TourismResourceDemand> demands = items.stream()
				.map(item -> upsert(item, ranges.get(NormalizationKey.from(item)), existing))
				.toList();
		repository.saveAll(demands);
		return demands.size();
	}

	private Map<NormalizationKey, ScoreRange> scoreRanges(
			List<TourismResourceDemandOpenApiItem> items
	) {
		Map<NormalizationKey, ScoreRange> ranges = new HashMap<>();
		for (TourismResourceDemandOpenApiItem item : items) {
			ranges.compute(
					NormalizationKey.from(item),
					(key, range) -> range == null
							? new ScoreRange(item.value(), item.value())
							: range.include(item.value())
			);
		}
		return ranges;
	}

	private TourismResourceDemand upsert(
			TourismResourceDemandOpenApiItem item,
			ScoreRange range,
			Map<ResourceDemandKey, TourismResourceDemand> existing
	) {
		double normalizedValue = normalize(item.value(), range.min(), range.max());
		ResourceDemandKey key = ResourceDemandKey.from(item);
		TourismResourceDemand demand = existing.get(key);
		if (demand == null) {
			demand = new TourismResourceDemand(
					item.regionCode(),
					item.regionName(),
					item.resourceType(),
					item.indicatorCode(),
					item.indicatorName(),
					item.value(),
					normalizedValue,
					item.referenceDate()
			);
		}
		demand.update(
				item.regionName(),
				item.indicatorName(),
				item.value(),
				normalizedValue,
				item.referenceDate()
		);
		return demand;
	}

	private Map<ResourceDemandKey, TourismResourceDemand> indexByKey(
			Collection<TourismResourceDemand> demands
	) {
		Map<ResourceDemandKey, TourismResourceDemand> indexed = new HashMap<>();
		for (TourismResourceDemand demand : demands) {
			indexed.put(ResourceDemandKey.from(demand), demand);
		}
		return indexed;
	}

	double normalize(double value, double min, double max) {
		if (Double.compare(max, min) == 0) {
			return 0;
		}
		return (value - min) / (max - min);
	}

	private record NormalizationKey(
			ResourceDemandType resourceType,
			String indicatorCode,
			LocalDate referenceDate
	) {
		private static NormalizationKey from(TourismResourceDemandOpenApiItem item) {
			return new NormalizationKey(item.resourceType(), item.indicatorCode(), item.referenceDate());
		}
	}

	private record ResourceDemandKey(
			String regionCode,
			ResourceDemandType resourceType,
			String indicatorCode,
			LocalDate referenceDate
	) {
		private static ResourceDemandKey from(TourismResourceDemandOpenApiItem item) {
			return new ResourceDemandKey(
					item.regionCode(), item.resourceType(), item.indicatorCode(), item.referenceDate()
			);
		}

		private static ResourceDemandKey from(TourismResourceDemand demand) {
			return new ResourceDemandKey(
					demand.getRegionCode(),
					demand.getResourceType(),
					demand.getIndicatorCode(),
					demand.getReferenceDate()
			);
		}
	}

	private record ScoreRange(double min, double max) {
		private ScoreRange include(double value) {
			return new ScoreRange(Math.min(min, value), Math.max(max, value));
		}
	}
}
