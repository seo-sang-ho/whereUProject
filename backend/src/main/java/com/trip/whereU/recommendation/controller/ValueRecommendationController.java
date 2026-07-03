package com.trip.whereU.recommendation.controller;

import com.trip.whereU.global.dto.ApiResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationResponse;
import com.trip.whereU.recommendation.service.ValueRecommendationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class ValueRecommendationController {

	private final ValueRecommendationService service;

	public ValueRecommendationController(ValueRecommendationService service) {
		this.service = service;
	}

	@GetMapping("/value")
	public ResponseEntity<ApiResponse<ValueRecommendationResponse>> getValueRecommendations(
			@RequestParam(defaultValue = "10") int limit
	) {
		return ResponseEntity.ok(ApiResponse.success(service.getLatestValueRecommendations(limit)));
	}
}
