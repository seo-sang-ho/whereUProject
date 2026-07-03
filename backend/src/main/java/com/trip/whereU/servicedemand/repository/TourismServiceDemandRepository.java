package com.trip.whereU.servicedemand.repository;

import com.trip.whereU.servicedemand.entity.TourismServiceDemand;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TourismServiceDemandRepository extends JpaRepository<TourismServiceDemand, Long> {

	List<TourismServiceDemand> findAllByReferenceDateIn(Collection<LocalDate> referenceDates);

	List<TourismServiceDemand> findByReferenceDate(LocalDate referenceDate);

	List<TourismServiceDemand> findByReferenceDateAndRegionCodeIn(
			LocalDate referenceDate,
			Collection<String> regionCodes
	);

	@Query("select max(d.referenceDate) from TourismServiceDemand d")
	Optional<LocalDate> findLatestReferenceDate();

	@Query("select distinct d.referenceDate from TourismServiceDemand d order by d.referenceDate desc")
	List<LocalDate> findReferenceDatesDescending();
}
