package com.trip.whereU.resourcedemand.exception;

public class ResourceDemandSyncAlreadyRunningException extends RuntimeException {

	public ResourceDemandSyncAlreadyRunningException() {
		super("관광 자원 수요 동기화가 이미 진행 중입니다.");
	}
}
