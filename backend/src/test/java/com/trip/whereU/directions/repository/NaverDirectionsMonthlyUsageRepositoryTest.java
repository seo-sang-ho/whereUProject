package com.trip.whereU.directions.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.trip.whereU.directions.entity.NaverDirectionsMonthlyUsage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NaverDirectionsMonthlyUsageRepositoryTest {

	@Autowired
	private NaverDirectionsMonthlyUsageRepository repository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void lockedLookupReturnsInsertedRow() {
		repository.saveAndFlush(new NaverDirectionsMonthlyUsage("2026-07", 0));
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

		NaverDirectionsMonthlyUsage usage = transactionTemplate.execute(status -> repository
				.findByUsageMonthForUpdate("2026-07")
				.orElseThrow());

		assertThat(usage.getReservedCount()).isZero();
	}

	@Test
	void concurrentReservationsCannotExceedTheSafeLimit() throws Exception {
		repository.saveAndFlush(new NaverDirectionsMonthlyUsage("2026-07", 49_999));
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Boolean> first = executor.submit(() -> reserveAfterStart(transactionTemplate, ready, start));
			Future<Boolean> second = executor.submit(() -> reserveAfterStart(transactionTemplate, ready, start));

			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			assertThat(first.get(5, TimeUnit.SECONDS)).isNotEqualTo(second.get(5, TimeUnit.SECONDS));
			assertThat(repository.findById("2026-07")).get()
				.extracting(NaverDirectionsMonthlyUsage::getReservedCount)
				.isEqualTo(50_000L);
		} finally {
			executor.shutdownNow();
		}
	}

	private boolean reserveAfterStart(
			TransactionTemplate transactionTemplate,
			CountDownLatch ready,
			CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		if (!start.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Concurrent reservation start timed out");
		}
		return Boolean.TRUE.equals(transactionTemplate.execute(status -> repository
				.findByUsageMonthForUpdate("2026-07")
				.map(usage -> usage.reserve(50_000))
				.orElseThrow()));
	}
}
