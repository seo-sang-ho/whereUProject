# NAVER Directions 출발 시각 파싱 수정 설계

## 목적

NAVER Directions 5가 반환하는 시간대 없는 `departureTime`을 한국 시간으로 해석하여, 자동차 예상 시간 카드가 `NAVER_API_UNAVAILABLE` fallback으로 잘못 전환되지 않게 한다.

## 원인

- 실제 NAVER 응답: `2026-07-21T10:03:10`
- 기존 테스트 응답: `2026-07-20T15:00:00+09:00`
- 기존 구현은 `OffsetDateTime.parse`를 직접 사용하므로 실제 응답에서 `DateTimeParseException`이 발생한다.
- 이 예외가 `DirectionsService`에서 외부 API 장애로 처리되어 예상 시간이 표시되지 않는다.

## 설계

`NaverDirectionsClient`에서 `departureTime`을 `LocalDateTime`으로 파싱한 뒤 `Asia/Seoul`을 적용하여 `OffsetDateTime`으로 변환한다. 기존 `NaverDirectionsResult`, `DirectionsEstimateResponse`, 프런트엔드 타입과 API 응답 계약은 유지한다.

## 데이터 흐름

1. NAVER가 시간대 없는 `departureTime` 문자열을 반환한다.
2. Client가 문자열을 `LocalDateTime`으로 해석한다.
3. `Asia/Seoul` 시간대를 적용한다.
4. 기존 DTO에 `OffsetDateTime`을 전달한다.
5. API 응답에는 `+09:00`이 포함된 시각이 반환된다.

## API·DB·성능 영향

- API: `GET /api/directions/availability`, `POST /api/directions/estimate` 계약 변경 없음.
- DB: 스키마와 저장 데이터 변경 없음.
- 개인정보: 사용자 출발 좌표의 저장·로그 정책 변경 없음.
- 성능: 경로 응답당 날짜 파싱과 시간대 적용 한 번만 추가되어 영향은 무시할 수 있다.

## 오류 처리

형식이 없거나 해석할 수 없는 `departureTime`은 기존과 동일하게 안전한 `NAVER_API_UNAVAILABLE` fallback으로 처리한다. 원본 응답, 좌표, Secret은 오류 메시지나 로그에 노출하지 않는다.

## 테스트

- 실제 형식인 `2026-07-21T10:03:10`을 사용한 Client 회귀 테스트를 먼저 작성하고 기존 코드에서 실패함을 확인한다.
- 수정 후 결과가 `2026-07-21T10:03:10+09:00`인지 확인한다.
- Client focused test와 백엔드 전체 테스트를 실행한다.
- 실행 중인 백엔드를 재시작한 뒤 실제 `/api/directions/estimate`가 `AVAILABLE`과 시간·거리·통행료를 반환하는지 확인한다.
