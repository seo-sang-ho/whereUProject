package com.trip.whereU.directions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

import com.trip.whereU.directions.config.NaverDirectionsProperties;
import com.trip.whereU.directions.entity.NaverDirectionsMonthlyUsage;
import com.trip.whereU.directions.repository.NaverDirectionsMonthlyUsageRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class DirectionsUsageServiceTest {

	private static final String JULY_2026 = "2026-07";

	@Mock
	private NaverDirectionsMonthlyUsageRepository repository;

	@Mock
	private TransactionTemplate transactionTemplate;

	@Mock
	private TransactionTemplate requiresNewTransactionTemplate;

	@Mock
	private PlatformTransactionManager transactionManager;

	private DirectionsUsageService service;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-07-20T00:00:00Z"), ZoneId.of("Asia/Seoul"));
		NaverDirectionsProperties properties = new NaverDirectionsProperties("https://example.test", 50_000, 60, 1_000);
		service = new DirectionsUsageService(
				repository,
				properties,
				clock,
				transactionTemplate,
				requiresNewTransactionTemplate
		);
	}

	@Test
	void reservesTheLastAvailableRequest() {
		allowTransactionExecution();
		NaverDirectionsMonthlyUsage usage = new NaverDirectionsMonthlyUsage(JULY_2026, 49_999);
		given(repository.findByUsageMonthForUpdate(JULY_2026)).willReturn(Optional.of(usage));

		assertThat(service.reserveCurrentMonth()).isTrue();

		assertThat(usage.getReservedCount()).isEqualTo(50_000);
	}

	@Test
	void rejectsReservationAtTheSafeLimit() {
		allowTransactionExecution();
		NaverDirectionsMonthlyUsage usage = new NaverDirectionsMonthlyUsage(JULY_2026, 50_000);
		given(repository.findByUsageMonthForUpdate(JULY_2026)).willReturn(Optional.of(usage));

		assertThat(service.reserveCurrentMonth()).isFalse();

		assertThat(usage.getReservedCount()).isEqualTo(50_000);
	}

	@Test
	void availabilityDoesNotExposeOrIncrementUsage() {
		given(repository.findById(JULY_2026))
				.willReturn(Optional.of(new NaverDirectionsMonthlyUsage(JULY_2026, 49_999)));

		assertThat(service.isCurrentMonthAvailable()).isTrue();

		then(repository).should(never()).save(any());
	}

	@Test
	void productionClockUsesKoreanMonthAtUtcMonthBoundary() {
		Instant utcMonthBoundary = Instant.parse("2026-07-31T15:00:00Z");
		ZoneId koreaZone = ZoneId.of("Asia/Seoul");
		Clock utcClock = Clock.fixed(utcMonthBoundary, ZoneOffset.UTC);
		Clock koreanClock = Clock.fixed(utcMonthBoundary, koreaZone);
		NaverDirectionsProperties properties = new NaverDirectionsProperties("https://example.test", 50_000, 60, 1_000);

		try (MockedStatic<Clock> clock = Mockito.mockStatic(Clock.class)) {
			clock.when(Clock::systemDefaultZone).thenReturn(utcClock);
			clock.when(() -> Clock.system(koreaZone)).thenReturn(koreanClock);

			new DirectionsUsageService(repository, properties, transactionManager).isCurrentMonthAvailable();
		}

		then(repository).should().findById("2026-08");
	}

	@Test
	void createsMissingMonthInANewTransactionThenReserves() {
		allowTransactionExecution();
		allowRequiresNewTransactionExecution();
		NaverDirectionsMonthlyUsage usage = new NaverDirectionsMonthlyUsage(JULY_2026, 0);
		given(repository.findByUsageMonthForUpdate(JULY_2026))
				.willReturn(Optional.empty(), Optional.of(usage));

		assertThat(service.reserveCurrentMonth()).isTrue();

		then(repository).should().saveAndFlush(any(NaverDirectionsMonthlyUsage.class));
		assertThat(usage.getReservedCount()).isOne();
	}

	@Test
	void retriesLockedReservationAfterFirstRowCreationCollision() {
		allowTransactionExecution();
		NaverDirectionsMonthlyUsage usage = new NaverDirectionsMonthlyUsage(JULY_2026, 0);
		given(repository.findByUsageMonthForUpdate(JULY_2026))
				.willReturn(Optional.empty(), Optional.of(usage));
		doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate usage month"))
				.when(requiresNewTransactionTemplate)
				.executeWithoutResult(any());

		assertThat(service.reserveCurrentMonth()).isTrue();

		assertThat(usage.getReservedCount()).isOne();
	}

	private Object execute(TransactionCallback<?> callback) {
		return callback.doInTransaction(new SimpleTransactionStatus());
	}

	@SuppressWarnings("unchecked")
	private void allowTransactionExecution() {
		given(transactionTemplate.execute(any())).willAnswer(invocation -> execute(invocation.getArgument(0)));
	}

	@SuppressWarnings("unchecked")
	private void allowRequiresNewTransactionExecution() {
		doAnswer(invocation -> {
			executeWithoutResult(invocation.getArgument(0));
			return null;
		}).when(requiresNewTransactionTemplate).executeWithoutResult(any());
	}

	private void executeWithoutResult(Consumer<TransactionStatus> callback) {
		callback.accept(new SimpleTransactionStatus());
	}
}
