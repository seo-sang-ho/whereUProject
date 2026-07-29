package com.trip.whereU.directions.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Ticker;
import com.trip.whereU.directions.config.DirectionsRateLimitProperties;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DirectionsRateLimitServiceTest {

	private final MutableTimeMeter timeMeter = new MutableTimeMeter();
	private final MutableTicker ticker = new MutableTicker();

	@Test
	void rejectsEleventhRequestFromSameIpAndReturnsRetryAfter() {
		DirectionsRateLimitService service = service(properties(true, 10, 10, 100));

		for (int count = 0; count < 10; count++) {
			assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
		}

		RateLimitDecision rejected = service.tryAcquire("203.0.113.10");
		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.retryAfterSeconds()).isPositive();
	}

	@Test
	void isolatesBucketsByIp() {
		DirectionsRateLimitService service = service(properties(true, 1, 1, 100));

		assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
		assertThat(service.tryAcquire("203.0.113.10").allowed()).isFalse();
		assertThat(service.tryAcquire("203.0.113.11").allowed()).isTrue();
	}

	@Test
	void refillsWithoutSleeping() {
		DirectionsRateLimitService service = service(properties(true, 1, 1, 100));
		assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
		assertThat(service.tryAcquire("203.0.113.10").allowed()).isFalse();

		timeMeter.advance(Duration.ofMinutes(1));

		assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
	}

	@Test
	void rejectsAfterDailyCapacityAndRefillsAfterTwentyFourHours() {
		DirectionsRateLimitService service = service(properties(true, 1_000, 1_000, 100));

		for (int count = 0; count < 100; count++) {
			assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
		}
		assertThat(service.tryAcquire("203.0.113.10").allowed()).isFalse();

		timeMeter.advance(Duration.ofHours(24));

		assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
	}

	@Test
	void disabledLimiterAlwaysAllows() {
		DirectionsRateLimitService service = service(properties(false, 1, 1, 1));

		assertThat(service.tryAcquire("203.0.113.10")).isEqualTo(RateLimitDecision.permitted());
		assertThat(service.tryAcquire("203.0.113.10")).isEqualTo(RateLimitDecision.permitted());
	}

	@Test
	void expiresUnusedBuckets() {
		DirectionsRateLimitService service = service(properties(true, 1, 1, 100));
		service.tryAcquire("203.0.113.10");
		assertThat(service.bucketCount()).isOne();

		ticker.advance(Duration.ofHours(25));
		service.cleanUp();

		assertThat(service.bucketCount()).isZero();
	}

	@Test
	void capsBucketCacheSize() {
		DirectionsRateLimitService service = service(
				new DirectionsRateLimitProperties(true, 10, 10, 100, 2, 24)
		);
		service.tryAcquire("203.0.113.10");
		service.tryAcquire("203.0.113.11");
		service.tryAcquire("203.0.113.12");
		service.cleanUp();

		assertThat(service.bucketCount()).isLessThanOrEqualTo(2);
	}

	private DirectionsRateLimitService service(DirectionsRateLimitProperties properties) {
		return new DirectionsRateLimitService(properties, timeMeter, ticker);
	}

	private DirectionsRateLimitProperties properties(
			boolean enabled,
			long minuteCapacity,
			long minuteRefillTokens,
			long dailyCapacity
	) {
		return new DirectionsRateLimitProperties(
				enabled,
				minuteCapacity,
				minuteRefillTokens,
				dailyCapacity,
				10_000,
				24
		);
	}

	final class MutableTimeMeter implements TimeMeter {
		private final AtomicLong nowNanos = new AtomicLong();

		@Override
		public long currentTimeNanos() {
			return nowNanos.get();
		}

		@Override
		public boolean isWallClockBased() {
			return false;
		}

		void advance(Duration duration) {
			nowNanos.addAndGet(duration.toNanos());
		}
	}

	final class MutableTicker implements Ticker {
		private final AtomicLong nowNanos = new AtomicLong();

		@Override
		public long read() {
			return nowNanos.get();
		}

		void advance(Duration duration) {
			nowNanos.addAndGet(duration.toNanos());
		}
	}
}
