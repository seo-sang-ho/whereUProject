package com.trip.whereU.resourcedemand.repository;

import com.trip.whereU.resourcedemand.entity.TourismResourceDemand;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TourismResourceDemandRepository extends JpaRepository<TourismResourceDemand, Long> {

	List<TourismResourceDemand> findAllByReferenceDateIn(Collection<LocalDate> referenceDates);

	List<TourismResourceDemand> findByReferenceDateOrderByResourceTypeAscIndicatorCodeAscRegionCodeAsc(
			LocalDate referenceDate
	);

	@Query("select max(d.referenceDate) from TourismResourceDemand d")
	Optional<LocalDate> findLatestReferenceDate();
}
