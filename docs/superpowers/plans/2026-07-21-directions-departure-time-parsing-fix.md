# NAVER Directions 출발 시각 파싱 수정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 시간대가 없는 NAVER `departureTime`을 한국 시간으로 변환하여 자동차 예상 시간을 정상 반환한다.

**Architecture:** `NaverDirectionsClient`의 응답 변환 경계에서 `LocalDateTime`에 `Asia/Seoul`을 적용해 기존 `OffsetDateTime` DTO 계약을 유지한다. API, DB, Frontend는 변경하지 않는다.

**Tech Stack:** Java 21+, Spring Boot, JUnit 5, AssertJ

## Global Constraints

- 실제 Secret, API Key, 사용자 출발 좌표를 코드·테스트·로그에 기록하지 않는다.
- 기존 `/api/directions/estimate` 요청·응답 계약과 fallback 정책을 유지한다.
- `.DS_Store`를 스테이징하거나 커밋하지 않는다.

---

### Task 1: 실제 NAVER 출발 시각을 한국 시간으로 변환

**Files:**
- Modify: `backend/src/test/java/com/trip/whereU/directions/client/NaverDirectionsClientTest.java`
- Modify: `backend/src/main/java/com/trip/whereU/directions/client/NaverDirectionsClient.java`

**Interfaces:**
- Consumes: NAVER 응답 필드 `departureTime` (`yyyy-MM-dd'T'HH:mm:ss`)
- Produces: `NaverDirectionsResult.calculatedAt()` (`OffsetDateTime`, `+09:00`)

- [ ] **Step 1: 실제 응답 형식으로 실패 테스트 작성**

테스트 JSON의 `departureTime`을 `2026-07-21T10:03:10`으로 구성하고 결과가 `OffsetDateTime.parse("2026-07-21T10:03:10+09:00")`인지 검증한다.

- [ ] **Step 2: RED 확인**

Run: `./gradlew test --tests com.trip.whereU.directions.client.NaverDirectionsClientTest.parsesOptimalSummaryAndRoundsDurationUpToNearestMinute`

Expected: `DateTimeParseException`을 감싼 `IllegalStateException`으로 FAIL.

- [ ] **Step 3: 최소 구현**

```java
private static final ZoneId NAVER_DIRECTIONS_ZONE = ZoneId.of("Asia/Seoul");

private OffsetDateTime parseDepartureTime(String departureTime) {
    return LocalDateTime.parse(departureTime)
            .atZone(NAVER_DIRECTIONS_ZONE)
            .toOffsetDateTime();
}
```

`parseResult`에서 기존 `OffsetDateTime.parse(...)` 대신 위 메서드를 사용한다.

- [ ] **Step 4: GREEN 및 전체 회귀 검증**

Run: `./gradlew test --tests com.trip.whereU.directions.client.NaverDirectionsClientTest`

Expected: Client 테스트 전체 PASS.

Run: `./gradlew test --rerun-tasks --no-daemon`

Expected: Backend 테스트 전체 PASS.

- [ ] **Step 5: 실제 흐름 검증**

새 코드로 실행한 백엔드에서 실제 NAVER 형식 응답을 사용했을 때 `AVAILABLE`, 시간, 거리, 통행료, `+09:00` 시각이 반환되는지 확인한다. Secret과 좌표는 결과에 기록하지 않는다.

- [ ] **Step 6: 변경 범위와 보안 확인 후 커밋**

```bash
git status --short
git diff --check
git diff -- backend/src/main/java/com/trip/whereU/directions/client/NaverDirectionsClient.java backend/src/test/java/com/trip/whereU/directions/client/NaverDirectionsClientTest.java
git add backend/src/main/java/com/trip/whereU/directions/client/NaverDirectionsClient.java backend/src/test/java/com/trip/whereU/directions/client/NaverDirectionsClientTest.java
git commit -m "fix: 실제 네이버 출발 시각을 정상 처리"
```
