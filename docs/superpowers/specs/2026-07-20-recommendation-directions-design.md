# 추천 관광지 자동차 예상 시간 및 네이버 지도 전환 설계

- 상태: 설계 승인 완료
- 작성일: 2026-07-20
- 대상: whereU 가성비 추천·맞춤 추천 화면

## 1. 기능 목적

추천 관광지가 사용자의 현재 위치에서 자동차로 얼마나 걸리는지 whereU 안에서 확인할 수 있게 한다. 네이버 Directions 5 무료 이용량에 가까워지면 외부 API 호출을 중단하고, 모든 길찾기를 네이버 지도 앱·웹으로 전환해 비용 초과를 방지한다.

이 기능은 추천 점수에 실제 이동 가능성 정보를 더해 사용자의 여행 의사결정을 돕는다. 기존 관광 체류강도 API와 추천 점수 계산식은 변경하지 않는다.

## 2. 확정된 사용자 경험

### 2.1 데스크톱

1. 사용자가 가성비 추천 또는 맞춤 추천 카드를 선택한다.
2. 선택된 추천 카드와 같은 높이의 오른쪽에 비슷한 크기의 길찾기 카드가 열린다.
3. 길찾기 카드에는 선택한 첫 번째 실제 관광지와 `자동차 시간 확인` 버튼을 표시한다.
4. 사용자가 버튼을 누르면 현재 월의 내부 길찾기 제공 가능 여부를 먼저 확인한다.
5. 내부 길찾기가 가능할 때만 브라우저 위치 권한을 요청한다.
6. 성공하면 자동차 예상 시간, 거리, 통행료를 길찾기 카드에 표시한다.
7. 다른 추천 카드를 선택하면 기존 길찾기 카드를 닫고 새 선택 카드 오른쪽으로 이동한다.

추천 패널은 현재처럼 지도 왼쪽에 유지한다. 길찾기 카드는 추천 패널 내부가 아니라 `map-workspace`의 별도 오버레이로 렌더링한다. 선택 카드의 화면 좌표를 기준으로 위쪽 위치를 계산하며 지도 영역을 벗어나지 않도록 보정한다.

### 2.2 좁은 화면과 모바일

오른쪽에 두 카드를 나란히 표시할 공간이 없으면 길찾기 카드를 선택된 추천 카드 바로 아래에 렌더링한다. 데스크톱과 모바일은 동일한 길찾기 상태와 API를 사용하고 배치만 변경한다.

### 2.3 무료 한도 도달 및 장애

- 월 안전 한도에 도달하면 위치 권한과 Directions 5 요청을 생략한다.
- 길찾기 카드에는 `네이버 지도에서 길찾기` 버튼만 표시한다.
- 위치 권한 거부, 관광지 좌표 누락, 경로 없음, 네이버 API 장애도 같은 네이버 지도 전환 흐름을 사용한다.
- 새로운 달에는 새로운 월 사용량 레코드를 사용하므로 내부 자동차 예상 시간 제공이 자동으로 재개된다.

## 3. 데이터 흐름

### 3.1 내부 예상 시간 제공

```text
추천 카드 선택
→ 길찾기 카드 표시
→ GET /api/directions/availability
→ AVAILABLE이면 브라우저 위치 권한 요청
→ POST /api/directions/estimate
→ 관광지 contentId로 DB 좌표 조회
→ 메모리 캐시 조회
→ 캐시 미스이면 월 사용량 1건 예약
→ 네이버 Directions 5 호출
→ 소요 시간·거리·통행료 응답
→ 길찾기 카드 표시
```

### 3.2 네이버 지도 전환

```text
월 50,000건 도달 또는 내부 계산 실패
→ NAVER_MAP_REQUIRED 상태
→ 네이버 지도 길찾기 버튼 표시
→ 모바일은 공식 nmap URL Scheme으로 연결
→ 데스크톱은 네이버 지도 웹 길찾기로 연결
```

네이버 지도 연결 시 출발지는 사용자의 현재 위치이고 목적지는 선택 관광지다. 위치 권한을 거부했거나 현재 위치가 없다면 출발지 파라미터를 생략한다. 네이버 지도는 생략된 출발지에 사용자의 현재 위치를 기본으로 사용할 수 있다.

## 4. Backend 설계

### 4.1 패키지 구조

기존 계층 구조를 유지하며 `directions` 도메인을 추가한다.

```text
com.trip.whereU.directions
├── client
│   └── NaverDirectionsClient
├── config
│   ├── NaverDirectionsConfig
│   └── NaverDirectionsProperties
├── controller
│   └── DirectionsController
├── dto
│   ├── DirectionsAvailabilityResponse
│   ├── DirectionsEstimateRequest
│   ├── DirectionsEstimateResponse
│   └── NaverDirectionsResponse
├── entity
│   └── NaverDirectionsMonthlyUsage
├── repository
│   └── NaverDirectionsMonthlyUsageRepository
└── service
    ├── DirectionsService
    └── DirectionsUsageService
```

- Controller: 요청 수신, Bean Validation, 공통 `ApiResponse` 반환만 담당한다.
- Service: 목적지 조회, 캐시, 사용량 예약, fallback 판단을 담당한다.
- Client: 네이버 인증 헤더와 Directions 5 HTTP 요청·응답 변환만 담당한다.
- Repository: 월 사용량 조회와 잠금 등 데이터 접근만 담당한다.
- Entity를 API 응답으로 직접 노출하지 않는다.

### 4.2 설정

```yaml
naver:
  maps:
    directions-base-url: ${NAVER_MAPS_DIRECTIONS_BASE_URL:https://maps.apigw.ntruss.com/map-direction/v1/driving}
    directions-monthly-safe-limit: ${NAVER_DIRECTIONS_MONTHLY_SAFE_LIMIT:50000}
    directions-cache-ttl-minutes: ${NAVER_DIRECTIONS_CACHE_TTL_MINUTES:10}
    directions-cache-maximum-size: ${NAVER_DIRECTIONS_CACHE_MAXIMUM_SIZE:10000}
```

기존 `NAVER_MAPS_API_KEY_ID`, `NAVER_MAPS_API_KEY` 환경 변수를 재사용한다. 동일한 Maps 애플리케이션에서 Directions 권한이 활성화되어 있어야 한다. 실제 인증값은 코드, 설정 파일, 테스트, 로그, Notion에 기록하지 않는다.

### 4.3 네이버 요청

Directions 5에는 다음 값을 전달한다.

- `start`: 사용자 현재 위치의 `longitude,latitude`
- `goal`: 관광지 DB 좌표의 `longitude,latitude`
- `option`: `traoptimal`
- `cartype`: `1`
- `lang`: `ko`

네이버 응답에서는 `route.traoptimal[0].summary`만 사용한다.

- `duration`: 밀리초를 분으로 반올림
- `distance`: 미터
- `tollFare`: 원
- `departureTime`: 경로 계산 기준 시각

전체 경로 좌표, 분기점과 상세 도로 정보는 저장하거나 Frontend로 전달하지 않는다.

## 5. API 설계

### 5.1 내부 제공 가능 여부

```http
GET /api/directions/availability
```

응답 예시:

```json
{
  "success": true,
  "data": {
    "status": "AVAILABLE"
  }
}
```

월 안전 한도 도달 시 `status`는 `NAVER_MAP_REQUIRED`다. 정확한 누적 호출 수와 한도 값은 외부에 노출하지 않는다. Frontend는 이 결과를 짧게 캐시하되, estimate 요청에서 Backend가 한도를 다시 검사한다.

### 5.2 자동차 예상 시간

```http
POST /api/directions/estimate
Content-Type: application/json
```

요청:

```json
{
  "originLatitude": 37.5665,
  "originLongitude": 126.9780,
  "destinationContentId": "126508"
}
```

성공 응답:

```json
{
  "success": true,
  "data": {
    "status": "AVAILABLE",
    "destinationContentId": "126508",
    "destinationName": "경주 황리단길",
    "travelTimeMinutes": 75,
    "distanceMeters": 84200,
    "tollFare": 3200,
    "calculatedAt": "2026-07-20T15:00:00+09:00",
    "fallbackReason": null
  }
}
```

네이버 지도 전환 응답:

```json
{
  "success": true,
  "data": {
    "status": "NAVER_MAP_REQUIRED",
    "destinationContentId": "126508",
    "destinationName": "경주 황리단길",
    "travelTimeMinutes": null,
    "distanceMeters": null,
    "tollFare": null,
    "calculatedAt": null,
    "fallbackReason": "MONTHLY_LIMIT_REACHED"
  }
}
```

`fallbackReason`은 다음 값만 사용한다.

- `MONTHLY_LIMIT_REACHED`
- `DESTINATION_COORDINATES_MISSING`
- `NAVER_API_UNAVAILABLE`
- `ROUTE_NOT_FOUND`

잘못된 위도·경도 또는 빈 `destinationContentId`는 Validation 오류로 처리한다. 존재하지 않는 관광지 콘텐츠는 404에 해당하는 도메인 예외로 처리한다. 예상 가능한 외부 API 실패는 추천 API 전체 실패로 전파하지 않고 `NAVER_MAP_REQUIRED`로 변환한다.

## 6. DB 구조와 월 사용량 제어

테이블은 월별 합계만 저장한다.

```text
naver_directions_monthly_usage
- usage_month       VARCHAR(7) PRIMARY KEY  예: 2026-07
- reserved_count    BIGINT NOT NULL
- created_at        DATETIME NOT NULL
- updated_at        DATETIME NOT NULL
```

사용자의 위치, 출발지, 목적지, 경로 결과는 이 테이블에 저장하지 않는다.

### 6.1 안전 한도

- 공식 월 무료 이용량: 60,000건
- whereU 월 안전 한도: 50,000건
- 남겨두는 여유: 10,000건

DB 사용량은 whereU 애플리케이션의 예약 건수만 나타낸다. 같은 네이버 클라우드 대표 계정에서 다른 애플리케이션이 Directions를 사용하면 실제 계정 사용량과 차이가 날 수 있으므로 10,000건의 여유를 유지하고 운영 콘솔 사용량을 함께 확인한다.

### 6.2 동시 요청

캐시 미스가 발생하면 `DirectionsUsageService`가 현재 월 레코드를 쓰기 잠금으로 조회한다. 레코드가 없으면 월 기본 레코드를 만든다. `reserved_count < safeLimit`일 때만 1을 증가시키고 외부 호출 권한을 반환한다. 월 최초 레코드 생성이 동시에 발생하면 `usage_month` PK 충돌을 처리한 뒤 기존 레코드를 다시 조회한다.

외부 API 호출 전에 횟수를 예약하고, 외부 호출이 실패해도 감소시키지 않는다. 실패 요청이 네이버 측 사용량으로 집계될 가능성을 고려한 보수적인 정책이다.

## 7. 캐시와 위치정보 보호

### 7.1 위치정보

- 브라우저 현재 위치는 사용자가 버튼을 누른 순간에만 요청한다.
- 화면에는 좌표 대신 `내 위치`라고 표시한다.
- Backend는 현재 위치를 네이버 API의 출발지로 전달한 뒤 DB에 저장하지 않는다.
- 요청 DTO, 네이버 요청 URL, 위도·경도는 로그에 출력하지 않는다.
- Frontend는 페이지 메모리에서만 현재 위치를 유지하고 새로고침 시 폐기한다.

### 7.2 Caffeine 캐시

정확한 위치를 그대로 저장하지 않고 위도·경도를 소수점 셋째 자리로 반올림해 약 100m 단위 캐시 키를 만든다.

```text
cache key = rounded latitude + rounded longitude + destinationContentId + traoptimal
TTL = 10분
maximum size = 10,000
```

Caffeine은 만료 시간과 최대 크기를 자동 관리해 수동 `ConcurrentHashMap`에서 발생할 수 있는 만료 데이터 누적 문제를 해결한다. 캐시는 프로세스 메모리에만 존재하고 서버 재시작 시 사라진다. 여러 서버 인스턴스가 각각 캐시를 보유하더라도 월 사용량 DB는 공유하므로 안전 한도는 유지된다.

## 8. Frontend 설계

### 8.1 추가 구성요소

```text
frontend/src
├── api/directionsApi.ts
├── hooks/useDirectionsAvailability.ts
├── hooks/useDrivingEstimate.ts
├── lib/geolocation.ts
├── lib/naverDirectionsLink.ts
├── types/directions.ts
└── components/RecommendationDirectionsCard.tsx
```

`App.tsx`는 선택한 추천과 길찾기 카드 표시 여부만 조정한다. 위치 권한, API 상태, 네이버 링크 생성은 각각의 모듈로 분리한다.

### 8.2 카드 위치

- 데스크톱: 추천 패널은 기존 `left: 20px`, `width: 340px`를 유지한다.
- 길찾기 카드: 추천 패널 오른쪽에 10~12px 간격으로 표시한다.
- 길찾기 카드 너비: 추천 카드와 유사한 약 310~320px.
- 선택 카드의 `getBoundingClientRect()`를 이용해 지도 작업 영역 기준 top을 계산한다.
- 추천 패널 스크롤과 창 크기 변경 시 위치를 다시 계산한다.
- 선택 카드가 보이도록 `scrollIntoView({block: "nearest"})`를 적용한다.
- 지도 작업 영역 아래를 벗어나면 길찾기 카드 top을 내부로 보정한다.
- 좁은 화면에서는 `useMediaQuery`를 통해 선택 추천 카드 아래에 같은 컴포넌트를 렌더링한다.

길찾기 카드에는 다음 상태가 있다.

```text
CLOSED
→ READY
→ CHECKING_AVAILABILITY
→ LOCATING
→ LOADING_ROUTE
→ AVAILABLE 또는 NAVER_MAP_REQUIRED
```

### 8.3 네이버 지도 연결

모바일에서는 네이버 공식 URL Scheme을 사용한다.

```text
nmap://route/car
?slat={현재위도}
&slng={현재경도}
&sname={내 위치}
&dlat={관광지위도}
&dlng={관광지경도}
&dname={관광지명}
&appname={whereU 웹 URL}
```

출발 좌표가 없으면 `slat`, `slng`, `sname`을 생략해 네이버 지도의 현재 위치 기본값을 사용한다. Android 모바일 웹은 네이버 공식 Intent URL로 앱 미설치 시 스토어 이동을 처리하고, iOS는 공식 안내에 따라 앱 호출 실패 시 App Store로 안내한다. 데스크톱에서는 네이버 지도 웹 길찾기 화면을 연다. 외부 링크는 새 탭에서 열고 `noopener,noreferrer`를 적용한다.

## 9. 오류 처리

| 상황 | whereU 동작 |
| --- | --- |
| 위치 권한 거부 | 출발지를 추측하지 않고 네이버 지도 버튼 표시 |
| 위치 확인 시간 초과 | 다시 시도와 네이버 지도 버튼 표시 |
| 관광지 좌표 없음 | 네이버 지도 장소 검색으로 연결 |
| 월 50,000건 도달 | 위치 권한과 Directions 호출 없이 네이버 지도 버튼 표시 |
| 네이버 인증·서버 오류 | 민감정보 없이 오류 로그를 남기고 네이버 지도 버튼 표시 |
| 경로 없음 | 네이버 지도 버튼 표시 |
| 추천 API 오류 | 기존 추천 오류 UI 유지; 길찾기 기능은 표시하지 않음 |

네이버 API Key, 현재 위치, 요청 URL은 로그에 남기지 않는다. 로그에는 상태 코드, 내부 오류 분류, 관광지 `contentId`만 남긴다.

## 10. 보안 검토

- Authentication: 현재 추천 화면과 동일하게 로그인 없이 사용 가능한 공개 API로 제공한다.
- Authorization: 사용자별 권한 데이터가 없으므로 별도 권한 분기는 없다.
- Validation: 현재 위치 범위와 `destinationContentId`를 검증한다.
- Exception Handling: 외부 API 오류는 공통 예외 응답 대신 정상 fallback 상태로 변환한다.
- CORS: 기존 Frontend 허용 정책 범위에서만 사용한다.
- CSRF: 쿠키 인증을 사용하지 않는 현재 구조에서는 상태 변경 목적의 사용자 데이터 API가 아니다. 향후 쿠키 인증 도입 시 다시 검토한다.
- Cost protection: 월 50,000건의 서버 측 hard limit을 모든 요청에 적용한다.
- Secret management: 인증키는 기존 환경 변수만 사용하며 응답·로그·Notion에 노출하지 않는다.

## 11. 예상 성능 영향

- 추천 API 자체에는 Directions 호출을 추가하지 않으므로 기존 추천 응답 시간은 변하지 않는다.
- 사용자가 `자동차 시간 확인`을 누른 경우에만 외부 API 호출이 발생한다.
- 캐시 히트는 외부 호출과 DB 사용량 예약을 생략한다.
- 캐시 미스는 월 사용량 잠금 트랜잭션 1회와 외부 HTTP 요청 1회를 추가한다.
- Caffeine 최대 10,000개 제한으로 메모리 사용량을 제한한다.
- 위치별 캐시 키를 약 100m 단위로 묶어 캐시 적중률과 위치정보 최소화를 함께 고려한다.

## 12. 테스트 전략

### Backend

- Directions Client 요청 헤더·좌표 순서·응답 변환 테스트
- 시간 밀리초를 분으로 변환하는 테스트
- 관광지 좌표 조회와 Validation 테스트
- 캐시 히트 시 외부 API와 사용량 예약을 생략하는 테스트
- 49,999번째 예약 성공과 50,000번째 이후 거부 테스트
- 동시 요청에서 안전 한도를 초과하지 않는 Repository 통합 테스트
- 네이버 오류 코드와 HTTP 오류의 fallback 변환 테스트
- 위치와 API Key가 로그 메시지에 포함되지 않는지 코드 검토

### Frontend

- 추천 카드 선택 시 오른쪽 길찾기 카드 표시
- 다른 카드 선택 시 길찾기 카드 이동 및 상태 초기화
- 위치 권한 허용·거부·시간 초과 상태
- AVAILABLE 응답의 시간·거리·통행료 표시
- NAVER_MAP_REQUIRED 응답의 네이버 지도 버튼 표시
- 모바일에서 선택 카드 아래 배치
- 네이버 링크 파라미터 URL 인코딩 테스트

Frontend 상태 테스트에는 Vitest와 React Testing Library를 도입한다. Vitest는 Vite 설정과 TypeScript를 재사용하며, React Testing Library는 구현 내부가 아닌 사용자의 클릭과 화면 결과를 검증한다. 기존 `build`와 `lint`만으로 확인할 수 없었던 브라우저 위치 권한과 비동기 상태 전환을 자동 검증하는 것이 도입 목적이다.

## 13. 구현 범위와 제외 범위

### 포함

- 선택 관광지 자동차 예상 시간·거리·통행료
- 사용자 위치의 일시적 출발지 사용
- 월 50,000건 안전 한도
- 10분 메모리 캐시
- 네이버 지도 앱·웹 fallback
- 추천 카드 오른쪽 부착형 UI와 모바일 인라인 UI
- Backend·Frontend 자동 테스트

### 제외

- whereU 내부 대중교통·도보 경로 계산
- 경로 polyline을 whereU 지도에 직접 표시
- 사용자 위치와 이동 기록 저장
- 차량 종류·연료·연비 개인 설정
- 관리자용 사용량 대시보드
- 기존 관광 체류강도와 추천 점수 계산 변경

## 14. 참고 자료

- [NAVER Maps Directions 5 API](https://api.ncloud-docs.com/docs/application-maps-directions5)
- [네이버 지도 앱 URL Scheme](https://guide.ncloud-docs.com/docs/en/maps-url-scheme)
- [NAVER Cloud Platform Maps 요금](https://ncloud.com/charge/region/ko?language=ko-KR)
