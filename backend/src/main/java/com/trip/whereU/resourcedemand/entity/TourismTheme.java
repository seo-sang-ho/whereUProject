package com.trip.whereU.resourcedemand.entity;

import java.util.Map;
import java.util.Optional;

public enum TourismTheme {
	NATURE("자연"),
	CULTURE_HISTORY("문화·역사"),
	ACTIVITY("액티비티"),
	FOOD("미식"),
	SHOPPING("쇼핑"),
	HEALING_STAY("힐링·숙박");

	private static final Map<String, TourismTheme> BY_INDICATOR_CODE = Map.ofEntries(
			Map.entry("1205", NATURE),
			Map.entry("1201", CULTURE_HISTORY),
			Map.entry("1203", CULTURE_HISTORY),
			Map.entry("1101", ACTIVITY),
			Map.entry("1104", ACTIVITY),
			Map.entry("1108", ACTIVITY),
			Map.entry("1202", ACTIVITY),
			Map.entry("1204", ACTIVITY),
			Map.entry("1103", FOOD),
			Map.entry("1106", FOOD),
			Map.entry("1111", FOOD),
			Map.entry("1105", SHOPPING),
			Map.entry("1112", SHOPPING),
			Map.entry("1102", HEALING_STAY),
			Map.entry("1107", HEALING_STAY),
			Map.entry("1110", HEALING_STAY)
	);

	private final String label;

	TourismTheme(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public static Optional<TourismTheme> fromIndicatorCode(String indicatorCode) {
		return Optional.ofNullable(BY_INDICATOR_CODE.get(indicatorCode));
	}
}
