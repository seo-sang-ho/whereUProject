package com.trip.whereU.tourism.repository;

import com.trip.whereU.tourism.entity.TourismRegionTourApiMapping;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourismRegionTourApiMappingRepository
		extends JpaRepository<TourismRegionTourApiMapping, Long> {

	List<TourismRegionTourApiMapping> findByEnabledTrueAndRegionCodeIn(Collection<String> regionCodes);
}
