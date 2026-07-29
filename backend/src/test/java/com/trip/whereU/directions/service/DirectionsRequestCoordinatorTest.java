package com.trip.whereU.directions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.trip.whereU.directions.dto.DirectionsEstimateResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DirectionsRequestCoordinatorTest {

	private static final OffsetDateTime CALCULATED_AT =
			OffsetDateTime.parse("2026-07-20T15:00:00+09:00");

	private final ExecutorService executor = Executors.newFixedThreadPool(2);
	private final ObservedInFlightMap observedMap = new ObservedInFlightMap();
	private final DirectionsRequestCoordinator coordinator =
			new DirectionsRequestCoordinator(observedMap);

	@AfterEach
	void shutDownExecutor() {
		executor.shutdownNow();
	}

	@Test
	void sharesOneOperationAcrossConcurrentCallers() throws Exception {
		CountDownLatch operationStarted = new CountDownLatch(1);
		CountDownLatch releaseOperation = new CountDownLatch(1);
		AtomicInteger executions = new AtomicInteger();
		Supplier<DirectionsEstimateResponse> operation = () -> {
			executions.incrementAndGet();
			operationStarted.countDown();
			await(releaseOperation);
			return response();
		};

		Future<DirectionsEstimateResponse> first = executor.submit(
				() -> coordinator.execute(key(), operation)
		);
		assertThat(operationStarted.await(5, TimeUnit.SECONDS)).isTrue();
		Future<DirectionsEstimateResponse> second = executor.submit(
				() -> coordinator.execute(key(), operation)
		);
		assertThat(observedMap.awaitFollower()).isTrue();
		releaseOperation.countDown();

		assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(response());
		assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(response());
		assertThat(executions).hasValue(1);
	}

	@Test
	void removesFailedOperationSoNextCallCanRetry() {
		assertThatThrownBy(() -> coordinator.execute(key(), () -> {
			throw new IllegalStateException("first failure");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(coordinator.execute(key(), this::response)).isEqualTo(response());
	}

	@Test
	void doesNotSerializeDifferentKeys() throws Exception {
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		Future<DirectionsEstimateResponse> first = executor.submit(() ->
				coordinator.execute(key(), () -> {
					firstStarted.countDown();
					await(releaseFirst);
					return response();
				})
		);
		assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

		Future<DirectionsEstimateResponse> independent = executor.submit(
				() -> coordinator.execute(otherKey(), this::otherResponse)
		);
		assertThat(independent.get(5, TimeUnit.SECONDS)).isEqualTo(otherResponse());

		releaseFirst.countDown();
		assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(response());
	}

	private DirectionsCacheKey key() {
		return new DirectionsCacheKey(
				new BigDecimal("37.567"),
				new BigDecimal("126.978"),
				"126508",
				"traoptimal"
		);
	}

	private DirectionsCacheKey otherKey() {
		return new DirectionsCacheKey(
				new BigDecimal("35.180"),
				new BigDecimal("129.076"),
				"264337",
				"traoptimal"
		);
	}

	private DirectionsEstimateResponse response() {
		return DirectionsEstimateResponse.available(
				"126508",
				"경복궁",
				25,
				12_300,
				0,
				CALCULATED_AT
		);
	}

	private DirectionsEstimateResponse otherResponse() {
		return DirectionsEstimateResponse.available(
				"264337",
				"해운대해수욕장",
				18,
				8_500,
				0,
				CALCULATED_AT
		);
	}

	private void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
