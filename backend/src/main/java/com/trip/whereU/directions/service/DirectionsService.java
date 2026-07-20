package com.trip.whereU.directions.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.trip.whereU.directions.client.NaverDirectionsClient;
import com.trip.whereU.directions.config.NaverDirectionsProperties;
import com.trip.whereU.directions.dto.DirectionsAvailabilityResponse;
import com.trip.whereU.directions.dto.DirectionsEstimateRequest;
import com.trip.whereU.directions.dto.DirectionsEstimateResponse;
import com.trip.whereU.directions.dto.DirectionsFallbackReason;
import com.trip.whereU.directions.dto.DirectionsStatus;
import com.trip.whereU.directions.dto.NaverDirectionsResult;
import com.trip.whereU.directions.exception.DirectionsDestinationNotFoundException;
import com.trip.whereU.tourism.entity.TourismContent;
import com.trip.whereU.tourism.repository.TourismContentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class DirectionsService {

	private static final String ROUTE_OPTION = "traoptimal";

	private final TourismContentRepository tourismContentRepository;
	private final DirectionsUsageService usageService;
	private final NaverDirectionsClient client;
	private final Cache<DirectionsCacheKey, DirectionsEstimateResponse> cache;

	public DirectionsService(
			TourismContentRepository tourismContentRepository,
			DirectionsUsageService usageService,
			NaverDirectionsClient client,
			NaverDirectionsProperties properties
	) {
		this.tourismContentRepository = tourismContentRepository;
		this.usageService = usageService;
		this.client = client;
		this.cache = Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(properties.directionsCacheTtlMinutes()))
				.maximumSize(properties.directionsCacheMaximumSize())
				.build();
	}

	public DirectionsAvailabilityResponse getAvailability() {
		DirectionsStatus status = usageService.isCurrentMonthAvailable()
				? DirectionsStatus.AVAILABLE
				: DirectionsStatus.NAVER_MAP_REQUIRED;
		return new DirectionsAvailabilityResponse(status);
	}

	public DirectionsEstimateResponse estimate(DirectionsEstimateRequest request) {
		TourismContent destination = tourismContentRepository.findByContentId(request.destinationContentId())
				.orElseThrow(() -> new DirectionsDestinationNotFoundException(request.destinationContentId()));

		if (destination.getLatitude() == null || destination.getLongitude() == null) {
			return fallback(destination, DirectionsFallbackReason.DESTINATION_COORDINATES_MISSING);
		}

		DirectionsCacheKey cacheKey = new DirectionsCacheKey(
				roundOrigin(request.originLatitude()),
				roundOrigin(request.originLongitude()),
				destination.getContentId(),
				ROUTE_OPTION
		);
		DirectionsEstimateResponse cachedResponse = cache.getIfPresent(cacheKey);
		if (cachedResponse != null) {
			return cachedResponse;
		}

		if (!usageService.reserveCurrentMonth()) {
			return fallback(destination, DirectionsFallbackReason.MONTHLY_LIMIT_REACHED);
		}

		try {
			Optional<NaverDirectionsResult> result = client.getDrivingEstimate(
					request.originLatitude(),
					request.originLongitude(),
					destination.getLatitude(),
					destination.getLongitude()
			);
			if (result.isEmpty()) {
				return fallback(destination, DirectionsFallbackReason.ROUTE_NOT_FOUND);
			}

			DirectionsEstimateResponse response = available(destination, result.orElseThrow());
			cache.put(cacheKey, response);
			return response;
		} catch (RestClientException | IllegalStateException exception) {
			return fallback(destination, DirectionsFallbackReason.NAVER_API_UNAVAILABLE);
		}
	}

	private DirectionsEstimateResponse available(TourismContent destination, NaverDirectionsResult result) {
		return DirectionsEstimateResponse.available(
				destination.getContentId(),
				destination.getTitle(),
				result.travelTimeMinutes(),
				result.distanceMeters(),
				result.tollFare(),
				result.calculatedAt()
		);
	}

	private DirectionsEstimateResponse fallback(
			TourismContent destination,
			DirectionsFallbackReason reason
	) {
		return DirectionsEstimateResponse.naverMapRequired(
				destination.getContentId(),
				destination.getTitle(),
				reason
		);
	}

	private BigDecimal roundOrigin(double coordinate) {
		return BigDecimal.valueOf(coordinate).setScale(3, RoundingMode.HALF_UP);
	}

	private record DirectionsCacheKey(
			BigDecimal originLatitude,
			BigDecimal originLongitude,
			String destinationContentId,
			String option
	) {
	}
}
