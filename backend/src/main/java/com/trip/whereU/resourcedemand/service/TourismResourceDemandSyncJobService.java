package com.trip.whereU.resourcedemand.service;

import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncJobResponse;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncResponse;
import com.trip.whereU.resourcedemand.entity.ResourceDemandSyncJobStatus;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.exception.ResourceDemandSyncAlreadyRunningException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TourismResourceDemandSyncJobService {

	private static final String FAILED_MESSAGE = "동기화 작업 중 오류가 발생했습니다.";

	private final TourismResourceDemandService syncService;
	private final Executor executor;
	private final Map<UUID, SyncJob> jobs = new ConcurrentHashMap<>();
	private UUID activeJobId;

	public TourismResourceDemandSyncJobService(
			TourismResourceDemandService syncService,
			@Qualifier("resourceDemandSyncExecutor") Executor executor
	) {
		this.syncService = syncService;
		this.executor = executor;
	}

	public synchronized TourismResourceDemandSyncJobResponse start(
			ResourceDemandType resourceType,
			String indicatorCode
	) {
		validateRequest(resourceType, indicatorCode);
		if (activeJobId != null) {
			throw new ResourceDemandSyncAlreadyRunningException();
		}

		UUID jobId = UUID.randomUUID();
		jobs.put(jobId, SyncJob.running(jobId));
		activeJobId = jobId;
		try {
			executor.execute(() -> execute(jobId, resourceType, indicatorCode));
		} catch (RuntimeException exception) {
			jobs.remove(jobId);
			activeJobId = null;
			throw exception;
		}
		return get(jobId);
	}

	public TourismResourceDemandSyncJobResponse get(UUID jobId) {
		SyncJob job = jobs.get(jobId);
		if (job == null) {
			throw new IllegalArgumentException("존재하지 않는 동기화 작업입니다.");
		}
		return job.toResponse();
	}

	private void execute(
			UUID jobId,
			ResourceDemandType resourceType,
			String indicatorCode
	) {
		try {
			TourismResourceDemandSyncResponse result = resourceType == null
					? syncService.sync()
					: syncService.sync(resourceType, indicatorCode);
			ResourceDemandSyncJobStatus status = result.failedIndicators().isEmpty()
					? ResourceDemandSyncJobStatus.COMPLETED
					: ResourceDemandSyncJobStatus.PARTIAL_FAILED;
			jobs.computeIfPresent(jobId, (id, job) -> job.complete(status, result));
		} catch (Exception exception) {
			jobs.computeIfPresent(jobId, (id, job) -> job.fail());
		} finally {
			release(jobId);
		}
	}

	private void validateRequest(ResourceDemandType resourceType, String indicatorCode) {
		if (resourceType == null && indicatorCode == null) {
			return;
		}
		syncService.validateIndicator(resourceType, indicatorCode);
	}

	private synchronized void release(UUID jobId) {
		if (jobId.equals(activeJobId)) {
			activeJobId = null;
		}
	}

	private record SyncJob(
			UUID jobId,
			ResourceDemandSyncJobStatus status,
			LocalDateTime startedAt,
			LocalDateTime completedAt,
			TourismResourceDemandSyncResponse result,
			String errorMessage
	) {
		private static SyncJob running(UUID jobId) {
			return new SyncJob(
					jobId,
					ResourceDemandSyncJobStatus.RUNNING,
					LocalDateTime.now(),
					null,
					null,
					null
			);
		}

		private SyncJob complete(
				ResourceDemandSyncJobStatus completedStatus,
				TourismResourceDemandSyncResponse syncResult
		) {
			return new SyncJob(
					jobId,
					completedStatus,
					startedAt,
					LocalDateTime.now(),
					syncResult,
					null
			);
		}

		private SyncJob fail() {
			return new SyncJob(
					jobId,
					ResourceDemandSyncJobStatus.FAILED,
					startedAt,
					LocalDateTime.now(),
					null,
					FAILED_MESSAGE
			);
		}

		private TourismResourceDemandSyncJobResponse toResponse() {
			return new TourismResourceDemandSyncJobResponse(
					jobId,
					status,
					startedAt,
					completedAt,
					result,
					errorMessage
			);
		}
	}
}
