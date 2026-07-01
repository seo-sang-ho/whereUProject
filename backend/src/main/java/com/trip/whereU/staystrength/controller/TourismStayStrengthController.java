package com.trip.whereU.staystrength.controller;

import com.trip.whereU.staystrength.dto.TourismStayStrengthDebugResponse;
import com.trip.whereU.staystrength.dto.TourismStayStrengthLatestResponse;
import com.trip.whereU.staystrength.dto.TourismStayStrengthResponse;
import com.trip.whereU.staystrength.dto.TourismStayStrengthSyncResponse;
import com.trip.whereU.staystrength.service.TourismStayStrengthService;
import com.trip.whereU.global.dto.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stay-strengths")
public class TourismStayStrengthController {

	private final TourismStayStrengthService tourismStayStrengthService;

	public TourismStayStrengthController(TourismStayStrengthService tourismStayStrengthService) {
		this.tourismStayStrengthService = tourismStayStrengthService;
	}

	@PostMapping("/sync")
	public ResponseEntity<ApiResponse<TourismStayStrengthSyncResponse>> syncStayStrengthData() {
		return ResponseEntity.ok(ApiResponse.success(tourismStayStrengthService.syncStayStrengthData()));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<TourismStayStrengthResponse>>> getAllStayStrengthLevels() {
		return ResponseEntity.ok(ApiResponse.success(tourismStayStrengthService.getAllStayStrengthLevels()));
	}

	@GetMapping("/latest")
	public ResponseEntity<ApiResponse<TourismStayStrengthLatestResponse>> getLatestStayStrengthLevels() {
		return ResponseEntity.ok(ApiResponse.success(tourismStayStrengthService.getLatestStayStrengthLevels()));
	}

	@GetMapping("/latest/bounds")
	public ResponseEntity<ApiResponse<TourismStayStrengthLatestResponse>> getLatestStayStrengthLevelsInBounds(
			@RequestParam double minLatitude,
			@RequestParam double maxLatitude,
			@RequestParam double minLongitude,
			@RequestParam double maxLongitude
	) {
		return ResponseEntity.ok(ApiResponse.success(tourismStayStrengthService.getLatestStayStrengthLevelsInBounds(
				minLatitude,
				maxLatitude,
				minLongitude,
				maxLongitude
		)));
	}

	@GetMapping("/debug/request-uri")
	public ResponseEntity<ApiResponse<TourismStayStrengthDebugResponse>> getOpenApiRequestUri() {
		return ResponseEntity.ok(ApiResponse.success(
				new TourismStayStrengthDebugResponse(tourismStayStrengthService.getMaskedOpenApiRequestUri())
		));
	}

	@GetMapping("/bounds")
	public ResponseEntity<ApiResponse<List<TourismStayStrengthResponse>>> getStayStrengthLevelsInBounds(
			@RequestParam double minLatitude,
			@RequestParam double maxLatitude,
			@RequestParam double minLongitude,
			@RequestParam double maxLongitude
	) {
		return ResponseEntity.ok(ApiResponse.success(tourismStayStrengthService.getStayStrengthLevelsInBounds(
				minLatitude,
				maxLatitude,
				minLongitude,
				maxLongitude
		)));
	}
}
