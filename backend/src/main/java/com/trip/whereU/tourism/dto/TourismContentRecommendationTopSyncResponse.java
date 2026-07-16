package com.trip.whereU.tourism.dto;

import java.util.List;

public record TourismContentRecommendationTopSyncResponse(
		int requestedRegionCount,
		int successRegionCount,
		int failedRegionCount,
		int totalFetchedCount,
		int totalSavedCount,
		List<RegionResult> results
) {

	public static TourismContentRecommendationTopSyncResponse of(List<RegionResult> results) {
		int successRegionCount = (int) results.stream().filter(RegionResult::success).count();
		int totalFetchedCount = results.stream().mapToInt(RegionResult::fetchedCount).sum();
		int totalSavedCount = results.stream().mapToInt(RegionResult::savedCount).sum();
		return new TourismContentRecommendationTopSyncResponse(
				results.size(),
				successRegionCount,
				results.size() - successRegionCount,
				totalFetchedCount,
				totalSavedCount,
				List.copyOf(results)
		);
	}

	public record RegionResult(
			int rank,
			String regionCode,
			String regionName,
			String legalDongCode,
			boolean success,
			int fetchedCount,
			int savedCount,
			int pageRequestCount,
			String message
	) {

		public static RegionResult success(
				int rank,
				String regionCode,
				String regionName,
				String legalDongCode,
				TourismContentSyncResponse syncResponse
		) {
			return new RegionResult(
					rank,
					regionCode,
					regionName,
					legalDongCode,
					true,
					syncResponse.fetchedCount(),
					syncResponse.savedCount(),
					syncResponse.pageRequestCount(),
					null
			);
		}

		public static RegionResult failure(
				int rank,
				String regionCode,
				String regionName,
				String legalDongCode,
				String message
		) {
			return new RegionResult(rank, regionCode, regionName, legalDongCode, false, 0, 0, 0, message);
		}
	}
}
