package com.trip.whereU.tourism.controller;

import com.trip.whereU.global.dto.ApiResponse;
import com.trip.whereU.resourcedemand.entity.TourismTheme;
import com.trip.whereU.tourism.dto.TourismContentRecommendationTopSyncResponse;
import com.trip.whereU.tourism.service.TourismContentRecommendationSyncService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/admin/tourism-contents")
public class TourismContentAdminController {

	private final TourismContentRecommendationSyncService recommendationSyncService;
	private final String adminToken;

	public TourismContentAdminController(
			TourismContentRecommendationSyncService recommendationSyncService,
			@Value("${tourism.content-api.admin-token:}") String adminToken
	) {
		this.recommendationSyncService = recommendationSyncService;
		this.adminToken = adminToken;
	}

	@PostMapping("/sync/recommendation-top")
	public ResponseEntity<ApiResponse<TourismContentRecommendationTopSyncResponse>> syncRecommendationTopRegions(
			@RequestHeader(value = "X-Admin-Token", required = false) String requestAdminToken,
			@RequestParam(defaultValue = "10") int limit,
			@RequestParam(defaultValue = "10") int pageSize,
			@RequestParam(required = false) String contentTypeId,
			@RequestParam(defaultValue = "C") String arrange,
			@RequestParam(required = false) String lclsSystm1,
			@RequestParam(required = false) String lclsSystm2,
			@RequestParam(required = false) String lclsSystm3
	) {
		if (!isAuthorized(requestAdminToken)) {
			return ResponseEntity
					.status(HttpStatus.FORBIDDEN)
					.body(ApiResponse.failure("관리자 토큰이 올바르지 않습니다."));
		}
		return ResponseEntity.ok(ApiResponse.success(
				recommendationSyncService.syncValueRecommendationTopRegions(
						contentTypeId,
						arrange,
						lclsSystm1,
						lclsSystm2,
						lclsSystm3,
						limit,
						pageSize
				)
		));
	}

	@PostMapping("/sync/personalized-top")
	public ResponseEntity<ApiResponse<TourismContentRecommendationTopSyncResponse>> syncPersonalizedTopRegions(
			@RequestHeader(value = "X-Admin-Token", required = false) String requestAdminToken,
			@RequestParam List<TourismTheme> themes,
			@RequestParam(defaultValue = "10") int limit,
			@RequestParam(defaultValue = "10") int pageSize,
			@RequestParam(required = false) String contentTypeId,
			@RequestParam(defaultValue = "C") String arrange,
			@RequestParam(required = false) String lclsSystm1,
			@RequestParam(required = false) String lclsSystm2,
			@RequestParam(required = false) String lclsSystm3
	) {
		if (!isAuthorized(requestAdminToken)) {
			return ResponseEntity
					.status(HttpStatus.FORBIDDEN)
					.body(ApiResponse.failure("관리자 토큰이 올바르지 않습니다."));
		}
		return ResponseEntity.ok(ApiResponse.success(
				recommendationSyncService.syncPersonalizedRecommendationTopRegions(
						themes,
						contentTypeId,
						arrange,
						lclsSystm1,
						lclsSystm2,
						lclsSystm3,
						limit,
						pageSize
				)
		));
	}

	@PostMapping("/sync/recommendation-candidates")
	public ResponseEntity<ApiResponse<TourismContentRecommendationTopSyncResponse>> syncRecommendationCandidateRegions(
			@RequestHeader(value = "X-Admin-Token", required = false) String requestAdminToken,
			@RequestParam(required = false) List<TourismTheme> themes,
			@RequestParam(defaultValue = "100") int limit,
			@RequestParam(defaultValue = "10") int pageSize,
			@RequestParam(required = false) String contentTypeId,
			@RequestParam(defaultValue = "C") String arrange,
			@RequestParam(required = false) String lclsSystm1,
			@RequestParam(required = false) String lclsSystm2,
			@RequestParam(required = false) String lclsSystm3
	) {
		if (!isAuthorized(requestAdminToken)) {
			return ResponseEntity
					.status(HttpStatus.FORBIDDEN)
					.body(ApiResponse.failure("관리자 토큰이 올바르지 않습니다."));
		}
		return ResponseEntity.ok(ApiResponse.success(
				recommendationSyncService.syncRecommendationCandidateRegions(
						themes,
						contentTypeId,
						arrange,
						lclsSystm1,
						lclsSystm2,
						lclsSystm3,
						limit,
						pageSize
				)
		));
	}

	private boolean isAuthorized(String requestAdminToken) {
		if (!StringUtils.hasText(adminToken)) {
			return true;
		}
		if (!StringUtils.hasText(requestAdminToken)) {
			return false;
		}
		return MessageDigest.isEqual(
				adminToken.getBytes(StandardCharsets.UTF_8),
				requestAdminToken.getBytes(StandardCharsets.UTF_8)
		);
	}
}
