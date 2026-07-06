package com.trip.whereU.resourcedemand.service;

import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.entity.TourismResourceDemandSyncState;
import com.trip.whereU.resourcedemand.repository.TourismResourceDemandSyncStateRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourismResourceDemandSyncStateService {

	private final TourismResourceDemandSyncStateRepository repository;

	public TourismResourceDemandSyncStateService(TourismResourceDemandSyncStateRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public boolean isCompleted(
			ResourceDemandType resourceType,
			String indicatorCode,
			LocalDate referenceDate
	) {
		return repository.findByResourceTypeAndIndicatorCodeAndReferenceDate(
				resourceType, indicatorCode, referenceDate
		).isPresent();
	}

	@Transactional
	public void markCompleted(
			ResourceDemandType resourceType,
			String indicatorCode,
			LocalDate referenceDate,
			int collectedCount
	) {
		TourismResourceDemandSyncState state = repository
				.findByResourceTypeAndIndicatorCodeAndReferenceDate(
						resourceType, indicatorCode, referenceDate
				)
				.orElseGet(() -> new TourismResourceDemandSyncState(
						resourceType, indicatorCode, referenceDate, collectedCount
				));
		state.complete(collectedCount);
		repository.save(state);
	}
}
