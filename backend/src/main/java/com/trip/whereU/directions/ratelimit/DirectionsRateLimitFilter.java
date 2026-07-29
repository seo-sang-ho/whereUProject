package com.trip.whereU.directions.ratelimit;

import com.trip.whereU.global.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class DirectionsRateLimitFilter extends OncePerRequestFilter {

	private static final String ESTIMATE_PATH = "/api/directions/estimate";
	private static final String RATE_LIMIT_MESSAGE =
			"자동차 시간 요청이 많아 네이버 지도 길찾기로 전환합니다.";
	private static final String UNAVAILABLE_MESSAGE =
			"자동차 시간 요청을 처리하지 못해 네이버 지도 길찾기로 전환합니다.";

	private final ClientIpResolver clientIpResolver;
	private final DirectionsRateLimitService rateLimitService;
	private final ObjectMapper objectMapper;

	public DirectionsRateLimitFilter(
			ClientIpResolver clientIpResolver,
			DirectionsRateLimitService rateLimitService,
			ObjectMapper objectMapper
	) {
		this.clientIpResolver = clientIpResolver;
		this.rateLimitService = rateLimitService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getServletPath();
		if (path.isEmpty()) {
			path = request.getRequestURI();
		}
		return !HttpMethod.POST.matches(request.getMethod())
				|| !ESTIMATE_PATH.equals(path);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		RateLimitDecision decision;
		try {
			decision = rateLimitService.tryAcquire(clientIpResolver.resolve(request));
		} catch (RuntimeException exception) {
			writeFailure(response, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE, null);
			return;
		}
		if (!decision.allowed()) {
			writeFailure(
					response,
					HttpStatus.TOO_MANY_REQUESTS,
					RATE_LIMIT_MESSAGE,
					decision.retryAfterSeconds()
			);
			return;
		}
		filterChain.doFilter(request, response);
	}

	private void writeFailure(
			HttpServletResponse response,
			HttpStatus status,
			String message,
			Long retryAfterSeconds
	) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		if (retryAfterSeconds != null) {
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
		}
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(message));
	}
}
