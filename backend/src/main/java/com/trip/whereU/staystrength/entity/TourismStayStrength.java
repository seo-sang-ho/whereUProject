package com.trip.whereU.staystrength.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
		name = "tourism_demand",
		indexes = {
				@Index(
						name = "idx_tourism_demand_reference_date",
						columnList = "reference_date"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_tourism_demand_region_reference_date",
						columnNames = {"region_code", "reference_date"}
				)
		}
)
public class TourismStayStrength {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "region_code", nullable = false, length = 30)
	private String regionCode;

	@Column(name = "region_name", nullable = false, length = 100)
	private String regionName;

	@Column(name = "raw_demand_score", nullable = false)
	private double rawStayStrength;

	@Column(name = "normalized_demand_score", nullable = false)
	private double normalizedStayStrength;

	@Convert(converter = StayStrengthLevelConverter.class)
	@Column(name = "demand_signal", nullable = false, length = 20)
	private StayStrengthLevel level;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TourismStayStrength() {
	}

	public TourismStayStrength(
			String regionCode,
			String regionName,
			double rawStayStrength,
			double normalizedStayStrength,
			LocalDate referenceDate
	) {
		this.regionCode = regionCode;
		this.regionName = regionName;
		updateStayStrength(rawStayStrength, normalizedStayStrength, referenceDate);
	}

	public void updateStayStrength(
			double rawStayStrength,
			double normalizedStayStrength,
			LocalDate referenceDate
	) {
		this.rawStayStrength = rawStayStrength;
		this.normalizedStayStrength = normalizedStayStrength;
		this.level = StayStrengthLevel.fromNormalizedScore(normalizedStayStrength);
		this.referenceDate = referenceDate;
		this.updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getRegionCode() {
		return regionCode;
	}

	public String getRegionName() {
		return regionName;
	}

	public double getRawStayStrength() {
		return rawStayStrength;
	}

	public double getNormalizedStayStrength() {
		return normalizedStayStrength;
	}

	public StayStrengthLevel getLevel() {
		return level;
	}

	public LocalDate getReferenceDate() {
		return referenceDate;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
