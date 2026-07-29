package com.trip.whereU.directions.service;

import com.trip.whereU.directions.dto.DirectionsEstimateResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class ObservedInFlightMap extends ConcurrentHashMap<
		DirectionsCacheKey,
		CompletableFuture<DirectionsEstimateResponse>
> {

	private final CountDownLatch followerJoined = new CountDownLatch(1);

	@Override
	public CompletableFuture<DirectionsEstimateResponse> putIfAbsent(
			DirectionsCacheKey key,
			CompletableFuture<DirectionsEstimateResponse> value
	) {
		CompletableFuture<DirectionsEstimateResponse> existing =
				super.putIfAbsent(key, value);
		if (existing != null) {
			followerJoined.countDown();
		}
		return existing;
	}

	boolean awaitFollower() throws InterruptedException {
		return followerJoined.await(5, TimeUnit.SECONDS);
	}
}
