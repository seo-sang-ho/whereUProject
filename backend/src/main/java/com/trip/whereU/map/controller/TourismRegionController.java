package com.trip.whereU.map.controller;

import com.trip.whereU.global.dto.ApiResponse;
import com.trip.whereU.map.dto.TourismRegionResponse;
import com.trip.whereU.map.dto.TourismRegionSyncResponse;
import com.trip.whereU.map.service.TourismRegionService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
public class TourismRegionController {

	private final TourismRegionService tourismRegionService;

	public TourismRegionController(TourismRegionService tourismRegionService) {
		this.tourismRegionService = tourismRegionService;
	}

	@PostMapping("/sync-coordinates")
	public ResponseEntity<ApiResponse<TourismRegionSyncResponse>> syncCoordinates() {
		return ResponseEntity.ok(ApiResponse.success(tourismRegionService.syncMissingCoordinates()));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<TourismRegionResponse>>> getAllRegions() {
		return ResponseEntity.ok(ApiResponse.success(tourismRegionService.getAllRegions()));
	}
}
