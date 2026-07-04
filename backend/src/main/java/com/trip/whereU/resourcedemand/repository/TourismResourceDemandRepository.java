package com.trip.whereU.resourcedemand.repository;

import com.trip.whereU.resourcedemand.entity.TourismResourceDemand;
import com.trip.whereU.resourcedemand.entity.TourismTheme;
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

	List<TourismResourceDemand> findByReferenceDateAndThemeIn(
			LocalDate referenceDate,
			Collection<TourismTheme> themes
	);

	@Query("select max(d.referenceDate) from TourismResourceDemand d")
	Optional<LocalDate> findLatestReferenceDate();

	@Query("select distinct d.referenceDate from TourismResourceDemand d where d.theme = :theme order by d.referenceDate desc")
	List<LocalDate> findReferenceDatesByThemeDescending(TourismTheme theme);
}
