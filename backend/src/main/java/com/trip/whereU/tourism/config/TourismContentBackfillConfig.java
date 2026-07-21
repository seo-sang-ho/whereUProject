package com.trip.whereU.tourism.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class TourismContentBackfillConfig {

	@Bean(name = "tourismContentBackfillExecutor")
	public Executor tourismContentBackfillExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("tourism-content-backfill-");
		executor.initialize();
		return executor;
	}
}
