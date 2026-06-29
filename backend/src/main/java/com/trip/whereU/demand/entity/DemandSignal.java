package com.trip.whereU.demand.entity;

public enum DemandSignal {
	SMOOTH("원활", "비교적 여유로운 상태"),
	NORMAL("보통", "일반적인 방문 수준"),
	CROWDED("혼잡", "방문 시간 조정 권장");

	private final String label;
	private final String description;

	DemandSignal(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String getLabel() {
		return label;
	}

	public String getDescription() {
		return description;
	}

	public static DemandSignal fromNormalizedScore(double normalizedScore) {
		if (normalizedScore < 0.3) {
			return SMOOTH;
		}
		if (normalizedScore < 0.7) {
			return NORMAL;
		}
		return CROWDED;
	}
}
