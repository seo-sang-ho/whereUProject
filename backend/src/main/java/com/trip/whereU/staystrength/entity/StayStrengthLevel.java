package com.trip.whereU.staystrength.entity;

public enum StayStrengthLevel {
	LOW("낮음", "다른 지역보다 체류 성향이 낮은 편"),
	MEDIUM("보통", "전국 시군구 중 중간 수준의 체류 성향"),
	HIGH("높음", "다른 지역보다 체류 성향이 높은 편");

	private final String label;
	private final String description;

	StayStrengthLevel(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

	public static StayStrengthLevel fromNormalizedScore(double normalizedScore) {
		if (normalizedScore < 0.3) {
			return LOW;
		}
		if (normalizedScore < 0.7) {
			return MEDIUM;
		}
		return HIGH;
	}
}
