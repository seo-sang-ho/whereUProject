package com.trip.whereU.resourcedemand.entity;

public enum ResourceDemandType {
	SERVICE("관광 서비스"),
	CULTURE("문화 자원");

	private final String label;

	ResourceDemandType(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
