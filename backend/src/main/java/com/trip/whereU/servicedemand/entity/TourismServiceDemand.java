package com.trip.whereU.servicedemand.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
		name = "tourism_service_demand",
		indexes = {
				@Index(
						name = "idx_service_demand_reference_date",
						columnList = "reference_date"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_service_demand_region_reference_date",
						columnNames = {"region_code", "reference_date"}
				)
		}
)
public class TourismServiceDemand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "region_code", nullable = false, length = 30)
	private String regionCode;

	@Column(name = "region_name", nullable = false, length = 100)
	private String regionName;

	@Column(name = "raw_service_demand", nullable = false)
	private double rawServiceDemand;

	@Column(name = "normalized_service_demand", nullable = false)
	private double normalizedServiceDemand;

	@Enumerated(EnumType.STRING)
	@Column(name = "service_demand_level", nullable = false, length = 20)
	private ServiceDemandLevel level;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TourismServiceDemand() {
	}

	public TourismServiceDemand(
			String regionCode,
			String regionName,
			double rawServiceDemand,
			double normalizedServiceDemand,
			LocalDate referenceDate
	) {
		this.regionCode = regionCode;
		this.regionName = regionName;
		update(rawServiceDemand, normalizedServiceDemand, referenceDate);
	}

	public void update(
			double rawServiceDemand,
			double normalizedServiceDemand,
			LocalDate referenceDate
	) {
		this.rawServiceDemand = rawServiceDemand;
		this.normalizedServiceDemand = normalizedServiceDemand;
		this.level = ServiceDemandLevel.fromNormalizedScore(normalizedServiceDemand);
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

	public double getRawServiceDemand() {
		return rawServiceDemand;
	}

	public double getNormalizedServiceDemand() {
		return normalizedServiceDemand;
	}

	public ServiceDemandLevel getLevel() {
		return level;
	}

	public LocalDate getReferenceDate() {
		return referenceDate;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
