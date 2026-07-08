package com.trip.whereU.tourism.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TourismContentApiProperties.class)
public class TourismContentConfig {
}
