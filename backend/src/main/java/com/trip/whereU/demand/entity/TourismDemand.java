package com.trip.whereU.demand.entity;

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
		name = "tourism_demand",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_tourism_demand_region_reference_date",
						columnNames = {"region_code", "reference_date"}
				)
		}
)
public class TourismDemand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "region_code", nullable = false, length = 30)
	private String regionCode;

	@Column(name = "region_name", nullable = false, length = 100)
	private String regionName;

	@Column(name = "raw_demand_score", nullable = false)
	private double rawDemandScore;

	@Column(name = "normalized_demand_score", nullable = false)
	private double normalizedDemandScore;

	@Enumerated(EnumType.STRING)
	@Column(name = "demand_signal", nullable = false, length = 20)
	private DemandSignal demandSignal;

	@Column(name = "latitude")
	private Double latitude;

	@Column(name = "longitude")
	private Double longitude;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TourismDemand() {
	}

	public TourismDemand(
			String regionCode,
			String regionName,
			double rawDemandScore,
			double normalizedDemandScore,
			Double latitude,
			Double longitude,
			LocalDate referenceDate
	) {
		this.regionCode = regionCode;
		this.regionName = regionName;
		updateDemand(rawDemandScore, normalizedDemandScore, latitude, longitude, referenceDate);
	}

	public void updateDemand(
			double rawDemandScore,
			double normalizedDemandScore,
			Double latitude,
			Double longitude,
			LocalDate referenceDate
	) {
		this.rawDemandScore = rawDemandScore;
		this.normalizedDemandScore = normalizedDemandScore;
		this.demandSignal = DemandSignal.fromNormalizedScore(normalizedDemandScore);
		this.latitude = latitude;
		this.longitude = longitude;
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

	public double getRawDemandScore() {
		return rawDemandScore;
	}

	public double getNormalizedDemandScore() {
		return normalizedDemandScore;
	}

	public DemandSignal getDemandSignal() {
		return demandSignal;
	}

	public Double getLatitude() {
		return latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public LocalDate getReferenceDate() {
		return referenceDate;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
