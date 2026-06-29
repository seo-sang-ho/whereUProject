package com.trip.whereU.demand.controller;

import com.trip.whereU.demand.dto.TourismDemandDebugResponse;
import com.trip.whereU.demand.dto.TourismDemandResponse;
import com.trip.whereU.demand.dto.TourismDemandSyncResponse;
import com.trip.whereU.demand.service.TourismDemandService;
import com.trip.whereU.global.dto.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demands")
public class TourismDemandController {

	private final TourismDemandService tourismDemandService;

	public TourismDemandController(TourismDemandService tourismDemandService) {
		this.tourismDemandService = tourismDemandService;
	}

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<TourismDemandSyncResponse>> syncDemandData() {
        return ResponseEntity.ok(ApiResponse.success(tourismDemandService.syncDemandData()));
    }

	@GetMapping
	public ResponseEntity<ApiResponse<List<TourismDemandResponse>>> getAllDemandSignals() {
		return ResponseEntity.ok(ApiResponse.success(tourismDemandService.getAllDemandSignals()));
	}

	@GetMapping("/debug/request-uri")
	public ResponseEntity<ApiResponse<TourismDemandDebugResponse>> getOpenApiRequestUri() {
		return ResponseEntity.ok(ApiResponse.success(
				new TourismDemandDebugResponse(tourismDemandService.getMaskedOpenApiRequestUri())
		));
	}

	@GetMapping("/bounds")
	public ResponseEntity<ApiResponse<List<TourismDemandResponse>>> getDemandSignalsInBounds(
			@RequestParam double minLatitude,
			@RequestParam double maxLatitude,
			@RequestParam double minLongitude,
			@RequestParam double maxLongitude
	) {
		return ResponseEntity.ok(ApiResponse.success(tourismDemandService.getDemandSignalsInBounds(
				minLatitude,
				maxLatitude,
				minLongitude,
				maxLongitude
		)));
	}
}
