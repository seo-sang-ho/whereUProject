package com.trip.whereU.directions.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.trip.whereU.directions.config.DirectionsRateLimitProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DirectionsRateLimitService {

	private final DirectionsRateLimitProperties properties;
	private final Cache<String, Bucket> buckets;
	private final TimeMeter timeMeter;

	@Autowired
	public DirectionsRateLimitService(DirectionsRateLimitProperties properties) {
		this(properties, TimeMeter.SYSTEM_NANOTIME, Ticker.systemTicker());
	}

	DirectionsRateLimitService(
			DirectionsRateLimitProperties properties,
			TimeMeter timeMeter,
			Ticker ticker
	) {
		this.properties = properties;
		this.timeMeter = timeMeter;
		this.buckets = Caffeine.newBuilder()
				.maximumSize(properties.cacheMaximumSize())
				.expireAfterAccess(Duration.ofHours(properties.cacheExpireAfterHours()))
				.ticker(ticker)
				.build();
	}

	public RateLimitDecision tryAcquire(String clientKey) {
		if (!properties.enabled()) {
			return RateLimitDecision.permitted();
		}
		ConsumptionProbe probe = buckets.get(clientKey, ignored -> newBucket())
				.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			return RateLimitDecision.permitted();
		}
		long retryAfterSeconds = Math.max(
				1,
				(probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L
		);
		return RateLimitDecision.rejected(retryAfterSeconds);
	}

	private Bucket newBucket() {
		return Bucket.builder()
				.withCustomTimePrecision(timeMeter)
				.addLimit(limit -> limit
						.capacity(properties.minuteCapacity())
						.refillGreedy(properties.minuteRefillTokens(), Duration.ofMinutes(1)))
				.addLimit(limit -> limit
						.capacity(properties.dailyCapacity())
						.refillGreedy(properties.dailyCapacity(), Duration.ofHours(24)))
				.build();
	}

	long bucketCount() {
		return buckets.estimatedSize();
	}

	void cleanUp() {
		buckets.cleanUp();
	}
}
