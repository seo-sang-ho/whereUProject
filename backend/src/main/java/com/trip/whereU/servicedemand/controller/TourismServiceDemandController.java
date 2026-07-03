package com.trip.whereU.servicedemand.controller;

import com.trip.whereU.global.dto.ApiResponse;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandLatestResponse;
import com.trip.whereU.servicedemand.dto.TourismServiceDemandSyncResponse;
import com.trip.whereU.servicedemand.service.TourismServiceDemandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-demands")
public class TourismServiceDemandController {

	private final TourismServiceDemandService service;

	public TourismServiceDemandController(TourismServiceDemandService service) {
		this.service = service;
	}

	@PostMapping("/sync")
	public ResponseEntity<ApiResponse<TourismServiceDemandSyncResponse>> sync() {
		return ResponseEntity.ok(ApiResponse.success(service.sync()));
	}

	@GetMapping("/latest")
	public ResponseEntity<ApiResponse<TourismServiceDemandLatestResponse>> getLatest() {
		return ResponseEntity.ok(ApiResponse.success(service.getLatest()));
	}

	@GetMapping("/latest/bounds")
	public ResponseEntity<ApiResponse<TourismServiceDemandLatestResponse>> getLatestInBounds(
			@RequestParam double minLatitude,
			@RequestParam double maxLatitude,
			@RequestParam double minLongitude,
			@RequestParam double maxLongitude
	) {
		return ResponseEntity.ok(ApiResponse.success(service.getLatestInBounds(
				minLatitude,
				maxLatitude,
				minLongitude,
				maxLongitude
		)));
	}
}
