package com.trip.whereU.tourism.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
		name = "tourism_region_tour_api_mapping",
		indexes = {
				@Index(
						name = "idx_tourism_region_tour_api_mapping_region_code",
						columnList = "region_code"
				),
				@Index(
						name = "idx_tourism_region_tour_api_mapping_legal_dong",
						columnList = "legal_dong_code"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_tourism_region_tour_api_mapping_region_code",
						columnNames = "region_code"
				)
		}
)
public class TourismRegionTourApiMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "region_code", nullable = false, length = 30)
	private String regionCode;

	@Column(name = "region_name", length = 100)
	private String regionName;

	@Column(name = "legal_dong_code", nullable = false, length = 50)
	private String legalDongCode;

	@Column(name = "default_content_type_id", length = 20)
	private String defaultContentTypeId;

	@Column(name = "default_category_code", length = 50)
	private String defaultCategoryCode;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TourismRegionTourApiMapping() {
	}

	public TourismRegionTourApiMapping(
			String regionCode,
			String regionName,
			String legalDongCode,
			String defaultContentTypeId,
			String defaultCategoryCode,
			boolean enabled
	) {
		this.regionCode = regionCode;
		this.regionName = regionName;
		this.legalDongCode = legalDongCode;
		this.defaultContentTypeId = defaultContentTypeId;
		this.defaultCategoryCode = defaultCategoryCode;
		this.enabled = enabled;
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

	public String getLegalDongCode() {
		return legalDongCode;
	}

	public String getDefaultContentTypeId() {
		return defaultContentTypeId;
	}

	public String getDefaultCategoryCode() {
		return defaultCategoryCode;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
