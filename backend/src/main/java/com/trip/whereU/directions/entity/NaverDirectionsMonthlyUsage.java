package com.trip.whereU.directions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "naver_directions_monthly_usage")
public class NaverDirectionsMonthlyUsage {

	@Id
	@Column(name = "usage_month", nullable = false, length = 7)
	private String usageMonth;

	@Column(name = "reserved_count", nullable = false)
	private long reservedCount;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected NaverDirectionsMonthlyUsage() {
	}

	public NaverDirectionsMonthlyUsage(String usageMonth, long reservedCount) {
		this.usageMonth = usageMonth;
		this.reservedCount = reservedCount;
		this.createdAt = LocalDateTime.now();
		this.updatedAt = createdAt;
	}

	public boolean reserve(long safeLimit) {
		if (reservedCount >= safeLimit) {
			return false;
		}
		reservedCount++;
		updatedAt = LocalDateTime.now();
		return true;
	}

	public long getReservedCount() {
		return reservedCount;
	}
}
