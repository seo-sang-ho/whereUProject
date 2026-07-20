# Recommendation Directions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 선택한 가성비·맞춤 추천 관광지까지의 자동차 예상 시간·거리·통행료를 whereU 안에서 보여주고, 월 50,000건 안전 한도나 장애 상황에서는 네이버 지도 길찾기로 전환한다.

**Architecture:** 기존 추천 API와 체류강도 API는 수정하지 않고 독립적인 `directions` 도메인을 추가한다. Backend가 관광 콘텐츠 좌표 조회, 월별 호출 예약, Caffeine 캐시, NAVER Directions 5 연동을 담당하며 Frontend는 사용자의 명시적 클릭 뒤에만 현재 위치를 메모리에 받아 길찾기 카드를 갱신한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA, MySQL/H2, Caffeine, React 19, TypeScript 6, TanStack Query, Axios, Vitest, React Testing Library, NAVER Maps Directions 5.

## Global Constraints

- 사용자 현재 위치, Directions 요청 URL, API Key를 DB·일반 로그·Notion에 저장하지 않는다.
- 목적지 좌표는 `destinationContentId`로 `tourism_content`를 조회해 얻고 클라이언트 입력을 신뢰하지 않는다.
- 네이버 외부 호출 전 DB에서 월 사용량을 예약하고, 실패해도 차감하지 않는다.
- 캐시 히트는 월 사용량 예약과 외부 API 호출을 모두 생략한다.
- 공개 응답에는 정확한 월 사용량과 안전 한도 수치를 노출하지 않는다.
- 기존 관광 체류강도, 가성비 추천, 맞춤 추천 계산식과 API는 변경하지 않는다.
- `.DS_Store`, `.env`, Secret, 빌드 산출물을 스테이징하지 않는다.
- 각 구현 커밋 전에 관련 테스트와 Secret 점검을 실행하고 테스트는 기능 커밋에 포함한다.

---

## Planned File Structure

### Backend additions

```text
backend/src/main/java/com/trip/whereU/directions/
├── client/NaverDirectionsClient.java
├── config/NaverDirectionsConfig.java
├── config/NaverDirectionsProperties.java
├── controller/DirectionsController.java
├── dto/DirectionsAvailabilityResponse.java
├── dto/DirectionsEstimateRequest.java
├── dto/DirectionsEstimateResponse.java
├── dto/DirectionsFallbackReason.java
├── dto/DirectionsStatus.java
├── dto/NaverDirectionsResult.java
├── entity/NaverDirectionsMonthlyUsage.java
├── exception/DirectionsDestinationNotFoundException.java
├── repository/NaverDirectionsMonthlyUsageRepository.java
├── service/DirectionsService.java
└── service/DirectionsUsageService.java
```

### Frontend additions

```text
frontend/src/
├── api/directionsApi.ts
├── components/RecommendationDirectionsCard.tsx
├── components/RecommendationDirectionsCard.test.tsx
├── hooks/useDirectionsAvailability.ts
├── hooks/useDrivingEstimate.ts
├── lib/geolocation.ts
├── lib/geolocation.test.ts
├── lib/naverDirectionsLink.ts
├── lib/naverDirectionsLink.test.ts
├── test/setup.ts
└── types/directions.ts
```

---

### Task 1: NAVER Directions 설정과 응답 변환 Client

**Files:**

- Modify: `backend/build.gradle`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `.env.example`
- Modify: `backend/src/main/java/com/trip/whereU/map/config/NaverMapsProperties.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/config/NaverDirectionsConfig.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/config/NaverDirectionsProperties.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/dto/NaverDirectionsResult.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/client/NaverDirectionsClient.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/client/NaverDirectionsClientTest.java`
- Modify: `backend/src/test/java/com/trip/whereU/map/client/NaverGeocodingClientTest.java`

**Interfaces:**

- Consumes: `NaverMapsProperties.apiKeyId/apiKey`, origin/destination `latitude/longitude`.
- Produces: `Optional<NaverDirectionsResult>` containing minutes, meters, toll fare, and calculation time.
- Does not log or expose the full URL, coordinates, or credentials.

- [ ] **Step 1: Write the failing client parsing and URI tests**

Create `NaverDirectionsClientTest` with these concrete cases:

```java
@Test
void buildsDirectionsUriWithLongitudeBeforeLatitude() {
    URI uri = client().buildDirectionsUri(37.5665, 126.9780, 35.8562, 129.2247);

    assertThat(uri.getRawQuery()).contains(
            "start=126.978,37.5665",
            "goal=129.2247,35.8562",
            "option=traoptimal",
            "cartype=1",
            "lang=ko"
    );
}

@Test
void parsesOptimalSummaryAndRoundsDurationUpToNearestMinute() {
    Optional<NaverDirectionsResult> result = client().parseResult("""
            {"code":0,"route":{"traoptimal":[{"summary":{
              "distance":84200,"duration":4470001,"tollFare":3200,
              "departureTime":"2026-07-20T15:00:00+09:00"
            }}]}}
            """);

    assertThat(result).contains(new NaverDirectionsResult(
            75, 84200, 3200, OffsetDateTime.parse("2026-07-20T15:00:00+09:00")
    ));
}

@Test
void returnsEmptyWhenOptimalRouteDoesNotExist() {
    assertThat(client().parseResult("{\"code\":0,\"route\":{}}"))
            .isEmpty();
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `cd backend && ./gradlew test --tests '*NaverDirectionsClientTest'`

Expected: compilation failure because the directions classes do not exist.

- [ ] **Step 3: Add dependencies and configuration records**

Add these dependencies to `backend/build.gradle`:

```groovy
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'com.github.ben-manes.caffeine:caffeine'
```

Extend `NaverMapsProperties` without changing the existing geocoding fields:

```java
@ConfigurationProperties(prefix = "naver.maps")
public record NaverMapsProperties(
        String geocodingBaseUrl,
        String apiKeyId,
        String apiKey
) {
}
```

Create the directions-only properties and config:

```java
@ConfigurationProperties(prefix = "naver.maps")
public record NaverDirectionsProperties(
        String directionsBaseUrl,
        long directionsMonthlySafeLimit,
        long directionsCacheTtlMinutes,
        long directionsCacheMaximumSize
) {
}
```

```java
@Configuration
@EnableConfigurationProperties(NaverDirectionsProperties.class)
public class NaverDirectionsConfig {
}
```

Add the four approved `naver.maps` settings to `application.yml` and add only non-secret example variables to `.env.example`:

```dotenv
NAVER_MAPS_DIRECTIONS_BASE_URL=https://maps.apigw.ntruss.com/map-direction/v1/driving
NAVER_DIRECTIONS_MONTHLY_SAFE_LIMIT=50000
NAVER_DIRECTIONS_CACHE_TTL_MINUTES=10
NAVER_DIRECTIONS_CACHE_MAXIMUM_SIZE=10000
```

- [ ] **Step 4: Implement the minimal client**

Use this public result contract:

```java
public record NaverDirectionsResult(
        int travelTimeMinutes,
        long distanceMeters,
        int tollFare,
        OffsetDateTime calculatedAt
) {
}
```

`NaverDirectionsClient` must provide exactly these package-visible helpers for tests and one public call:

```java
public Optional<NaverDirectionsResult> getDrivingEstimate(
        double originLatitude,
        double originLongitude,
        double destinationLatitude,
        double destinationLongitude
)

URI buildDirectionsUri(
        double originLatitude,
        double originLongitude,
        double destinationLatitude,
        double destinationLongitude
)

Optional<NaverDirectionsResult> parseResult(String responseBody)
```

Construct the request with `RestClient`, headers `x-ncp-apigw-api-key-id` and `x-ncp-apigw-api-key`, and JSON accept type. Parse only `route.traoptimal[0].summary`. Convert duration using `(durationMillis + 59_999) / 60_000`; return empty for a missing route. Validate only that base URL and the reused credentials have text, with an error message that never prints their values.

- [ ] **Step 5: Update the existing geocoding test constructor and run tests**

If the map property constructor remains unchanged, no production geocoding code changes are needed. Run:

`cd backend && ./gradlew test --tests '*NaverDirectionsClientTest' --tests '*NaverGeocodingClientTest'`

Expected: both test classes pass.

- [ ] **Step 6: Commit the client foundation**

Stage only Task 1 files and commit:

`git commit -m "feat: 네이버 자동차 길찾기 연동 기반을 추가"`

---

### Task 2: 월 50,000건 사용량 예약과 동시성 보호

**Files:**

- Create: `backend/src/main/java/com/trip/whereU/directions/entity/NaverDirectionsMonthlyUsage.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/repository/NaverDirectionsMonthlyUsageRepository.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsUsageService.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/service/DirectionsUsageServiceTest.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/repository/NaverDirectionsMonthlyUsageRepositoryTest.java`

**Interfaces:**

- Consumes: current `YearMonth`, configured safe limit.
- Produces: `boolean reserveCurrentMonth()` and `boolean isCurrentMonthAvailable()`.
- Persists: month and reserved count only; never location or route data.

- [ ] **Step 1: Write failing entity/service boundary tests**

Use a fixed clock for `2026-07-20` and safe limit `50_000`:

```java
@Test
void reservesTheLastAvailableRequest() {
    given(repository.findByUsageMonthForUpdate("2026-07"))
            .willReturn(Optional.of(new NaverDirectionsMonthlyUsage("2026-07", 49_999)));

    assertThat(service.reserveCurrentMonth()).isTrue();
    assertThat(usage.getReservedCount()).isEqualTo(50_000);
}

@Test
void rejectsReservationAtTheSafeLimit() {
    given(repository.findByUsageMonthForUpdate("2026-07"))
            .willReturn(Optional.of(new NaverDirectionsMonthlyUsage("2026-07", 50_000)));

    assertThat(service.reserveCurrentMonth()).isFalse();
    assertThat(usage.getReservedCount()).isEqualTo(50_000);
}

@Test
void availabilityDoesNotExposeOrIncrementUsage() {
    given(repository.findById("2026-07"))
            .willReturn(Optional.of(new NaverDirectionsMonthlyUsage("2026-07", 49_999)));

    assertThat(service.isCurrentMonthAvailable()).isTrue();
    then(repository).should(never()).save(any());
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd backend && ./gradlew test --tests '*DirectionsUsageServiceTest'`

Expected: compilation failure because the entity, repository, and service do not exist.

- [ ] **Step 3: Implement the entity and locked repository**

Entity contract:

```java
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

    protected NaverDirectionsMonthlyUsage() {}

    public NaverDirectionsMonthlyUsage(String usageMonth, long reservedCount) {
        this.usageMonth = usageMonth;
        this.reservedCount = reservedCount;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public boolean reserve(long safeLimit) {
        if (reservedCount >= safeLimit) return false;
        reservedCount++;
        updatedAt = LocalDateTime.now();
        return true;
    }

    public long getReservedCount() { return reservedCount; }
}
```

Repository contract:

```java
public interface NaverDirectionsMonthlyUsageRepository
        extends JpaRepository<NaverDirectionsMonthlyUsage, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select usage from NaverDirectionsMonthlyUsage usage where usage.usageMonth = :usageMonth")
    Optional<NaverDirectionsMonthlyUsage> findByUsageMonthForUpdate(String usageMonth);
}
```

- [ ] **Step 4: Implement reservation with first-row collision retry**

`DirectionsUsageService` uses `Clock` and `TransactionTemplate`. Algorithm:

1. Format current month as `yyyy-MM`.
2. In a transaction, locked-read the row.
3. If missing, attempt `saveAndFlush(new NaverDirectionsMonthlyUsage(month, 0))` in a separate `REQUIRES_NEW` transaction.
4. Catch only `DataIntegrityViolationException` from a simultaneous first-row insert.
5. Start/retry the locked transaction and call `reserve(safeLimit)`.
6. Availability uses an unlocked read and checks `reservedCount < safeLimit`; a missing row is available.

Keep retry count at two attempts and throw a coordinate-free `IllegalStateException` only if the row is still unavailable after a collision retry.

- [ ] **Step 5: Add the repository integration test**

`NaverDirectionsMonthlyUsageRepositoryTest` uses the H2 MySQL-mode test datasource and verifies:

- locked lookup returns the inserted row;
- two transactions cannot both advance a row initialized to `49_999` beyond `50_000`;
- the final stored count is exactly `50_000`.

Use two executor tasks plus latches and a `TransactionTemplate`; do not use sleeps.

- [ ] **Step 6: Run the focused and integration tests**

Run: `cd backend && ./gradlew test --tests '*DirectionsUsageServiceTest' --tests '*NaverDirectionsMonthlyUsageRepositoryTest'`

Expected: all tests pass and the concurrency assertion stores `50_000`.

- [ ] **Step 7: Commit quota protection**

Stage only Task 2 files and commit:

`git commit -m "feat: 길찾기 월간 안전 한도를 적용"`

---

### Task 3: 길찾기 Service, 캐시, 공개 API와 fallback

**Files:**

- Create: `backend/src/main/java/com/trip/whereU/directions/dto/DirectionsStatus.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/dto/DirectionsFallbackReason.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/dto/DirectionsAvailabilityResponse.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/dto/DirectionsEstimateRequest.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/dto/DirectionsEstimateResponse.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/exception/DirectionsDestinationNotFoundException.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsService.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/controller/DirectionsController.java`
- Modify: `backend/src/main/java/com/trip/whereU/global/exception/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/service/DirectionsServiceTest.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/controller/DirectionsControllerTest.java`

**Interfaces:**

- `GET /api/directions/availability` → `{status: AVAILABLE | NAVER_MAP_REQUIRED}`.
- `POST /api/directions/estimate` consumes validated transient origin and `destinationContentId`.
- Response status is successful data even for expected external fallback; nonexistent content is 404.

- [ ] **Step 1: Write failing service behavior tests**

Cover these named cases with Mockito: `returnsCachedEstimateWithoutReservingUsageOrCallingNaver`, `reservesBeforeCallingNaverAndCachesSuccessfulResult`, `returnsMonthlyLimitFallbackWithoutCallingNaver`, `returnsCoordinateMissingFallbackWhenTourismContentHasNoCoordinates`, `returnsRouteNotFoundFallbackForEmptyNaverResult`, `returnsApiUnavailableFallbackForRestClientException`, and `throwsNotFoundForUnknownDestinationContentId`. Each test must construct a real `TourismContent`, stub only the repository, usage service, and client boundary involved in that case, assert the complete `DirectionsEstimateResponse`, and use `then(usageService).shouldHaveNoInteractions()`, `then(client).shouldHaveNoInteractions()`, or `InOrder` as appropriate to prove skipped calls and ordering.

The cache-key test must prove `37.56649` and `37.56640` share the same three-decimal key while a different `destinationContentId` does not.

- [ ] **Step 2: Run the service test and confirm failure**

Run: `cd backend && ./gradlew test --tests '*DirectionsServiceTest'`

Expected: compilation failure because the service DTOs and service do not exist.

- [ ] **Step 3: Add enums, validation request, and response factories**

```java
public enum DirectionsStatus { AVAILABLE, NAVER_MAP_REQUIRED }

public enum DirectionsFallbackReason {
    MONTHLY_LIMIT_REACHED,
    DESTINATION_COORDINATES_MISSING,
    NAVER_API_UNAVAILABLE,
    ROUTE_NOT_FOUND
}

public record DirectionsEstimateRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double originLatitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double originLongitude,
        @NotBlank @Size(max = 30) String destinationContentId
) {
}
```

`DirectionsEstimateResponse` uses nullable wrapper types and static factories named `available` and `naverMapRequired`. `DirectionsAvailabilityResponse` contains only `DirectionsStatus status`.

- [ ] **Step 4: Implement `DirectionsService` and its bounded cache**

Constructor dependencies:

```java
DirectionsService(
        TourismContentRepository tourismContentRepository,
        DirectionsUsageService usageService,
        NaverDirectionsClient client,
        NaverDirectionsProperties properties
)
```

Expose:

```java
public DirectionsAvailabilityResponse getAvailability()
public DirectionsEstimateResponse estimate(DirectionsEstimateRequest request)
```

Create a Caffeine cache in the constructor with configured `expireAfterWrite` and `maximumSize`. Use an internal immutable key of rounded origin latitude, rounded origin longitude, content ID, and literal option `traoptimal`. Round using `BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP)`.

Order inside `estimate`:

1. Find tourism content or throw `DirectionsDestinationNotFoundException(contentId)`.
2. If destination coordinates are null, return `DESTINATION_COORDINATES_MISSING`.
3. Read cache and return on hit.
4. Reserve monthly usage; on false return `MONTHLY_LIMIT_REACHED`.
5. Call the client and map empty to `ROUTE_NOT_FOUND`.
6. Catch `RestClientException` and coordinate-free client parsing `IllegalStateException` as `NAVER_API_UNAVAILABLE`.
7. Cache only `AVAILABLE` responses.

- [ ] **Step 5: Write failing controller contract tests**

Use `@WebMvcTest(DirectionsController.class)` and mocked `DirectionsService` to assert:

- availability response uses existing `ApiResponse.success` wrapper;
- valid estimate response is HTTP 200;
- latitude `91`, longitude `181`, and blank content ID return HTTP 400;
- `DirectionsDestinationNotFoundException` returns HTTP 404 and no sensitive details.

- [ ] **Step 6: Implement controller and exception mapping**

```java
@RestController
@RequestMapping("/api/directions")
public class DirectionsController {
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<DirectionsAvailabilityResponse>> availability()

    @PostMapping("/estimate")
    public ResponseEntity<ApiResponse<DirectionsEstimateResponse>> estimate(
            @Valid @RequestBody DirectionsEstimateRequest request
    )
}
```

Add a specific handler before the broad handlers:

```java
@ExceptionHandler(DirectionsDestinationNotFoundException.class)
public ResponseEntity<ApiResponse<Void>> handleDirectionsDestinationNotFound(
        DirectionsDestinationNotFoundException exception
) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.failure(exception.getMessage()));
}
```

Do not change existing handlers or expose Bean Validation field values in custom logs.

- [ ] **Step 7: Run Backend directions and regression tests**

Run:

```bash
cd backend
./gradlew test --tests 'com.trip.whereU.directions.*'
./gradlew test
```

Expected: directions tests and the entire existing Backend suite pass.

- [ ] **Step 8: Commit the public directions API**

Stage only Task 3 files and commit:

`git commit -m "feat: 추천 관광지 자동차 예상 시간 API를 추가"`

---

### Task 4: Frontend 테스트 기반, API 타입, 위치 요청과 네이버 링크

**Files:**

- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/tsconfig.app.json`
- Create: `frontend/src/test/setup.ts`
- Create: `frontend/src/types/directions.ts`
- Create: `frontend/src/api/directionsApi.ts`
- Create: `frontend/src/hooks/useDirectionsAvailability.ts`
- Create: `frontend/src/hooks/useDrivingEstimate.ts`
- Create: `frontend/src/lib/geolocation.ts`
- Create: `frontend/src/lib/geolocation.test.ts`
- Create: `frontend/src/lib/naverDirectionsLink.ts`
- Create: `frontend/src/lib/naverDirectionsLink.test.ts`

**Interfaces:**

- Consumes browser geolocation only after a user action.
- Calls `/directions/availability` before `/directions/estimate`.
- Produces encoded external Naver app/web URLs without persisting origin.

- [ ] **Step 1: Add the test runner dependencies and configuration**

Run from `frontend`:

```bash
npm install --save-dev vitest jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

Add scripts:

```json
"test": "vitest run",
"test:watch": "vitest"
```

Add to Vite config:

```ts
test: {
  environment: 'jsdom',
  setupFiles: './src/test/setup.ts',
  globals: true,
},
```

Add `"vitest/globals"` to `tsconfig.app.json` types. `setup.ts` imports `@testing-library/jest-dom/vitest`.

- [ ] **Step 2: Write failing geolocation and link tests**

Geolocation tests verify success, permission denial, and timeout map to these error codes:

```ts
export type GeolocationErrorCode = 'PERMISSION_DENIED' | 'POSITION_UNAVAILABLE' | 'TIMEOUT'
```

Link tests verify:

- mobile `nmap://route/car` includes `slat/slng/sname` only when origin is present;
- destination name and Korean text are encoded;
- desktop web URL contains destination coordinates/name;
- coordinates-missing fallback produces a Naver place-search URL instead of invalid route coordinates.

- [ ] **Step 3: Run tests and confirm failure**

Run: `cd frontend && npm test -- src/lib/geolocation.test.ts src/lib/naverDirectionsLink.test.ts`

Expected: module-not-found failures for the two new libraries.

- [ ] **Step 4: Implement exact Frontend contracts**

`types/directions.ts`:

```ts
export type DirectionsStatus = 'AVAILABLE' | 'NAVER_MAP_REQUIRED'
export type DirectionsFallbackReason =
  | 'MONTHLY_LIMIT_REACHED'
  | 'DESTINATION_COORDINATES_MISSING'
  | 'NAVER_API_UNAVAILABLE'
  | 'ROUTE_NOT_FOUND'

export interface Coordinates { latitude: number; longitude: number }
export interface DirectionsAvailability { status: DirectionsStatus }
export interface DirectionsEstimateRequest {
  originLatitude: number
  originLongitude: number
  destinationContentId: string
}
export interface DirectionsEstimate {
  status: DirectionsStatus
  destinationContentId: string
  destinationName: string
  travelTimeMinutes: number | null
  distanceMeters: number | null
  tollFare: number | null
  calculatedAt: string | null
  fallbackReason: DirectionsFallbackReason | null
}
```

Keep `DirectionsEstimateRequest` independent from `Coordinates`: it has exactly the three request fields shown above.

`directionsApi.ts` unwraps the existing `ApiResponse` and exports:

```ts
export async function fetchDirectionsAvailability(): Promise<DirectionsAvailability>
export async function fetchDrivingEstimate(request: DirectionsEstimateRequest): Promise<DirectionsEstimate>
```

Hooks:

```ts
export function useDirectionsAvailability(enabled: boolean)
export function useDrivingEstimate()
```

Availability uses a 60-second `staleTime`; estimate is a mutation.

`requestCurrentPosition()` wraps `navigator.geolocation.getCurrentPosition` with `{enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000}` and returns only a plain `Coordinates` object.

`buildNaverDirectionsLink` accepts device kind, destination, optional origin, and app name. Use `URLSearchParams`; never manually concatenate unencoded names. Export `buildNaverPlaceSearchLink(name)` for missing coordinates.

- [ ] **Step 5: Run unit tests, build, and lint**

Run:

```bash
cd frontend
npm test -- src/lib/geolocation.test.ts src/lib/naverDirectionsLink.test.ts
npm run build
npm run lint
```

Expected: all commands pass.

- [ ] **Step 6: Commit the Frontend directions foundation**

Stage only Task 4 files and commit:

`git commit -m "feat: 사용자 위치 기반 길찾기 흐름을 추가"`

---

### Task 5: 독립 길찾기 카드 상태와 접근성

**Files:**

- Create: `frontend/src/components/RecommendationDirectionsCard.tsx`
- Create: `frontend/src/components/RecommendationDirectionsCard.test.tsx`
- Modify: `frontend/src/App.css`

**Interfaces:**

- Consumes selected first tourism content, availability query, geolocation provider, estimate mutation.
- Produces READY/locating/loading/result/fallback UI and external Naver button.
- Keeps coordinates in component memory only.

- [ ] **Step 1: Write failing component behavior tests**

Render the component with a `QueryClientProvider` and mocked API/geolocation modules. Test:

1. Initial card shows destination and `자동차 시간 확인`.
2. `NAVER_MAP_REQUIRED` availability skips geolocation and shows only `네이버 지도에서 길찾기`.
3. `AVAILABLE` calls availability, then geolocation, then estimate in that order.
4. Successful estimate displays `1시간 15분`, `84.2km`, `통행료 3,200원`.
5. Permission denial shows a concise explanation, `다시 시도`, and Naver button.
6. Changing `destination.contentId` resets the previous estimate.
7. Close button invokes `onClose` and has an accessible name.

- [ ] **Step 2: Run the component test and confirm failure**

Run: `cd frontend && npm test -- src/components/RecommendationDirectionsCard.test.tsx`

Expected: module-not-found failure for the component.

- [ ] **Step 3: Implement the card with a single explicit state model**

Props:

```ts
interface RecommendationDirectionsCardProps {
  destination: RecommendedTourismContent
  mobileInline: boolean
  onClose: () => void
}
```

State:

```ts
type DirectionsCardPhase =
  | 'READY'
  | 'CHECKING_AVAILABILITY'
  | 'LOCATING'
  | 'LOADING_ROUTE'
  | 'AVAILABLE'
  | 'NAVER_MAP_REQUIRED'
```

The click handler must execute this order:

```text
refetch availability
→ unavailable: set fallback without geolocation
→ available: requestCurrentPosition
→ store origin only in component state
→ mutate estimate
→ render available or fallback response
```

Use `aria-live="polite"` for progress/result copy, native buttons, and `target="_blank" rel="noopener noreferrer"` for the Naver link. Formatters should be pure functions in the same file and tested through visible text.

- [ ] **Step 4: Add card CSS without App placement rules**

Create `.directions-card` at `width: 316px`, with the existing neutral/green palette, 8px radius, matching border/shadow, and clear focus styles. Add state blocks for loading, metrics, fallback copy, primary/secondary buttons. Do not yet position it absolutely; Task 6 owns attachment positioning.

- [ ] **Step 5: Run component tests, build, and lint**

Run:

```bash
cd frontend
npm test -- src/components/RecommendationDirectionsCard.test.tsx
npm run build
npm run lint
```

Expected: all commands pass.

- [ ] **Step 6: Commit the card component**

Stage only Task 5 files and commit:

`git commit -m "feat: 자동차 예상 시간 카드를 추가"`

---

### Task 6: 선택 추천 카드 오른쪽 부착과 모바일 인라인 배치

**Files:**

- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`
- Create: `frontend/src/lib/directionsCardPosition.ts`
- Create: `frontend/src/lib/directionsCardPosition.test.ts`

**Interfaces:**

- Consumes the currently selected recommendation and first real tourism content.
- Produces a desktop overlay aligned to the selected recommendation item or a mobile inline card immediately below it.
- Resets/omits directions UI for fallback recommendation cards without tourism content.

- [ ] **Step 1: Write failing position-calculation tests**

Create a pure function:

```ts
export function calculateDirectionsCardTop(input: {
  selectedTop: number
  workspaceTop: number
  workspaceHeight: number
  cardHeight: number
  gap: number
}): number
```

Test exact cases:

- normal alignment returns `selectedTop - workspaceTop`;
- top never goes below `gap`;
- bottom overflow clamps to `workspaceHeight - cardHeight - gap`;
- a card taller than workspace still returns `gap`.

- [ ] **Step 2: Run the position test and confirm failure**

Run: `cd frontend && npm test -- src/lib/directionsCardPosition.test.ts`

Expected: module-not-found failure.

- [ ] **Step 3: Implement the pure positioning function**

Calculate `maximumTop` as `Math.max(gap, workspaceHeight - cardHeight - gap)`, then return `Math.max(gap, Math.min(rawTop, maximumTop))`. No DOM access belongs in this library.

- [ ] **Step 4: Integrate selection refs and directions destination in `App.tsx`**

Add:

- `mapWorkspaceRef` on `.map-workspace`;
- `selectedRecommendationRef` on the selected recommendation button;
- `directionsCardRef` wrapper for measuring desktop card height;
- `directionsCardTop` state;
- `mobileInline` state driven by `window.matchMedia('(max-width: 760px)')`;
- derived `selectedRecommendation` from the active recommendation array;
- derived `directionsDestination = selectedRecommendation?.tourismContents[0] ?? null`.

On selection call `scrollIntoView({block: 'nearest'})` after render. Recalculate top on:

- selected code/destination change;
- recommendation panel `scroll`;
- window `resize`;
- `ResizeObserver` changes to the selected card or directions card.

Clean up every listener and observer in the effect return. Do not add coordinates to localStorage, sessionStorage, URL state, React Query keys, or logs.

Render desktop as a sibling immediately after `</aside>`:

```tsx
{directionsDestination && !mobileInline && (
  <div
    ref={directionsCardRef}
    className="directions-card-overlay"
    style={{ top: directionsCardTop }}
  >
    <RecommendationDirectionsCard
      key={directionsDestination.contentId}
      destination={directionsDestination}
      mobileInline={false}
      onClose={() => setSelectedRecommendationCode(null)}
    />
  </div>
)}
```

For mobile, insert the same component in the selected `<li>` immediately after the recommendation `<button>` and pass `mobileInline={true}`. Keep the desktop and mobile branches mutually exclusive so one selection never mounts two API flows.

- [ ] **Step 5: Add responsive placement CSS**

Desktop:

```css
.directions-card-overlay {
  position: absolute;
  z-index: 11;
  left: 372px;
}
```

This preserves the panel at `left: 20px`, `width: 340px`, and gives a 12px gap. At `max-width: 760px`, hide the overlay defensively and set `.recommendation-directions-inline { margin-top: 8px; }` with card width `100%`. Adjust the current mobile media query from 640px only where necessary so the layout switch and JavaScript media query use the same breakpoint.

- [ ] **Step 6: Run all Frontend verification**

Run:

```bash
cd frontend
npm test
npm run build
npm run lint
```

Expected: all tests, TypeScript/Vite build, and ESLint pass.

- [ ] **Step 7: Perform local visual checks**

With Backend and Frontend running, verify in a browser at desktop width and at 390px width:

- selected recommendation card remains in the left panel;
- directions card is attached immediately to its right on desktop;
- scrolling the recommendation list keeps vertical alignment;
- another selection moves and resets the card;
- mobile displays it directly below the selected item;
- quota fallback does not request location permission;
- map controls remain clickable and the route card stays inside the workspace.

Capture no screenshots containing API credentials or exact user coordinates.

- [ ] **Step 8: Commit UI integration**

Stage only Task 6 files and commit:

`git commit -m "feat: 선택 추천 옆에 길찾기 카드를 연결"`

---

### Task 7: 전체 회귀·보안 검증과 개발 로그

**Files:**

- Verify only; modify implementation files only if a failed check requires a scoped fix.
- External write: Notion `whereU 개발 로그` DB.

- [ ] **Step 1: Run complete automated verification**

```bash
cd backend && ./gradlew test
cd ../frontend && npm test && npm run build && npm run lint
```

Expected: every command exits with code 0.

- [ ] **Step 2: Run privacy and Secret checks**

From repository root:

```bash
rg -n "NAVER_MAPS_API_KEY|api-key|originLatitude|originLongitude|getCurrentPosition" backend/src/main frontend/src
git status --short
git diff --check
```

Review each match. Configuration must reference environment variable names only; production logs must not include keys, coordinates, request DTOs, or complete Directions URLs. Confirm `.DS_Store`, `.env*`, build output, and credential files are unstaged.

- [ ] **Step 3: Review final diff and commit any verification-only fix separately**

Use `git diff`, explicit path staging, and `git diff --cached`. If no code changes were needed, do not create an empty commit.

- [ ] **Step 4: Record the completed work in Notion**

Create one `whereU 개발 로그` entry containing:

- all related commit hashes;
- Backend/Frontend/config files changed;
- summary of internal estimate, 50,000 safe limit, cache, and Naver fallback;
- Backend full test result;
- Frontend test/build/lint result;
- local desktop/mobile visual verification result;
- security result that no coordinates or secrets are persisted/logged;
- 후속 작업: Naver Cloud console Directions 권한, Web 서비스 URL, 운영 환경 변수, 사용량 알림 확인.

Do not record actual API keys, `.env` contents, exact current location, or request URLs.

## Final Acceptance Checklist

- [ ] `GET /api/directions/availability` never returns usage counts.
- [ ] The 50,000th reservation boundary is concurrency-safe.
- [ ] Cache hits make neither DB reservations nor Naver calls.
- [ ] Location permission is requested only after explicit click and availability success.
- [ ] User coordinates exist only in transient Frontend state and Backend request processing/cache key.
- [ ] Expected Naver errors return `NAVER_MAP_REQUIRED` without breaking recommendations.
- [ ] Desktop card is attached to the right of the selected recommendation card.
- [ ] Mobile card is directly below the selected recommendation card.
- [ ] Existing stay-strength and recommendation tests remain green.
- [ ] `.DS_Store`, secrets, and build artifacts are excluded from every commit.
