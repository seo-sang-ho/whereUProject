package com.trip.whereU.directions.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DirectionsRateLimitFilterTest {

	@Mock
	private DirectionsRateLimitService rateLimitService;

	private DirectionsRateLimitFilter filter;
	private MockHttpServletResponse response;
	private MockFilterChain chain;

	@BeforeEach
	void setUp() {
		filter = new DirectionsRateLimitFilter(
				new ClientIpResolver(),
				rateLimitService,
				new ObjectMapper()
		);
		response = new MockHttpServletResponse();
		chain = new MockFilterChain();
	}

	@Test
	void rejectsEstimatePostWith429WithoutCallingDownstream() throws Exception {
		given(rateLimitService.tryAcquire("203.0.113.10"))
				.willReturn(RateLimitDecision.rejected(6));
		MockHttpServletRequest request = estimateRequest("203.0.113.10");
		request.addHeader("X-Forwarded-For", "198.51.100.5");

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(response.getHeader("Retry-After")).isEqualTo("6");
		assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(response.getContentAsString()).contains(
				"자동차 시간 요청이 많아 네이버 지도 길찾기로 전환합니다."
		);
		assertThat(response.getContentAsString())
				.doesNotContain(
						"203.0.113.10",
						"198.51.100.5",
						"37.5665",
						"126.978",
						"현재 사용 횟수",
						"limiter unavailable"
				);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void allowsEstimatePostWhenTokenIsAvailable() throws Exception {
		given(rateLimitService.tryAcquire("203.0.113.10"))
				.willReturn(RateLimitDecision.permitted());

		filter.doFilter(estimateRequest("203.0.113.10"), response, chain);

		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void rejectsEstimatePostWithMatrixParametersWithoutCallingDownstream() throws Exception {
		given(rateLimitService.tryAcquire("203.0.113.10"))
				.willReturn(RateLimitDecision.rejected(6));
		MockHttpServletRequest request = estimateRequest("203.0.113.10");
		request.setRequestURI("/api/directions/estimate;x=y");
		request.setServletPath("/api/directions/estimate;x=y");

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(429);
		assertThat(chain.getRequest()).isNull();
	}

	@ParameterizedTest
	@CsvSource({
			"GET, /api/directions/estimate",
			"POST, /api/directions/availability",
			"GET, /api/directions/availability",
			"POST, /api/recommendations/personalized",
			"POST, /api/directions/estimate-extra;x=y"
	})
	void skipsEveryNonTargetMethodOrPath(String method, String path) throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setServletPath(path);

		filter.doFilter(request, response, chain);

		then(rateLimitService).shouldHaveNoInteractions();
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void failsClosedWith503WhenLimiterBreaks() throws Exception {
		given(rateLimitService.tryAcquire("203.0.113.10"))
				.willThrow(new IllegalStateException("limiter unavailable"));

		filter.doFilter(estimateRequest("203.0.113.10"), response, chain);

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(response.getHeader("Retry-After")).isNull();
		assertThat(response.getContentAsString()).doesNotContain("limiter unavailable");
		assertThat(chain.getRequest()).isNull();
	}

	private MockHttpServletRequest estimateRequest(String remoteAddress) {
		MockHttpServletRequest request = new MockHttpServletRequest(
				HttpMethod.POST.name(),
				"/api/directions/estimate"
		);
		request.setServletPath("/api/directions/estimate");
		request.setRemoteAddr(remoteAddress);
		return request;
	}
}
