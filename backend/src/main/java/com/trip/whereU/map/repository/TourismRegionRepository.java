package com.trip.whereU.map.repository;

import com.trip.whereU.map.entity.TourismRegion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TourismRegionRepository extends JpaRepository<TourismRegion, String> {

	List<TourismRegion> findByLatitudeBetweenAndLongitudeBetween(
			double minLatitude,
			double maxLatitude,
			double minLongitude,
			double maxLongitude
	);
}
