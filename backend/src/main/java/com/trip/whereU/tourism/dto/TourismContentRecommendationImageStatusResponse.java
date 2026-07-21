package com.trip.whereU.tourism.dto;

import java.util.List;

public record TourismContentRecommendationImageStatusResponse(
		int checkedRegionCount,
		int imageReadyRegionCount,
		int missingImageRegionCount,
		int noContentRegionCount,
		int mappingMissingRegionCount,
		List<RegionImageStatus> results
) {

	public static TourismContentRecommendationImageStatusResponse of(List<RegionImageStatus> results) {
		int imageReadyRegionCount = countByStatus(results, "IMAGE_READY");
		int missingImageRegionCount = countByStatus(results, "MISSING_IMAGE");
		int noContentRegionCount = countByStatus(results, "NO_CONTENT");
		int mappingMissingRegionCount = countByStatus(results, "MAPPING_MISSING");
		return new TourismContentRecommendationImageStatusResponse(
				results.size(),
				imageReadyRegionCount,
				missingImageRegionCount,
				noContentRegionCount,
				mappingMissingRegionCount,
				List.copyOf(results)
		);
	}

	private static int countByStatus(List<RegionImageStatus> results, String status) {
		return (int) results.stream()
				.filter(result -> status.equals(result.status()))
				.count();
	}

	public record RegionImageStatus(
			int rank,
			String regionCode,
			String regionName,
			String legalDongCode,
			String status,
			int contentCount,
			int imageContentCount,
			String message
	) {
	}
}
