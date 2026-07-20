package com.trip.whereU.directions.exception;

public class DirectionsDestinationNotFoundException extends RuntimeException {

	public DirectionsDestinationNotFoundException(String contentId) {
		super("관광지 콘텐츠를 찾을 수 없습니다. contentId=" + contentId);
	}
}
