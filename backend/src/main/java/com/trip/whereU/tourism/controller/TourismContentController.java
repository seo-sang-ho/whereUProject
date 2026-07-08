package com.trip.whereU.tourism.controller;

import com.trip.whereU.global.dto.ApiResponse;
import com.trip.whereU.tourism.dto.TourismContentResponse;
import com.trip.whereU.tourism.dto.TourismContentSyncResponse;
import com.trip.whereU.tourism.service.TourismContentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tourism-contents")
public class TourismContentController {

	private final TourismContentService service;

	public TourismContentController(TourismContentService service) {
		this.service = service;
	}

	@PostMapping("/sync")
	public ResponseEntity<ApiResponse<TourismContentSyncResponse>> sync(
			@RequestParam(required = false) String contentTypeId,
			@RequestParam(defaultValue = "C") String arrange,
			@RequestParam(required = false) String lDongRegnCd,
			@RequestParam(required = false) String lDongSignguCd,
			@RequestParam(required = false) String lclsSystm1,
			@RequestParam(required = false) String lclsSystm2,
			@RequestParam(required = false) String lclsSystm3,
			@RequestParam(defaultValue = "100") int pageSize
	) {
		return ResponseEntity.ok(ApiResponse.success(
				service.sync(
						contentTypeId,
						arrange,
						lDongRegnCd,
						lDongSignguCd,
						lclsSystm1,
						lclsSystm2,
						lclsSystm3,
						pageSize
				)
		));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<Page<TourismContentResponse>>> getContents(
			@RequestParam(required = false) String areaCode,
			@RequestParam(required = false) String sigunguCode,
			@RequestParam(required = false) String legalDongCode,
			@RequestParam(required = false) String categoryCode,
			@RequestParam(required = false) String contentTypeId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size
	) {
		return ResponseEntity.ok(ApiResponse.success(service.getContents(
				areaCode,
				sigunguCode,
				legalDongCode,
				categoryCode,
				contentTypeId,
				PageRequest.of(page, size)
		)));
	}

	@GetMapping("/{contentId}")
	public ResponseEntity<ApiResponse<TourismContentResponse>> getContent(
			@PathVariable String contentId
	) {
		return ResponseEntity.ok(ApiResponse.success(service.getContent(contentId)));
	}
}
