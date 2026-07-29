package com.trip.whereU.directions.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

	private final ClientIpResolver resolver = new ClientIpResolver();

	@Test
	void usesRemoteAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.10");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
	}

	@Test
	void ignoresSpoofedForwardedHeaders() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.10");
		request.addHeader("X-Forwarded-For", "198.51.100.5");
		request.addHeader("Forwarded", "for=198.51.100.6");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
	}
}
