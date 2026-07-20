package com.trip.whereU.directions.service;

import com.trip.whereU.directions.config.NaverDirectionsProperties;
import com.trip.whereU.directions.entity.NaverDirectionsMonthlyUsage;
import com.trip.whereU.directions.repository.NaverDirectionsMonthlyUsageRepository;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DirectionsUsageService {

	private static final int RESERVATION_ATTEMPTS = 2;

	private final NaverDirectionsMonthlyUsageRepository repository;
	private final long safeLimit;
	private final Clock clock;
	private final TransactionTemplate transactionTemplate;
	private final TransactionTemplate requiresNewTransactionTemplate;

	@Autowired
	public DirectionsUsageService(
			NaverDirectionsMonthlyUsageRepository repository,
			NaverDirectionsProperties properties,
			PlatformTransactionManager transactionManager
	) {
		this(
				repository,
				properties,
				Clock.systemDefaultZone(),
				new TransactionTemplate(transactionManager),
				newRequiresNewTransactionTemplate(transactionManager)
		);
	}

	DirectionsUsageService(
			NaverDirectionsMonthlyUsageRepository repository,
			NaverDirectionsProperties properties,
			Clock clock,
			TransactionTemplate transactionTemplate,
			TransactionTemplate requiresNewTransactionTemplate
	) {
		this.repository = repository;
		this.safeLimit = properties.directionsMonthlySafeLimit();
		this.clock = clock;
		this.transactionTemplate = transactionTemplate;
		this.requiresNewTransactionTemplate = requiresNewTransactionTemplate;
	}

	public boolean reserveCurrentMonth() {
		String usageMonth = currentUsageMonth();
		for (int attempt = 0; attempt < RESERVATION_ATTEMPTS; attempt++) {
			ReservationResult result = transactionTemplate.execute(status -> repository
					.findByUsageMonthForUpdate(usageMonth)
					.map(usage -> usage.reserve(safeLimit) ? ReservationResult.RESERVED : ReservationResult.LIMIT_REACHED)
					.orElse(ReservationResult.MISSING));

			if (result == ReservationResult.RESERVED) {
				return true;
			}
			if (result == ReservationResult.LIMIT_REACHED) {
				return false;
			}

			createUsageMonth(usageMonth);
		}
		throw new IllegalStateException("Unable to reserve directions monthly usage");
	}

	public boolean isCurrentMonthAvailable() {
		return repository.findById(currentUsageMonth())
				.map(usage -> usage.getReservedCount() < safeLimit)
				.orElse(true);
	}

	private void createUsageMonth(String usageMonth) {
		try {
			requiresNewTransactionTemplate.executeWithoutResult(status ->
					repository.saveAndFlush(new NaverDirectionsMonthlyUsage(usageMonth, 0))
			);
		} catch (DataIntegrityViolationException ignored) {
			// A concurrent caller created this month. The next locked attempt owns the update.
		}
	}

	private String currentUsageMonth() {
		return YearMonth.now(clock).toString();
	}

	private static TransactionTemplate newRequiresNewTransactionTemplate(
			PlatformTransactionManager transactionManager
	) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return transactionTemplate;
	}

	private enum ReservationResult {
		RESERVED,
		LIMIT_REACHED,
		MISSING
	}
}
