package com.trip.whereU.resourcedemand.repository;

import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.entity.TourismResourceDemandSyncState;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourismResourceDemandSyncStateRepository
		extends JpaRepository<TourismResourceDemandSyncState, Long> {

	Optional<TourismResourceDemandSyncState> findByResourceTypeAndIndicatorCodeAndReferenceDate(
			ResourceDemandType resourceType,
			String indicatorCode,
			LocalDate referenceDate
	);
}
