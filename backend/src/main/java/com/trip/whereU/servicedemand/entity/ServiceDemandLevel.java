package com.trip.whereU.servicedemand.entity;

public enum ServiceDemandLevel {
	LOW("낮음", "다른 지역보다 관광 서비스 수요가 낮은 편"),
	MEDIUM("보통", "전국 시군구 중 중간 수준의 관광 서비스 수요"),
	HIGH("높음", "다른 지역보다 관광 서비스 수요가 높은 편");

	private final String label;
	private final String description;

	ServiceDemandLevel(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

	public static ServiceDemandLevel fromNormalizedScore(double normalizedScore) {
		if (normalizedScore < 0.3) {
			return LOW;
		}
		if (normalizedScore < 0.7) {
			return MEDIUM;
		}
		return HIGH;
	}
}
