package com.trip.whereU.recommendation.controller;

import com.trip.whereU.global.dto.ApiResponse;
import com.trip.whereU.recommendation.dto.ValueRecommendationResponse;
import com.trip.whereU.recommendation.dto.PersonalizedRecommendationResponse;
import com.trip.whereU.recommendation.service.PersonalizedRecommendationService;
import com.trip.whereU.recommendation.service.ValueRecommendationService;
import com.trip.whereU.resourcedemand.entity.TourismTheme;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class ValueRecommendationController {

	private final ValueRecommendationService service;
	private final PersonalizedRecommendationService personalizedService;

	public ValueRecommendationController(
			ValueRecommendationService service,
			PersonalizedRecommendationService personalizedService
	) {
		this.service = service;
		this.personalizedService = personalizedService;
	}

	@GetMapping("/value")
	public ResponseEntity<ApiResponse<ValueRecommendationResponse>> getValueRecommendations(
			@RequestParam(defaultValue = "10") int limit
	) {
		return ResponseEntity.ok(ApiResponse.success(service.getLatestValueRecommendations(limit)));
	}

	@GetMapping("/personalized")
	public ResponseEntity<ApiResponse<PersonalizedRecommendationResponse>> getPersonalizedRecommendations(
			@RequestParam List<TourismTheme> themes,
			@RequestParam(defaultValue = "10") int limit
	) {
		return ResponseEntity.ok(ApiResponse.success(
				personalizedService.getLatestRecommendations(themes, limit)
		));
	}
}
