package com.trip.whereU.map.service;

import com.trip.whereU.staystrength.repository.TourismStayStrengthRegionProjection;
import com.trip.whereU.staystrength.repository.TourismStayStrengthRepository;
import com.trip.whereU.map.client.NaverGeocodingClient;
import com.trip.whereU.map.dto.RegionCoordinate;
import com.trip.whereU.map.dto.TourismRegionResponse;
import com.trip.whereU.map.dto.TourismRegionSyncResponse;
import com.trip.whereU.map.entity.TourismRegion;
import com.trip.whereU.map.repository.TourismRegionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TourismRegionService {

	private final TourismStayStrengthRepository tourismStayStrengthRepository;
	private final TourismRegionRepository tourismRegionRepository;
	private final NaverGeocodingClient naverGeocodingClient;

	public TourismRegionService(
			TourismStayStrengthRepository tourismStayStrengthRepository,
			TourismRegionRepository tourismRegionRepository,
			NaverGeocodingClient naverGeocodingClient
	) {
		this.tourismStayStrengthRepository = tourismStayStrengthRepository;
		this.tourismRegionRepository = tourismRegionRepository;
		this.naverGeocodingClient = naverGeocodingClient;
	}

	public TourismRegionSyncResponse syncMissingCoordinates() {
		List<TourismStayStrengthRegionProjection> targets = tourismStayStrengthRepository.findDistinctRegions();
		Map<String, TourismRegion> existingRegions = tourismRegionRepository
				.findAllById(targets.stream().map(TourismStayStrengthRegionProjection::getRegionCode).toList())
				.stream()
				.collect(Collectors.toMap(TourismRegion::getRegionCode, Function.identity()));

		List<TourismRegion> regionsToSave = new ArrayList<>();
		List<String> failedRegionCodes = new ArrayList<>();
		int skippedCount = 0;

		for (TourismStayStrengthRegionProjection target : targets) {
			if (existingRegions.containsKey(target.getRegionCode())) {
				skippedCount++;
				continue;
			}

			Optional<RegionCoordinate> coordinate = naverGeocodingClient.geocode(target.getRegionName());
			if (coordinate.isEmpty()) {
				failedRegionCodes.add(target.getRegionCode());
				continue;
			}

			RegionCoordinate value = coordinate.get();
			regionsToSave.add(new TourismRegion(
					target.getRegionCode(),
					target.getRegionName(),
					value.latitude(),
					value.longitude()
			));
		}

		int savedCount = saveRegions(regionsToSave);
		return new TourismRegionSyncResponse(
				targets.size(),
				savedCount,
				skippedCount,
				failedRegionCodes.size(),
				List.copyOf(failedRegionCodes)
		);
	}

	@Transactional(readOnly = true)
	public List<TourismRegionResponse> getAllRegions() {
		return tourismRegionRepository.findAll().stream()
				.map(TourismRegionResponse::from)
				.toList();
	}

	private int saveRegions(List<TourismRegion> regions) {
		tourismRegionRepository.saveAll(regions);
		return regions.size();
	}
}
