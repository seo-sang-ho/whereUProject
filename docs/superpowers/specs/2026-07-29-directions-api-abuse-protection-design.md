# 자동차 예상시간 API 남용 방지 설계

- 상태: 설계 승인 완료
- 작성일: 2026-07-29
- 대상: 로그인 없이 단일 Backend 인스턴스로 운영하는 whereU 자동차 예상시간 API

## 1. 기능 목적

로그인하지 않은 외부 사용자가 `POST /api/directions/estimate`를 반복 호출해 NAVER Directions 월 사용량을 조기에 소진하는 문제를 방지한다.

정상 사용자는 whereU 내부에서 자동차 예상시간을 계속 확인할 수 있어야 한다. 단기 요청 제한에 걸리거나 월 50,000건 안전 한도에 도달한 사용자는 서비스 오류 화면 대신 네이버 지도 길찾기로 자연스럽게 전환한다.

기존 관광 체류강도 API, 추천 점수 계산, 관광정보 수집 API는 변경하지 않는다.

## 2. 확정된 운영 조건

- 사용자는 당분간 로그인하지 않는다.
- Backend는 한 개 인스턴스만 실행한다.
- 비용이 발생하는 `POST /api/directions/estimate`만 단기 요청 제한 대상으로 삼는다.
- `GET /api/directions/availability`와 기존 관광 수요 API에는 단기 요청 제한을 적용하지 않는다.
- 기존 DB 기반 월 50,000건 안전 한도는 최종 차단 장치로 유지한다.
- 서버가 여러 대로 늘어나기 전까지 Redis를 도입하지 않는다.

## 3. 선택한 접근법

### 3.1 Bucket4j 기반 인메모리 요청 제한

IP마다 Bucket4j 토큰 버킷을 생성한다. Bucket4j는 토큰 소비와 보충을 원자적으로 처리하므로 직접 카운터를 구현할 때 발생할 수 있는 시간 경계와 동시성 오류를 줄인다.

버킷 저장에는 프로젝트가 이미 사용하는 Caffeine을 재사용한다. 서버 한 대에서만 동작하므로 분산 저장소는 필요하지 않다.

초기 정책은 다음과 같다.

| 정책 | 값 | 의미 |
| --- | ---: | --- |
| 분당 버킷 용량 | 10 | 한 IP가 순간적으로 사용할 수 있는 최대 토큰 |
| 분당 보충량 | 10 | 1분에 걸쳐 점진적으로 다시 사용할 수 있는 토큰 |
| 24시간 버킷 용량 | 100 | 한 IP가 순간적으로 보유할 수 있는 장기 토큰 |
| 24시간 보충량 | 100 | 24시간에 걸쳐 점진적으로 다시 사용할 수 있는 장기 토큰 |
| IP 버킷 최대 개수 | 10,000 | 메모리 사용량 상한 |
| 미사용 버킷 만료 | 24시간 | 마지막 접근 후 자동 제거 |

두 버킷을 모두 통과해야 요청을 허용한다. 정상 사용자는 추천 장소를 몇 번 확인할 수 있고, 자동화된 반복 요청은 빠르게 차단된다.

### 3.2 동일 요청 single-flight

같은 출발지 캐시 키와 목적지에 대한 요청이 동시에 들어오면 첫 요청만 NAVER Directions를 호출한다. 나머지 요청은 진행 중인 첫 요청의 결과를 함께 받는다.

single-flight는 요청 제한을 대체하지 않는다. 요청 제한은 악의적 반복 호출을 막고, single-flight는 정상 사용자의 중복 클릭이나 동시 요청이 월 사용량을 낭비하지 않게 한다.

### 3.3 월 50,000건 최종 차단

기존 `naver_directions_monthly_usage` 테이블과 비관적 잠금을 그대로 사용한다. 단기 제한과 single-flight를 통과한 캐시 미스 요청만 월 사용량을 예약한다.

## 4. 전체 데이터 흐름

```text
POST /api/directions/estimate
→ HTTP method와 요청 경로 확인
→ 신뢰할 수 있는 클라이언트 IP 결정
→ IP별 Bucket4j 토큰 검사
  ├─ 거부: HTTP 429 + Retry-After
  │       → Frontend 네이버 지도 전환
  └─ 허용
      → 목적지 관광 콘텐츠와 좌표 확인
      → 성공 응답 캐시 조회
      ├─ 캐시 적중: 내부 예상시간 반환
      └─ 캐시 미스
          → 동일 캐시 키의 진행 중 요청 조회
          ├─ 기존 요청 있음: 같은 결과 대기
          └─ 기존 요청 없음: 대표 요청으로 실행
              → 월 50,000건 사용량 예약
              ├─ 한도 도달: NAVER_MAP_REQUIRED
              └─ 예약 성공
                  → NAVER Directions 호출
                  → 성공 결과 캐시 저장
                  → 대기 요청에 같은 결과 전달
```

요청 제한은 캐시 적중 여부와 관계없이 먼저 적용한다. 캐시만 반복 조회하는 트래픽도 서버 자원을 소비하기 때문이다. 월 사용량은 NAVER 호출 가능성이 있는 대표 요청에만 예약한다.

요청 본문의 Bean Validation은 요청 제한을 통과한 뒤 Controller에서 수행한다. 잘못된 본문을 반복 전송하는 요청도 서버 자원을 소비하므로 토큰을 사용한다.

## 5. Backend 구조

기존 `directions` 패키지 안에 책임이 분리된 구성 요소를 추가한다.

```text
com.trip.whereU.directions
├── config
│   └── DirectionsRateLimitProperties
├── ratelimit
│   ├── ClientIpResolver
│   ├── DirectionsRateLimitFilter
│   └── DirectionsRateLimitService
├── service
│   ├── DirectionsRequestCoordinator
│   ├── DirectionsService
│   └── DirectionsUsageService
└── dto
    └── 기존 요청·응답 DTO
```

### 5.1 `ClientIpResolver`

- 기본적으로 Servlet 요청의 `remoteAddr`만 사용한다.
- 사용자가 직접 보낼 수 있는 `X-Forwarded-For`와 `Forwarded` 헤더는 신뢰하지 않는다.
- Nginx나 Load Balancer를 도입할 때 신뢰 가능한 프록시 범위를 명시한 뒤 별도 설정으로 확장한다.
- IP를 DB, 파일, 애플리케이션 로그에 기록하지 않는다.

### 5.2 `DirectionsRateLimitService`

- IP를 키로 Caffeine 캐시에서 Bucket4j 버킷을 조회하거나 생성한다.
- 분당 제한과 24시간 제한 토큰을 한 번에 검사한다.
- 허용 여부와 다음 토큰까지 남은 시간을 반환한다.
- 캐시는 최대 10,000개 IP만 유지하고 24시간 미사용 항목을 제거한다.

### 5.3 `DirectionsRateLimitFilter`

- `POST /api/directions/estimate`에만 적용한다.
- Controller가 실행되기 전에 토큰을 검사한다.
- 허용되면 기존 요청 흐름을 계속한다.
- 거부되면 NAVER API와 DB 사용량 예약을 실행하지 않고 JSON 429 응답을 반환한다.
- `GET /api/directions/availability`, 추천, 체류강도, 관리자 API는 필터 대상에서 제외한다.

### 5.4 `DirectionsRequestCoordinator`

- 진행 중인 요청을 `DirectionsCacheKey`별 `CompletableFuture`로 관리한다.
- 첫 요청만 월 사용량 예약과 NAVER 호출을 수행한다.
- 같은 키의 후속 요청은 첫 요청 결과를 기다린다.
- 성공 결과만 기존 Caffeine 응답 캐시에 저장한다.
- fallback이나 예외 결과는 장기 캐시에 저장하지 않는다.
- 대표 요청 완료 또는 실패 후 진행 중 항목을 반드시 제거해 다음 요청이 재시도할 수 있게 한다.

## 6. 설정

설정값은 환경 변수로 조정할 수 있게 하며 실제 Secret 값은 사용하지 않는다.

```yaml
directions:
  rate-limit:
    enabled: ${DIRECTIONS_RATE_LIMIT_ENABLED:true}
    minute-capacity: ${DIRECTIONS_RATE_LIMIT_MINUTE_CAPACITY:10}
    minute-refill-tokens: ${DIRECTIONS_RATE_LIMIT_MINUTE_REFILL_TOKENS:10}
    daily-capacity: ${DIRECTIONS_RATE_LIMIT_DAILY_CAPACITY:100}
    cache-maximum-size: ${DIRECTIONS_RATE_LIMIT_CACHE_MAXIMUM_SIZE:10000}
    cache-expire-after-hours: ${DIRECTIONS_RATE_LIMIT_CACHE_EXPIRE_AFTER_HOURS:24}
```

설정 클래스에는 Bean Validation을 적용한다.

- 모든 용량과 시간은 1 이상이어야 한다.
- 버킷 캐시 크기는 최대 100,000으로 제한해 무제한 메모리 증가를 막는다.
- 잘못된 설정은 요청 처리 중 우회하지 않고 애플리케이션 시작을 실패시킨다.

새 라이브러리는 Bucket4j core만 추가한다. Spring Cloud Gateway나 Redis는 도입하지 않는다.

## 7. API 설계

### 7.1 기존 성공 요청

요청과 성공 응답 계약은 변경하지 않는다.

```http
POST /api/directions/estimate
Content-Type: application/json
```

```json
{
  "originLatitude": 37.5665,
  "originLongitude": 126.978,
  "destinationContentId": "126508"
}
```

### 7.2 단기 요청 제한 응답

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 6
```

```json
{
  "success": false,
  "message": "자동차 시간 요청이 많아 네이버 지도 길찾기로 전환합니다."
}
```

- 정확한 제한값, 현재 사용 횟수, 클라이언트 IP는 응답에 포함하지 않는다.
- `Retry-After`는 다음 토큰을 사용할 수 있을 때까지의 초 단위 정수다.
- Frontend는 429를 일반 장애와 구분해 사용자 친화적인 네이버 지도 안내를 표시한다.

### 7.3 월 사용량 제한 응답

기존 HTTP 200의 `NAVER_MAP_REQUIRED` 응답을 유지한다.

```json
{
  "success": true,
  "data": {
    "status": "NAVER_MAP_REQUIRED",
    "fallbackReason": "MONTHLY_LIMIT_REACHED"
  }
}
```

단기 제한은 특정 IP에만 적용되고, 월 제한은 전체 서비스에 적용되므로 두 계약을 구분한다.

## 8. Frontend 동작

`RecommendationDirectionsCard`의 기존 단계는 유지한다.

1. availability 확인
2. 브라우저 현재 위치 확인
3. estimate 요청
4. 성공하면 시간·거리·통행료 표시

estimate가 429를 반환하면 다음 상태로 전환한다.

- 내부 상태의 fallback reason: `RATE_LIMIT`
- 안내 문구: `요청이 많아 네이버 지도에서 길찾기를 계속해 주세요.`
- 확보한 현재 위치가 있으면 네이버 지도 링크의 출발지로 사용한다.
- `다시 시도` 버튼은 표시하지 않는다.
- `네이버 지도에서 길찾기` 버튼은 즉시 표시한다.

일반 네트워크 장애와 NAVER API 장애는 기존 `ROUTE` fallback을 유지한다.

## 9. DB 구조

새 테이블과 컬럼을 추가하지 않는다.

기존 테이블만 유지한다.

```text
naver_directions_monthly_usage
- usage_month       VARCHAR(7) PRIMARY KEY
- reserved_count    BIGINT NOT NULL
- created_at        DATETIME NOT NULL
- updated_at        DATETIME NOT NULL
```

다음 정보는 DB에 저장하지 않는다.

- 클라이언트 IP
- 사용자 출발지
- IP별 요청 횟수
- 진행 중 요청 정보
- 경로 캐시 키

서버를 재시작하면 IP별 단기 제한과 진행 중 요청 상태는 초기화된다. 월 사용량은 DB에 남으므로 50,000건 최종 차단은 유지된다.

## 10. 예외 처리와 보안

### 10.1 fail-closed

정책상 토큰이 부족한 경우에만 429를 반환한다. 요청 제한 구성 요소가 예상하지 못한 상태가 되면 비용이 발생하는 NAVER 요청을 계속하지 않고 503을 반환한다. Frontend는 두 응답 모두 네이버 지도 fallback으로 처리하되, 429에는 요청 과다 안내를 사용하고 503에는 기존 경로 장애 안내를 사용한다.

### 10.2 로그

다음 값은 로그에 남기지 않는다.

- 원본 IP
- `X-Forwarded-For`
- 출발지·목적지 좌표
- NAVER API Key와 API Key ID

운영 지표가 필요하면 원본 식별정보 없이 다음 집계값만 기록한다.

- 허용 요청 수
- 제한 요청 수
- 월 한도 fallback 수
- single-flight로 합쳐진 요청 수

### 10.3 우회 방지

- 요청 제한은 Frontend가 아니라 Backend에서 수행한다.
- 사용자가 임의로 넣은 전달 헤더를 IP 식별에 사용하지 않는다.
- destination content ID와 위도·경도 Bean Validation은 기존대로 유지한다.
- rate-limit 응답은 캐시하지 않는다.

## 11. 성능 영향

### 11.1 요청 제한

토큰 검사는 메모리 연산이므로 DB 또는 외부 네트워크 호출을 추가하지 않는다. 최대 10,000개 버킷과 24시간 만료를 적용해 회전 IP 공격으로 메모리가 무제한 증가하지 않게 한다.

### 11.2 single-flight

같은 키의 동시 요청이 하나의 NAVER 호출을 공유하므로 외부 호출 수와 월 사용량 예약 횟수가 줄어든다. 서로 다른 키의 요청은 독립적으로 실행하므로 불필요하게 전체 요청을 직렬화하지 않는다.

### 11.3 DB

새로운 DB 조회와 쓰기는 없다. 월 사용량 잠금은 대표 요청 한 건만 수행하므로 동일 경로 동시 요청에서 DB 경합이 감소한다.

## 12. 테스트 설계

### 12.1 Backend 설정 테스트

- 기본 설정값이 적용된다.
- 0 이하의 용량과 시간이 애플리케이션 시작 단계에서 거부된다.
- 캐시 최대 크기 상한을 넘는 설정이 거부된다.

### 12.2 요청 제한 서비스 테스트

- 같은 IP의 10번째 분당 요청까지 허용되고 다음 요청이 거부된다.
- 시간이 지나 토큰이 보충되면 다시 허용된다.
- 24시간 한도 100회를 넘으면 거부된다.
- 서로 다른 IP는 서로의 토큰에 영향을 주지 않는다.
- 미사용 버킷은 만료된다.

테스트에서는 실제 시간을 기다리지 않고 주입한 시간 소스를 사용한다.

### 12.3 Filter와 API 계약 테스트

- estimate POST 요청에만 요청 제한이 적용된다.
- 제한 초과 시 Controller와 DirectionsService가 호출되지 않는다.
- 429 JSON과 `Retry-After`가 반환된다.
- `X-Forwarded-For`를 조작해도 `remoteAddr` 기준 제한을 우회할 수 없다.
- availability와 기존 API는 영향을 받지 않는다.
- 응답에 IP, 좌표, 제한 카운트가 포함되지 않는다.

### 12.4 single-flight 동시성 테스트

- 같은 키의 동시 요청 여러 개가 NAVER Client를 한 번만 호출한다.
- 월 사용량 예약도 한 번만 실행한다.
- 모든 대기 요청이 같은 성공 결과를 받는다.
- 대표 요청 실패 후 진행 중 항목이 제거되어 다음 요청이 재시도된다.
- 서로 다른 키는 동시에 독립 실행된다.

### 12.5 Frontend 테스트

- estimate 429 응답을 `RATE_LIMIT`으로 분류한다.
- 429 이후 다시 시도 버튼 없이 네이버 지도 버튼을 표시한다.
- 현재 위치를 확보했다면 네이버 지도 링크에 출발지를 포함한다.
- 일반 경로 오류는 기존 안내를 유지한다.

### 12.6 전체 회귀 검증

- Backend 전체 테스트
- Frontend 전체 테스트
- Frontend ESLint
- Frontend 프로덕션 빌드
- Secret 하드코딩과 민감 파일 추적 검사

## 13. 운영 전 확인 사항

1. 실제 배포 경로에 프록시가 있는지 확인한다.
2. 프록시가 있으면 신뢰할 수 있는 프록시 범위와 실제 클라이언트 IP 전달 방식을 별도로 설계한다.
3. 부하 테스트로 정상 사용자가 429를 과도하게 받지 않는지 확인한다.
4. 월 사용량 50,000건 차단과 IP별 제한이 함께 동작하는지 확인한다.
5. 원본 IP와 좌표가 애플리케이션·프록시 로그에 남지 않는지 확인한다.
6. 서버가 두 대 이상이 되기 전에 Redis 또는 다른 공유 저장소 기반 rate-limit으로 전환한다.

## 14. 완료 기준

- 익명 사용자의 반복 estimate 요청이 분당 및 24시간 정책으로 차단된다.
- 제한 요청은 NAVER 호출과 월 사용량 예약을 소비하지 않는다.
- 동일 경로 동시 요청은 NAVER 호출과 월 사용량 예약을 한 번만 수행한다.
- 정상 사용자는 기존 자동차 예상시간 응답을 그대로 받는다.
- 제한된 사용자는 오류 화면 대신 네이버 지도 길찾기로 전환된다.
- IP와 위치정보는 DB, 로그, API 응답에 노출되지 않는다.
- 기존 추천과 관광 수요 API의 동작이 바뀌지 않는다.
