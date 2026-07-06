package com.trip.whereU.resourcedemand.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncJobResponse;
import com.trip.whereU.resourcedemand.dto.TourismResourceDemandSyncResponse;
import com.trip.whereU.resourcedemand.entity.ResourceDemandSyncJobStatus;
import com.trip.whereU.resourcedemand.entity.ResourceDemandType;
import com.trip.whereU.resourcedemand.exception.ResourceDemandSyncAlreadyRunningException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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
		when(syncService.sync(eq(false), any())).thenReturn(syncResponse);
		TourismResourceDemandSyncJobService jobService = jobService(Runnable::run);

		TourismResourceDemandSyncJobResponse job = jobService.start(null, null, false);

		assertThat(job.status()).isEqualTo(ResourceDemandSyncJobStatus.COMPLETED);
		assertThat(job.result()).isEqualTo(syncResponse);
		assertThat(job.processedIndicatorCount()).isEqualTo(17);
		assertThat(job.totalIndicatorCount()).isEqualTo(17);
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
		when(syncService.sync(eq(false), any())).thenReturn(response(List.of("SERVICE:1101")));
		TourismResourceDemandSyncJobService jobService = jobService(Runnable::run);

		TourismResourceDemandSyncJobResponse job = jobService.start(null, null, false);

		assertThat(job.status()).isEqualTo(ResourceDemandSyncJobStatus.PARTIAL_FAILED);
		assertThat(job.result().failedIndicators()).containsExactly("SERVICE:1101");
	}

	@Test
	void runsOnlyRequestedIndicator() {
		TourismResourceDemandSyncResponse syncResponse = response(List.of());
		when(syncService.sync(
				eq(ResourceDemandType.CULTURE), eq("1205"), eq(true), any()
		)).thenReturn(syncResponse);
		TourismResourceDemandSyncJobService jobService = jobService(Runnable::run);

		TourismResourceDemandSyncJobResponse job = jobService.start(
				ResourceDemandType.CULTURE, "1205", true
		);

		verify(syncService).validateIndicator(ResourceDemandType.CULTURE, "1205");
		verify(syncService).sync(
				eq(ResourceDemandType.CULTURE), eq("1205"), eq(true), any()
		);
		assertThat(job.status()).isEqualTo(ResourceDemandSyncJobStatus.COMPLETED);
		assertThat(job.totalIndicatorCount()).isEqualTo(1);
	}

	@Test
	void exposesProgressWhileJobIsRunning() throws Exception {
		CountDownLatch progressReported = new CountDownLatch(1);
		CountDownLatch allowCompletion = new CountDownLatch(1);
		when(syncService.sync(eq(false), any())).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			BiConsumer<Integer, Integer> progress = invocation.getArgument(1);
			progress.accept(6, 17);
			progressReported.countDown();
			if (!allowCompletion.await(2, TimeUnit.SECONDS)) {
				throw new IllegalStateException("테스트 작업 완료 대기 시간이 초과됐습니다.");
			}
			return response(List.of());
		});
		AtomicReference<Runnable> pendingTask = new AtomicReference<>();
		TourismResourceDemandSyncJobService jobService = jobService(pendingTask::set);
		TourismResourceDemandSyncJobResponse started = jobService.start(null, null, false);
		Thread worker = Thread.startVirtualThread(pendingTask.get());
		assertThat(progressReported.await(2, TimeUnit.SECONDS)).isTrue();

		TourismResourceDemandSyncJobResponse running = jobService.get(started.jobId());
		assertThat(running.status()).isEqualTo(ResourceDemandSyncJobStatus.RUNNING);
		assertThat(running.processedIndicatorCount()).isEqualTo(6);
		assertThat(running.totalIndicatorCount()).isEqualTo(17);

		allowCompletion.countDown();
		worker.join();
		assertThat(jobService.get(started.jobId()).processedIndicatorCount()).isEqualTo(17);
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
				"PARALLEL_FILTERED",
				289,
				failedIndicators
		);
	}
}
