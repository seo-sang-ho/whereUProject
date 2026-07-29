package com.trip.whereU.directions.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

	public static RateLimitDecision permitted() {
		return new RateLimitDecision(true, 0);
	}

	public static RateLimitDecision rejected(long retryAfterSeconds) {
		return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
	}
}
