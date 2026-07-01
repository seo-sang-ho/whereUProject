package com.trip.whereU.map.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(
		name = "tourism_region",
		indexes = {
				@Index(
						name = "idx_tourism_region_latitude_longitude",
						columnList = "latitude, longitude"
				)
		}
)
public class TourismRegion {

	@Id
	@Column(name = "region_code", length = 30)
	private String regionCode;

	@Column(name = "region_name", nullable = false, length = 100)
	private String regionName;

	@Column(name = "latitude", nullable = false)
	private double latitude;

	@Column(name = "longitude", nullable = false)
	private double longitude;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TourismRegion() {
	}

	public TourismRegion(String regionCode, String regionName, double latitude, double longitude) {
		this.regionCode = regionCode;
		update(regionName, latitude, longitude);
	}

	public void update(String regionName, double latitude, double longitude) {
		this.regionName = regionName;
		this.latitude = latitude;
		this.longitude = longitude;
		this.updatedAt = LocalDateTime.now();
	}

	public String getRegionCode() {
		return regionCode;
	}

	public String getRegionName() {
		return regionName;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
