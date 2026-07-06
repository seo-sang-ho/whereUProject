package com.trip.whereU.resourcedemand.controller;

import com.trip.whereU.global.dto.ApiResponse;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandLatestResponse;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncJobResponse;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.service.TourismResourceDemandService;
import com.trip.whereU.resourcedemand.service.TourismResourceDemandSyncJobService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resource-demands")
public class TourismResourceDemandController {

	private final TourismResourceDemandService service;
	private final TourismResourceDemandSyncJobService syncJobService;

	public TourismResourceDemandController(
			TourismResourceDemandService service,
			TourismResourceDemandSyncJobService syncJobService
	) {
		this.service = service;
		this.syncJobService = syncJobService;
	}

	@PostMapping("/sync")
	public ResponseEntity<ApiResponse<TourismResourceDemandSyncJobResponse>> sync(
			@RequestParam(required = false) ResourceDemandType resourceType,
			@RequestParam(required = false) String indicatorCode,
			@RequestParam(defaultValue = "false") boolean force
	) {
		return ResponseEntity
				.status(HttpStatus.ACCEPTED)
				.body(ApiResponse.success(syncJobService.start(resourceType, indicatorCode, force)));
	}

	@GetMapping("/sync/{jobId}")
	public ResponseEntity<ApiResponse<TourismResourceDemandSyncJobResponse>> getSyncJob(
			@PathVariable UUID jobId
	) {
		return ResponseEntity.ok(ApiResponse.success(syncJobService.get(jobId)));
	}

	@GetMapping("/latest")
	public ResponseEntity<ApiResponse<TourismResourceDemandLatestResponse>> getLatest() {
		return ResponseEntity.ok(ApiResponse.success(service.getLatest()));
	}
}
