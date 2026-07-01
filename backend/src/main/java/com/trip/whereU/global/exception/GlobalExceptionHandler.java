package com.trip.whereU.global.exception;

import com.trip.whereU.global.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(IllegalStateException exception) {
		return ResponseEntity
				.badRequest()
				.body(ApiResponse.failure(exception.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
		return ResponseEntity
				.badRequest()
				.body(ApiResponse.failure(exception.getMessage()));
	}

	@ExceptionHandler(RestClientException.class)
	public ResponseEntity<ApiResponse<Void>> handleRestClientException(RestClientException exception) {
		if (exception instanceof RestClientResponseException responseException) {
			return ResponseEntity
					.status(HttpStatus.BAD_GATEWAY)
						.body(ApiResponse.failure(
								"외부 API 호출에 실패했습니다. status="
									+ responseException.getStatusCode()
									+ ", body="
									+ shorten(responseException.getResponseBodyAsString())
					));
		}
		return ResponseEntity
				.status(HttpStatus.BAD_GATEWAY)
				.body(ApiResponse.failure("외부 API 호출에 실패했습니다. " + exception.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException() {
		return ResponseEntity
				.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.failure("서버 내부 오류가 발생했습니다."));
	}

	private String shorten(String value) {
		if (value == null || value.isBlank()) {
			return "(empty)";
		}
		String normalized = value.replaceAll("\\s+", " ").trim();
		if (normalized.length() <= 300) {
			return normalized;
		}
		return normalized.substring(0, 300) + "...";
	}
}
