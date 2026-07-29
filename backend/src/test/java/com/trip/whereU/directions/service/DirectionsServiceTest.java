package com.trip.whereU.directions.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class DirectionsServiceTest {

	private static final OffsetDateTime CALCULATED_AT =
			OffsetDateTime.parse("2026-07-20T15:00:00+09:00");

	@Mock
	private TourismContentRepository tourismContentRepository;
	@Mock
	private DirectionsUsageService usageService;
	@Mock
	private NaverDirectionsClient client;

	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	@AfterEach
	void shutDownExecutor() {
		executor.shutdownNow();
	}

	@Test
	void returnsAvailableWhenMonthlyUsageIsAvailable() {
		given(usageService.isCurrentMonthAvailable()).willReturn(true);

		DirectionsAvailabilityResponse response = service().getAvailability();

		assertThat(response).isEqualTo(new DirectionsAvailabilityResponse(DirectionsStatus.AVAILABLE));
		then(client).shouldHaveNoInteractions();
	}

	@Test
	void requiresNaverMapWhenMonthlyUsageIsUnavailable() {
		given(usageService.isCurrentMonthAvailable()).willReturn(false);

		DirectionsAvailabilityResponse response = service().getAvailability();

		assertThat(response).isEqualTo(new DirectionsAvailabilityResponse(DirectionsStatus.NAVER_MAP_REQUIRED));
		then(client).shouldHaveNoInteractions();
	}

	@Test
	void returnsCachedEstimateWithoutReservingUsageOrCallingNaver() {
		TourismContent firstDestination = content("126508", "경복궁", 37.578822, 126.976993);
		TourismContent secondDestination = content("126509", "창덕궁", 37.579431, 126.991042);
		NaverDirectionsResult firstResult = result(25, 12_300, 0);
		NaverDirectionsResult secondResult = result(32, 15_400, 1_500);
		given(tourismContentRepository.findByContentId("126508")).willReturn(Optional.of(firstDestination));
		given(tourismContentRepository.findByContentId("126509")).willReturn(Optional.of(secondDestination));
		given(usageService.reserveCurrentMonth()).willReturn(true);
		given(client.getDrivingEstimate(37.56649, 126.978, 37.578822, 126.976993))
				.willReturn(Optional.of(firstResult));
		given(client.getDrivingEstimate(37.56640, 126.978, 37.579431, 126.991042))
				.willReturn(Optional.of(secondResult));

		DirectionsService service = service();
		service.estimate(new DirectionsEstimateRequest(37.56649, 126.978, "126508"));
		clearInvocations(usageService, client);

		DirectionsEstimateResponse cached = service.estimate(
				new DirectionsEstimateRequest(37.56640, 126.978, "126508")
		);

		assertThat(cached).isEqualTo(available("126508", "경복궁", firstResult));
		then(usageService).shouldHaveNoInteractions();
		then(client).shouldHaveNoInteractions();

		DirectionsEstimateResponse differentDestination = service.estimate(
				new DirectionsEstimateRequest(37.56640, 126.978, "126509")
		);

		assertThat(differentDestination).isEqualTo(available("126509", "창덕궁", secondResult));
		then(usageService).should().reserveCurrentMonth();
		then(client).should().getDrivingEstimate(37.56640, 126.978, 37.579431, 126.991042);
	}

	@Test
	void reservesBeforeCallingNaverAndCachesSuccessfulResult() {
		TourismContent destination = content("126508", "경복궁", 37.578822, 126.976993);
		NaverDirectionsResult result = result(25, 12_300, 0);
		given(tourismContentRepository.findByContentId("126508")).willReturn(Optional.of(destination));
		given(usageService.reserveCurrentMonth()).willReturn(true);
		given(client.getDrivingEstimate(37.5665, 126.978, 37.578822, 126.976993))
				.willReturn(Optional.of(result));

		DirectionsEstimateResponse response = service().estimate(
				new DirectionsEstimateRequest(37.5665, 126.978, "126508")
		);

		assertThat(response).isEqualTo(available("126508", "경복궁", result));
		InOrder inOrder = inOrder(usageService, client);
		inOrder.verify(usageService).reserveCurrentMonth();
		inOrder.verify(client).getDrivingEstimate(37.5665, 126.978, 37.578822, 126.976993);
	}

	@Test
	void returnsMonthlyLimitFallbackWithoutCallingNaver() {
		given(tourismContentRepository.findByContentId("126508"))
				.willReturn(Optional.of(content("126508", "경복궁", 37.578822, 126.976993)));
		given(usageService.reserveCurrentMonth()).willReturn(false);

		DirectionsService service = service();
		DirectionsEstimateResponse response = service.estimate(request());
		DirectionsEstimateResponse secondResponse = service.estimate(request());

		assertThat(response).isEqualTo(fallback("126508", "경복궁", DirectionsFallbackReason.MONTHLY_LIMIT_REACHED));
		assertThat(secondResponse).isEqualTo(response);
		then(usageService).should(times(2)).reserveCurrentMonth();
		then(client).shouldHaveNoInteractions();
	}

	@Test
	void returnsCoordinateMissingFallbackWhenTourismContentHasNoCoordinates() {
		given(tourismContentRepository.findByContentId("126508"))
				.willReturn(Optional.of(content("126508", "경복궁", null, null)));

		DirectionsEstimateResponse response = service().estimate(request());

		assertThat(response).isEqualTo(fallback(
				"126508",
				"경복궁",
				DirectionsFallbackReason.DESTINATION_COORDINATES_MISSING
		));
		then(usageService).shouldHaveNoInteractions();
		then(client).shouldHaveNoInteractions();
	}

	@Test
	void returnsRouteNotFoundFallbackForEmptyNaverResult() {
		given(tourismContentRepository.findByContentId("126508"))
				.willReturn(Optional.of(content("126508", "경복궁", 37.578822, 126.976993)));
		given(usageService.reserveCurrentMonth()).willReturn(true);
		given(client.getDrivingEstimate(37.5665, 126.978, 37.578822, 126.976993))
				.willReturn(Optional.empty());

		DirectionsEstimateResponse response = service().estimate(request());

		assertThat(response).isEqualTo(fallback("126508", "경복궁", DirectionsFallbackReason.ROUTE_NOT_FOUND));
	}

	@Test
	void returnsApiUnavailableFallbackForRestClientException() {
		given(tourismContentRepository.findByContentId("126508"))
				.willReturn(Optional.of(content("126508", "경복궁", 37.578822, 126.976993)));
		given(usageService.reserveCurrentMonth()).willReturn(true);
		given(client.getDrivingEstimate(37.5665, 126.978, 37.578822, 126.976993))
				.willThrow(new RestClientException("upstream unavailable"));

		DirectionsEstimateResponse response = service().estimate(request());

		assertThat(response).isEqualTo(fallback(
				"126508",
				"경복궁",
				DirectionsFallbackReason.NAVER_API_UNAVAILABLE
		));
	}

	@Test
	void returnsApiUnavailableFallbackForSanitizedIllegalStateException() {
		given(tourismContentRepository.findByContentId("126508"))
				.willReturn(Optional.of(content("126508", "경복궁", 37.578822, 126.976993)));
		given(usageService.reserveCurrentMonth()).willReturn(true);
		given(client.getDrivingEstimate(37.5665, 126.978, 37.578822, 126.976993))
				.willThrow(new IllegalStateException("Naver Directions API 호출에 실패했습니다."));

		DirectionsEstimateResponse response = service().estimate(request());

		assertThat(response).isEqualTo(fallback(
				"126508",
				"경복궁",
				DirectionsFallbackReason.NAVER_API_UNAVAILABLE
		));
	}

	@Test
	void concurrentSameRouteFallbackRunsOnceAndLaterRequestRetries() throws Exception {
		CountDownLatch clientStarted = new CountDownLatch(1);
		CountDownLatch releaseClient = new CountDownLatch(1);
		AtomicInteger clientCalls = new AtomicInteger();
		NaverDirectionsResult success = result(25, 12_300, 0);
		given(tourismContentRepository.findByContentId("126508"))
				.willReturn(Optional.of(content("126508", "경복궁", 37.578822, 126.976993)));
		given(usageService.reserveCurrentMonth()).willReturn(true);
		given(client.getDrivingEstimate(37.5665, 126.978, 37.578822, 126.976993))
				.willAnswer(invocation -> {
					if (clientCalls.getAndIncrement() == 0) {
						clientStarted.countDown();
						assertThat(releaseClient.await(5, TimeUnit.SECONDS)).isTrue();
						return Optional.empty();
					}
					return Optional.of(success);
				});

		ObservedInFlightMap observedMap = new ObservedInFlightMap();
		DirectionsService service = service(new DirectionsRequestCoordinator(observedMap));
		Future<DirectionsEstimateResponse> first = executor.submit(() -> service.estimate(request()));
		assertThat(clientStarted.await(5, TimeUnit.SECONDS)).isTrue();
		Future<DirectionsEstimateResponse> second =
				executor.submit(() -> service.estimate(request()));
		assertThat(observedMap.awaitFollower()).isTrue();
		releaseClient.countDown();

		DirectionsEstimateResponse fallback = fallback(
				"126508", "경복궁", DirectionsFallbackReason.ROUTE_NOT_FOUND
		);
		assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(fallback);
		assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(fallback);
		then(usageService).should(times(1)).reserveCurrentMonth();
		then(client).should(times(1)).getDrivingEstimate(
				37.5665, 126.978, 37.578822, 126.976993
		);

		DirectionsEstimateResponse later = service.estimate(request());
		assertThat(later).isEqualTo(available("126508", "경복궁", success));
		then(usageService).should(times(2)).reserveCurrentMonth();
		then(client).should(times(2)).getDrivingEstimate(
				37.5665, 126.978, 37.578822, 126.976993
		);
	}

	@Test
	void throwsNotFoundForUnknownDestinationContentId() {
		given(tourismContentRepository.findByContentId("missing")).willReturn(Optional.empty());

		assertThatThrownBy(() -> service().estimate(
				new DirectionsEstimateRequest(37.5665, 126.978, "missing")
		))
				.isInstanceOf(DirectionsDestinationNotFoundException.class)
				.hasMessage("관광지 콘텐츠를 찾을 수 없습니다. contentId=missing");
		then(usageService).shouldHaveNoInteractions();
		then(client).shouldHaveNoInteractions();
	}

	private DirectionsService service() {
		return service(new DirectionsRequestCoordinator());
	}

	private DirectionsService service(DirectionsRequestCoordinator requestCoordinator) {
		return new DirectionsService(
				tourismContentRepository,
				usageService,
				client,
				new NaverDirectionsProperties(
						"https://example.com",
						Duration.ofSeconds(3),
						Duration.ofSeconds(7),
						50_000,
						10,
						100
				),
				requestCoordinator
		);
	}

	private DirectionsEstimateRequest request() {
		return new DirectionsEstimateRequest(37.5665, 126.978, "126508");
	}

	private NaverDirectionsResult result(int minutes, long distanceMeters, int tollFare) {
		return new NaverDirectionsResult(minutes, distanceMeters, tollFare, CALCULATED_AT);
	}

	private DirectionsEstimateResponse available(
			String contentId,
			String name,
			NaverDirectionsResult result
	) {
		return new DirectionsEstimateResponse(
				DirectionsStatus.AVAILABLE,
				contentId,
				name,
				result.travelTimeMinutes(),
				result.distanceMeters(),
				result.tollFare(),
				result.calculatedAt(),
				null
		);
	}

	private DirectionsEstimateResponse fallback(
			String contentId,
			String name,
			DirectionsFallbackReason reason
	) {
		return new DirectionsEstimateResponse(
				DirectionsStatus.NAVER_MAP_REQUIRED,
				contentId,
				name,
				null,
				null,
				null,
				null,
				reason
		);
	}

	private TourismContent content(String contentId, String title, Double latitude, Double longitude) {
		return new TourismContent(
				contentId,
				"12",
				title,
				"서울특별시 종로구 사직로 161",
				null,
				latitude,
				longitude,
				"11",
				"11110",
				"11-11110",
				"VE010100",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"20260708103000"
		);
	}
}
