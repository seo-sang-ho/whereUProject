package com.trip.whereU.resourcedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncJobResponse;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncResponse;
import com.trip.whereU.resourcedemand.entity.ResourceDemandSyncJobStatus;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.exception.ResourceDemandSyncAlreadyRunningException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TourismResourceDemandSyncJobServiceTest {

	@Mock
	private TourismResourceDemandService syncService;

	@Test
	void completesFullSyncInBackgroundExecutor() {
		TourismResourceDemandSyncResponse syncResponse = response(List.of());
		when(syncService.sync(false)).thenReturn(syncResponse);
		TourismResourceDemandSyncJobService jobService = jobService(Runnable::run);

		TourismResourceDemandSyncJobResponse job = jobService.start(null, null, false);

		assertThat(job.status()).isEqualTo(ResourceDemandSyncJobStatus.COMPLETED);
		assertThat(job.result()).isEqualTo(syncResponse);
		assertThat(job.completedAt()).isNotNull();
	}

	@Test
	void rejectsSecondJobWhileFirstJobIsRunning() {
		AtomicReference<Runnable> pendingTask = new AtomicReference<>();
		TourismResourceDemandSyncJobService jobService = jobService(pendingTask::set);

		TourismResourceDemandSyncJobResponse runningJob = jobService.start(null, null, false);

		assertThat(runningJob.status()).isEqualTo(ResourceDemandSyncJobStatus.RUNNING);
		assertThatThrownBy(() -> jobService.start(null, null, false))
				.isInstanceOf(ResourceDemandSyncAlreadyRunningException.class);
	}

	@Test
	void marksJobAsPartialFailureWhenIndicatorFailed() {
		when(syncService.sync(false)).thenReturn(response(List.of("SERVICE:1101")));
		TourismResourceDemandSyncJobService jobService = jobService(Runnable::run);

		TourismResourceDemandSyncJobResponse job = jobService.start(null, null, false);

		assertThat(job.status()).isEqualTo(ResourceDemandSyncJobStatus.PARTIAL_FAILED);
		assertThat(job.result().failedIndicators()).containsExactly("SERVICE:1101");
	}

	@Test
	void runsOnlyRequestedIndicator() {
		TourismResourceDemandSyncResponse syncResponse = response(List.of());
		when(syncService.sync(ResourceDemandType.CULTURE, "1205", true)).thenReturn(syncResponse);
		TourismResourceDemandSyncJobService jobService = jobService(Runnable::run);

		TourismResourceDemandSyncJobResponse job = jobService.start(
				ResourceDemandType.CULTURE, "1205", true
		);

		verify(syncService).validateIndicator(ResourceDemandType.CULTURE, "1205");
		verify(syncService).sync(ResourceDemandType.CULTURE, "1205", true);
		assertThat(job.status()).isEqualTo(ResourceDemandSyncJobStatus.COMPLETED);
	}

	private TourismResourceDemandSyncJobService jobService(Executor executor) {
		return new TourismResourceDemandSyncJobService(syncService, executor);
	}

	private TourismResourceDemandSyncResponse response(List<String> failedIndicators) {
		return new TourismResourceDemandSyncResponse(
				3024,
				1260,
				4284,
				LocalDate.of(2025, 9, 1),
				17 - failedIndicators.size(),
				0,
				List.of(),
				List.of("SERVICE", "CULTURE"),
				List.of(),
				failedIndicators
		);
	}
}
