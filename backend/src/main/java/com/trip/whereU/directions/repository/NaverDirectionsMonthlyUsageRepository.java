package com.trip.whereU.directions.repository;

import com.trip.whereU.directions.entity.NaverDirectionsMonthlyUsage;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NaverDirectionsMonthlyUsageRepository
		extends JpaRepository<NaverDirectionsMonthlyUsage, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select usage from NaverDirectionsMonthlyUsage usage where usage.usageMonth = :usageMonth")
	Optional<NaverDirectionsMonthlyUsage> findByUsageMonthForUpdate(String usageMonth);
}
