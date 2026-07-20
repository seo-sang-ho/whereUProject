package com.trip.whereU.directions.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trip.whereU.directions.dto.DirectionsAvailabilityResponse;
import com.trip.whereU.directions.dto.DirectionsEstimateRequest;
import com.trip.whereU.directions.dto.DirectionsEstimateResponse;
import com.trip.whereU.directions.dto.DirectionsStatus;
import com.trip.whereU.directions.exception.DirectionsDestinationNotFoundException;
import com.trip.whereU.directions.service.DirectionsService;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DirectionsController.class)
class DirectionsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DirectionsService directionsService;

	@Test
	void wrapsAvailabilityInApiResponse() throws Exception {
		given(directionsService.getAvailability())
				.willReturn(new DirectionsAvailabilityResponse(DirectionsStatus.AVAILABLE));

		mockMvc.perform(get("/api/directions/availability"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("AVAILABLE"))
				.andExpect(jsonPath("$.message").doesNotExist());
	}

	@Test
	void returnsSuccessfulEstimate() throws Exception {
		DirectionsEstimateResponse response = DirectionsEstimateResponse.available(
				"126508",
				"경복궁",
				25,
				12_300,
				0,
				OffsetDateTime.parse("2026-07-20T15:00:00+09:00")
		);
		given(directionsService.estimate(any(DirectionsEstimateRequest.class))).willReturn(response);

		mockMvc.perform(post("/api/directions/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(validRequest()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.status").value("AVAILABLE"))
				.andExpect(jsonPath("$.data.destinationContentId").value("126508"))
				.andExpect(jsonPath("$.data.destinationName").value("경복궁"))
				.andExpect(jsonPath("$.data.travelTimeMinutes").value(25))
				.andExpect(jsonPath("$.data.distanceMeters").value(12_300))
				.andExpect(jsonPath("$.data.tollFare").value(0))
				.andExpect(jsonPath("$.data.calculatedAt").value("2026-07-20T15:00:00+09:00"))
				.andExpect(jsonPath("$.data.fallbackReason").doesNotExist());
	}

	@ParameterizedTest
	@MethodSource("invalidRequests")
	void rejectsInvalidEstimateRequest(String requestBody) throws Exception {
		mockMvc.perform(post("/api/directions/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(requestBody))
				.andExpect(status().isBadRequest());

		then(directionsService).shouldHaveNoInteractions();
	}

	@Test
	void mapsUnknownDestinationToNotFoundWithoutSensitiveDetails() throws Exception {
		given(directionsService.estimate(any(DirectionsEstimateRequest.class)))
				.willThrow(new DirectionsDestinationNotFoundException("126508"));

		mockMvc.perform(post("/api/directions/estimate")
					.contentType(MediaType.APPLICATION_JSON)
					.content(validRequest()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.message").value("관광지 콘텐츠를 찾을 수 없습니다. contentId=126508"))
				.andExpect(content().string(not(containsString("37.5665"))))
				.andExpect(content().string(not(containsString("126.978"))))
				.andExpect(content().string(not(containsString("api-key"))));
	}

	private static Stream<Arguments> invalidRequests() {
		return Stream.of(
				Arguments.of("""
						{
						  "originLatitude": 91,
						  "originLongitude": 126.978,
						  "destinationContentId": "126508"
						}
						"""),
				Arguments.of("""
						{
						  "originLatitude": 37.5665,
						  "originLongitude": 181,
						  "destinationContentId": "126508"
						}
						"""),
				Arguments.of("""
						{
						  "originLatitude": 37.5665,
						  "originLongitude": 126.978,
						  "destinationContentId": " "
						}
						""")
		);
	}

	private String validRequest() {
		return """
				{
				  "originLatitude": 37.5665,
				  "originLongitude": 126.978,
				  "destinationContentId": "126508"
				}
				""";
	}
}
