package com.trip.whereU.resourcedemand.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
		name = "tourism_resource_demand_sync_state",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_resource_sync_state_type_indicator_date",
				columnNames = {"resource_type", "indicator_code", "reference_date"}
		)
)
public class TourismResourceDemandSyncState {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 20)
	private ResourceDemandType resourceType;

	@Column(name = "indicator_code", nullable = false, length = 20)
	private String indicatorCode;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "collected_count", nullable = false)
	private int collectedCount;

	@Column(name = "completed_at", nullable = false)
	private LocalDateTime completedAt;

	protected TourismResourceDemandSyncState() {
	}

	public TourismResourceDemandSyncState(
			ResourceDemandType resourceType,
			String indicatorCode,
			LocalDate referenceDate,
			int collectedCount
	) {
		this.resourceType = resourceType;
		this.indicatorCode = indicatorCode;
		this.referenceDate = referenceDate;
		this.collectedCount = collectedCount;
		this.completedAt = LocalDateTime.now();
	}

	public void complete(int collectedCount) {
		this.collectedCount = collectedCount;
		this.completedAt = LocalDateTime.now();
	}
}
