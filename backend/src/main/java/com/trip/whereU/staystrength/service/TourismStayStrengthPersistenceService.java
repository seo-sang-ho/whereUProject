package com.trip.whereU.staystrength.service;

import com.trip.whereU.staystrength.dto.TourismStayStrengthOpenApiItem;
import com.trip.whereU.staystrength.entity.TourismStayStrength;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
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
public class TourismStayStrengthPersistenceService {

	private final TourismStayStrengthRepository tourismStayStrengthRepository;

	public TourismStayStrengthPersistenceService(TourismStayStrengthRepository tourismStayStrengthRepository) {
		this.tourismStayStrengthRepository = tourismStayStrengthRepository;
	}

	@Transactional
	public int saveStayStrengthItems(List<TourismStayStrengthOpenApiItem> items) {
		if (items.isEmpty()) {
			return 0;
		}

		double minScore = items.stream()
				.mapToDouble(TourismStayStrengthOpenApiItem::stayStrength)
				.min()
				.orElse(0);
		double maxScore = items.stream()
				.mapToDouble(TourismStayStrengthOpenApiItem::stayStrength)
				.max()
				.orElse(0);

		Set<LocalDate> referenceDates = items.stream()
				.map(TourismStayStrengthOpenApiItem::referenceDate)
				.collect(Collectors.toSet());
		Map<StayStrengthKey, TourismStayStrength> existingStayStrengths = indexByKey(
				tourismStayStrengthRepository.findAllByReferenceDateIn(referenceDates)
		);

		List<TourismStayStrength> stayStrengths = items.stream()
				.map(item -> upsert(item, minScore, maxScore, existingStayStrengths))
				.toList();
		tourismStayStrengthRepository.saveAll(stayStrengths);
		return stayStrengths.size();
	}

	private TourismStayStrength upsert(
			TourismStayStrengthOpenApiItem item,
			double minScore,
			double maxScore,
			Map<StayStrengthKey, TourismStayStrength> existingStayStrengths
	) {
		double normalizedScore = normalize(item.stayStrength(), minScore, maxScore);
		StayStrengthKey key = new StayStrengthKey(item.regionCode(), item.referenceDate());
		TourismStayStrength stayStrength = existingStayStrengths.get(key);
		if (stayStrength == null) {
			stayStrength = new TourismStayStrength(
						item.regionCode(),
						item.regionName(),
						item.stayStrength(),
						normalizedScore,
						item.referenceDate()
				);
		}
		stayStrength.updateStayStrength(
				item.stayStrength(),
				normalizedScore,
				item.referenceDate()
		);
		return stayStrength;
	}

	private Map<StayStrengthKey, TourismStayStrength> indexByKey(Collection<TourismStayStrength> stayStrengths) {
		Map<StayStrengthKey, TourismStayStrength> indexed = new HashMap<>();
		for (TourismStayStrength stayStrength : stayStrengths) {
			indexed.put(new StayStrengthKey(stayStrength.getRegionCode(), stayStrength.getReferenceDate()), stayStrength);
		}
		return indexed;
	}

	double normalize(double score, double minScore, double maxScore) {
		if (Double.compare(maxScore, minScore) == 0) {
			return 0;
		}
		return (score - minScore) / (maxScore - minScore);
	}

	private record StayStrengthKey(String regionCode, LocalDate referenceDate) {
	}
}
