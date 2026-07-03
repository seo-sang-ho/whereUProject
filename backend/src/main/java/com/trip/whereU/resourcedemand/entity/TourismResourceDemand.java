package com.trip.whereU.resourcedemand.entity;

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
		name = "tourism_resource_demand",
		indexes = {
				@Index(
						name = "idx_resource_demand_reference_indicator_region",
						columnList = "reference_date, indicator_code, region_code"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_resource_demand_region_type_indicator_date",
						columnNames = {"region_code", "resource_type", "indicator_code", "reference_date"}
				)
		}
)
public class TourismResourceDemand {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "region_code", nullable = false, length = 30)
	private String regionCode;

	@Column(name = "region_name", nullable = false, length = 100)
	private String regionName;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 20)
	private ResourceDemandType resourceType;

	@Column(name = "indicator_code", nullable = false, length = 20)
	private String indicatorCode;

	@Column(name = "indicator_name", nullable = false, length = 150)
	private String indicatorName;

	@Enumerated(EnumType.STRING)
	@Column(name = "theme", length = 30)
	private TourismTheme theme;

	@Column(name = "raw_value", nullable = false)
	private double rawValue;

	@Column(name = "normalized_value", nullable = false)
	private double normalizedValue;

	@Column(name = "reference_date", nullable = false)
	private LocalDate referenceDate;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TourismResourceDemand() {
	}

	public TourismResourceDemand(
			String regionCode,
			String regionName,
			ResourceDemandType resourceType,
			String indicatorCode,
			String indicatorName,
			double rawValue,
			double normalizedValue,
			LocalDate referenceDate
	) {
		this.regionCode = regionCode;
		this.resourceType = resourceType;
		this.indicatorCode = indicatorCode;
		update(regionName, indicatorName, rawValue, normalizedValue, referenceDate);
	}

	public void update(
			String regionName,
			String indicatorName,
			double rawValue,
			double normalizedValue,
			LocalDate referenceDate
	) {
		this.regionName = regionName;
		this.indicatorName = indicatorName;
		this.theme = TourismTheme.fromIndicatorCode(indicatorCode).orElse(null);
		this.rawValue = rawValue;
		this.normalizedValue = normalizedValue;
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

	public ResourceDemandType getResourceType() {
		return resourceType;
	}

	public String getIndicatorCode() {
		return indicatorCode;
	}

	public String getIndicatorName() {
		return indicatorName;
	}

	public TourismTheme getTheme() {
		return theme;
	}

	public double getRawValue() {
		return rawValue;
	}

	public double getNormalizedValue() {
		return normalizedValue;
	}

	public LocalDate getReferenceDate() {
		return referenceDate;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
