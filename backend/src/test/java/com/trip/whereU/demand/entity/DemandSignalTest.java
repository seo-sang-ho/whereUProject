package com.trip.whereU.demand.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DemandSignalTest {

	@Test
	void normalizedScoreToSignal() {
		assertThat(DemandSignal.fromNormalizedScore(0.29)).isEqualTo(DemandSignal.SMOOTH);
		assertThat(DemandSignal.fromNormalizedScore(0.3)).isEqualTo(DemandSignal.NORMAL);
		assertThat(DemandSignal.fromNormalizedScore(0.69)).isEqualTo(DemandSignal.NORMAL);
		assertThat(DemandSignal.fromNormalizedScore(0.7)).isEqualTo(DemandSignal.CROWDED);
	}
}
