package com.trip.whereU.demand.repository;

import com.trip.whereU.demand.entity.TourismDemand;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourismDemandRepository extends JpaRepository<TourismDemand, Long> {

	Optional<TourismDemand> findByRegionCodeAndReferenceDate(String regionCode, LocalDate referenceDate);

	List<TourismDemand> findByLatitudeBetweenAndLongitudeBetween(
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude
	);
}
