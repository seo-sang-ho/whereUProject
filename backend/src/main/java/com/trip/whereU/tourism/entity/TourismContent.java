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
		name = "tourism_content",
		indexes = {
				@Index(
						name = "idx_tourism_content_latitude_longitude",
						columnList = "latitude, longitude"
				),
				@Index(
						name = "idx_tourism_content_area_sigungu",
						columnList = "area_code, sigungu_code"
				),
				@Index(
						name = "idx_tourism_content_legal_dong",
						columnList = "legal_dong_code"
				),
				@Index(
						name = "idx_tourism_content_category",
						columnList = "category_code"
				),
				@Index(
						name = "idx_tourism_content_type",
						columnList = "content_type_id"
				)
		},
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_tourism_content_content_id",
						columnNames = "content_id"
				)
		}
)
public class TourismContent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "content_id", nullable = false, length = 30)
	private String contentId;

	@Column(name = "content_type_id", length = 20)
	private String contentTypeId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "address", length = 300)
	private String address;

	@Column(name = "detail_address", length = 300)
	private String detailAddress;

	@Column(name = "latitude")
	private Double latitude;

	@Column(name = "longitude")
	private Double longitude;

	@Column(name = "area_code", length = 30)
	private String areaCode;

	@Column(name = "sigungu_code", length = 30)
	private String sigunguCode;

	@Column(name = "legal_dong_code", length = 50)
	private String legalDongCode;

	@Column(name = "category_code", length = 50)
	private String categoryCode;

	@Column(name = "cat1", length = 20)
	private String cat1;

	@Column(name = "cat2", length = 20)
	private String cat2;

	@Column(name = "cat3", length = 20)
	private String cat3;

	@Column(name = "first_image", length = 1000)
	private String firstImage;

	@Column(name = "first_image2", length = 1000)
	private String firstImage2;

	@Column(name = "tel", length = 100)
	private String tel;

	@Column(name = "zipcode", length = 20)
	private String zipcode;

	@Column(name = "modified_time", length = 30)
	private String modifiedTime;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected TourismContent() {
	}

	public TourismContent(
			String contentId,
			String contentTypeId,
			String title,
			String address,
			String detailAddress,
			Double latitude,
			Double longitude,
			String areaCode,
			String sigunguCode,
			String legalDongCode,
			String categoryCode,
			String cat1,
			String cat2,
			String cat3,
			String firstImage,
			String firstImage2,
			String tel,
			String zipcode,
			String modifiedTime
	) {
		this.contentId = contentId;
		this.createdAt = LocalDateTime.now();
		update(
				contentTypeId,
				title,
				address,
				detailAddress,
				latitude,
				longitude,
				areaCode,
				sigunguCode,
				legalDongCode,
				categoryCode,
				cat1,
				cat2,
				cat3,
				firstImage,
				firstImage2,
				tel,
				zipcode,
				modifiedTime
		);
	}

	public void update(
			String contentTypeId,
			String title,
			String address,
			String detailAddress,
			Double latitude,
			Double longitude,
			String areaCode,
			String sigunguCode,
			String legalDongCode,
			String categoryCode,
			String cat1,
			String cat2,
			String cat3,
			String firstImage,
			String firstImage2,
			String tel,
			String zipcode,
			String modifiedTime
	) {
		this.contentTypeId = contentTypeId;
		this.title = title;
		this.address = address;
		this.detailAddress = detailAddress;
		this.latitude = latitude;
		this.longitude = longitude;
		this.areaCode = areaCode;
		this.sigunguCode = sigunguCode;
		this.legalDongCode = legalDongCode;
		this.categoryCode = categoryCode;
		this.cat1 = cat1;
		this.cat2 = cat2;
		this.cat3 = cat3;
		this.firstImage = firstImage;
		this.firstImage2 = firstImage2;
		this.tel = tel;
		this.zipcode = zipcode;
		this.modifiedTime = modifiedTime;
		this.updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getContentId() {
		return contentId;
	}

	public String getContentTypeId() {
		return contentTypeId;
	}

	public String getTitle() {
		return title;
	}

	public String getAddress() {
		return address;
	}

	public String getDetailAddress() {
		return detailAddress;
	}

	public Double getLatitude() {
		return latitude;
	}

	public Double getLongitude() {
		return longitude;
	}

	public String getAreaCode() {
		return areaCode;
	}

	public String getSigunguCode() {
		return sigunguCode;
	}

	public String getLegalDongCode() {
		return legalDongCode;
	}

	public String getCategoryCode() {
		return categoryCode;
	}

	public String getCat1() {
		return cat1;
	}

	public String getCat2() {
		return cat2;
	}

	public String getCat3() {
		return cat3;
	}

	public String getFirstImage() {
		return firstImage;
	}

	public String getFirstImage2() {
		return firstImage2;
	}

	public String getTel() {
		return tel;
	}

	public String getZipcode() {
		return zipcode;
	}

	public String getModifiedTime() {
		return modifiedTime;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
