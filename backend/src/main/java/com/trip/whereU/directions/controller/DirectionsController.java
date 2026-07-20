package com.trip.whereU.directions.controller;

import com.trip.whereU.directions.dto.DirectionsAvailabilityResponse;
import com.trip.whereU.directions.dto.DirectionsEstimateRequest;
import com.trip.whereU.directions.dto.DirectionsEstimateResponse;
import com.trip.whereU.directions.service.DirectionsService;
import com.trip.whereU.global.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/directions")
public class DirectionsController {

	private final DirectionsService directionsService;

	public DirectionsController(DirectionsService directionsService) {
		this.directionsService = directionsService;
	}

	@GetMapping("/availability")
	public ResponseEntity<ApiResponse<DirectionsAvailabilityResponse>> availability() {
		return ResponseEntity.ok(ApiResponse.success(directionsService.getAvailability()));
	}

	@PostMapping("/estimate")
	public ResponseEntity<ApiResponse<DirectionsEstimateResponse>> estimate(
			@Valid @RequestBody DirectionsEstimateRequest request
	) {
		return ResponseEntity.ok(ApiResponse.success(directionsService.estimate(request)));
	}
}
