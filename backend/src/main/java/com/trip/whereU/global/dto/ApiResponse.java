package com.trip.whereU.global.dto;

public record ApiResponse<T>(
		boolean success,
		T data,
		String message
) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static <T> ApiResponse<T> failure(String message) {
		return new ApiResponse<>(false, null, message);
	}
}
