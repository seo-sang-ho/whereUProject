package com.trip.whereU.staystrength.repository;

import com.trip.whereU.staystrength.entity.TourismStayStrength;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TourismStayStrengthRepository extends JpaRepository<TourismStayStrength, Long> {

	Optional<TourismStayStrength> findByRegionCodeAndReferenceDate(String regionCode, LocalDate referenceDate);

	List<TourismStayStrength> findAllByReferenceDateIn(Collection<LocalDate> referenceDates);

	List<TourismStayStrength> findByRegionCodeIn(Collection<String> regionCodes);

	List<TourismStayStrength> findByReferenceDate(LocalDate referenceDate);

	List<TourismStayStrength> findByReferenceDateAndRegionCodeIn(
			LocalDate referenceDate,
			Collection<String> regionCodes
	);

	@Query("select max(d.referenceDate) from TourismStayStrength d")
	Optional<LocalDate> findLatestReferenceDate();

	@Query("select distinct d.referenceDate from TourismStayStrength d order by d.referenceDate desc")
	List<LocalDate> findReferenceDatesDescending();

	@Query("""
			select distinct
				d.regionCode as regionCode,
				d.regionName as regionName
			from TourismStayStrength d
			order by d.regionCode
			""")
	List<TourismStayStrengthRegionProjection> findDistinctRegions();
}
