package com.trip.whereU.resourcedemand.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TourismResourceDemandSyncConfig {

	@Bean(name = "resourceDemandSyncExecutor")
	public Executor resourceDemandSyncExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("resource-demand-sync-");
		executor.initialize();
		return executor;
	}

	@Bean(name = "resourceDemandFetchExecutor")
	public Executor resourceDemandFetchExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("resource-demand-fetch-");
		executor.initialize();
		return executor;
	}
}
