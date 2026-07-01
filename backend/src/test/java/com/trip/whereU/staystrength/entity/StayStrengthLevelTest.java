package com.trip.whereU.staystrength.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StayStrengthLevelTest {

	@Test
	void normalizedScoreToLevel() {
		assertThat(StayStrengthLevel.fromNormalizedScore(0.29)).isEqualTo(StayStrengthLevel.LOW);
		assertThat(StayStrengthLevel.fromNormalizedScore(0.3)).isEqualTo(StayStrengthLevel.MEDIUM);
		assertThat(StayStrengthLevel.fromNormalizedScore(0.69)).isEqualTo(StayStrengthLevel.MEDIUM);
		assertThat(StayStrengthLevel.fromNormalizedScore(0.7)).isEqualTo(StayStrengthLevel.HIGH);
	}

	@Test
	void convertsLegacyDatabaseValuesToAccurateLevels() {
		StayStrengthLevelConverter converter = new StayStrengthLevelConverter();

		assertThat(converter.convertToEntityAttribute("SMOOTH")).isEqualTo(StayStrengthLevel.LOW);
		assertThat(converter.convertToEntityAttribute("NORMAL")).isEqualTo(StayStrengthLevel.MEDIUM);
		assertThat(converter.convertToEntityAttribute("CROWDED")).isEqualTo(StayStrengthLevel.HIGH);
		assertThat(converter.convertToDatabaseColumn(StayStrengthLevel.HIGH)).isEqualTo("CROWDED");
	}
}
