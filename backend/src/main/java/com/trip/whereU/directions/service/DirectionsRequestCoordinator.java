package com.trip.whereU.directions.service;

import com.trip.whereU.directions.dto.DirectionsEstimateResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class DirectionsRequestCoordinator {

	private final ConcurrentMap<DirectionsCacheKey, CompletableFuture<DirectionsEstimateResponse>>
			inFlight;

	public DirectionsRequestCoordinator() {
		this(new ConcurrentHashMap<>());
	}

	DirectionsRequestCoordinator(
			ConcurrentMap<DirectionsCacheKey, CompletableFuture<DirectionsEstimateResponse>>
					inFlight
	) {
		this.inFlight = inFlight;
	}

	public DirectionsEstimateResponse execute(
			DirectionsCacheKey key,
			Supplier<DirectionsEstimateResponse> operation
	) {
		CompletableFuture<DirectionsEstimateResponse> leader = new CompletableFuture<>();
		CompletableFuture<DirectionsEstimateResponse> existing = inFlight.putIfAbsent(key, leader);
		if (existing != null) {
			return await(existing);
		}
		try {
			DirectionsEstimateResponse response = operation.get();
			leader.complete(response);
			return response;
		} catch (RuntimeException | Error failure) {
			leader.completeExceptionally(failure);
			throw failure;
		} finally {
			inFlight.remove(key, leader);
		}
	}

	private DirectionsEstimateResponse await(
			CompletableFuture<DirectionsEstimateResponse> future
	) {
		try {
			return future.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("진행 중인 자동차 경로 대기가 중단되었습니다.", exception);
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(cause);
		}
	}
}
