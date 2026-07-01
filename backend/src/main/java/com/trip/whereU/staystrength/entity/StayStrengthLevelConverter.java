package com.trip.whereU.staystrength.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class StayStrengthLevelConverter implements AttributeConverter<StayStrengthLevel, String> {

	@Override
	public String convertToDatabaseColumn(StayStrengthLevel level) {
		if (level == null) {
			return null;
		}
		return switch (level) {
			case LOW -> "SMOOTH";
			case MEDIUM -> "NORMAL";
			case HIGH -> "CROWDED";
		};
	}

	@Override
	public StayStrengthLevel convertToEntityAttribute(String value) {
		if (value == null) {
			return null;
		}
		return switch (value) {
			case "LOW", "SMOOTH" -> StayStrengthLevel.LOW;
			case "MEDIUM", "NORMAL" -> StayStrengthLevel.MEDIUM;
			case "HIGH", "CROWDED" -> StayStrengthLevel.HIGH;
			default -> throw new IllegalArgumentException("지원하지 않는 체류강도 단계입니다: " + value);
		};
	}
}
