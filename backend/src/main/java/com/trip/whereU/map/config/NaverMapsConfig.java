package com.trip.whereU.map.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NaverMapsProperties.class)
public class NaverMapsConfig {
}
