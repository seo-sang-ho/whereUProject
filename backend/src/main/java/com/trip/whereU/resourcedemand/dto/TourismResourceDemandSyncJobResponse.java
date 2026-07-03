package com.trip.whereU.resourcedemand.dto;

import com.trip.whereU.resourcedemand.entity.ResourceDemandSyncJobStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record TourismResourceDemandSyncJobResponse(
		UUID jobId,
		ResourceDemandSyncJobStatus status,
		LocalDateTime startedAt,
		LocalDateTime completedAt,
		TourismResourceDemandSyncResponse result,
		String errorMessage
) {
}
