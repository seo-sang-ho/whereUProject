# 자동차 예상시간 API 남용 방지 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 없는 단일 Backend에서 IP별 요청 제한과 동일 경로 요청 병합을 적용하고, 제한된 사용자를 네이버 지도 길찾기로 전환한다.

**Architecture:** 비용이 발생하는 `POST /api/directions/estimate` 앞에 Bucket4j 기반 Servlet Filter를 두고, IP별 분당·24시간 토큰을 Caffeine 메모리에 보관한다. 허용된 캐시 미스 요청은 `DirectionsRequestCoordinator`가 동일 캐시 키별로 하나만 실행하며, 기존 DB 월 50,000건 예약은 최종 차단 장치로 유지한다. Frontend는 429를 별도 오류로 분류해 재시도 없이 네이버 지도 fallback을 표시한다.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring MVC, Bucket4j 8.19.0, Caffeine, JUnit 5, Mockito, React 19, TypeScript, Axios, React Query, Vitest

## Global Constraints

- Source design: `docs/superpowers/specs/2026-07-29-directions-api-abuse-protection-design.md`
- 적용 대상은 `POST /api/directions/estimate` 하나다.
- `GET /api/directions/availability`, 추천 API, 체류강도 API, 관리자 API에는 단기 요청 제한을 적용하지 않는다.
- IP별 정책은 분당 용량 10·분당 보충 10·24시간 용량 100·24시간 보충 100이다.
- IP 버킷은 최대 10,000개, 마지막 접근 후 24시간이 지나면 제거한다.
- Caffeine 버킷 최대 크기 설정의 절대 상한은 100,000이다.
- 클라이언트 IP는 `HttpServletRequest.getRemoteAddr()`만 사용하고 `X-Forwarded-For`와 `Forwarded`는 신뢰하지 않는다.
- IP, 출발지, 목적지 좌표, API Key는 DB·파일·애플리케이션 로그에 기록하지 않는다.
- 정책 초과는 HTTP 429와 `Retry-After`, 내부 rate-limit 장애는 HTTP 503을 반환한다.
- 단기 제한 요청은 NAVER API와 월 사용량 예약을 소비하지 않는다.
- 동일 경로 동시 요청은 NAVER 호출과 월 사용량 예약을 한 번만 수행한다.
- fallback과 예외 결과는 장기 응답 캐시에 저장하지 않는다.
- 새 DB 테이블, Redis, Spring Cloud Gateway, Spring Security는 도입하지 않는다.
- 기존 관광 수요 강도 API와 추천 점수 계산은 수정하지 않는다.
- 실제 Secret 값은 생성·수정·문서화·커밋하지 않는다.

Before every Task commit, run this staged-change gate in addition to the Task-specific tests:

```bash
git diff --cached --check
git diff --cached --name-only
git diff --cached | rg -n -e \
  '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AIza[0-9A-Za-z_-]{35}|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|gh[pousr]_[0-9A-Za-z]{20,}|github_pat_[0-9A-Za-z_]{20,}|sk-[0-9A-Za-z_-]{20,}'
```

Expected: `--check` exits 0, staged paths exactly match the Task, and the high-confidence Secret scan has no output. Also reject `.DS_Store`, real `.env*`, secret YAML, certificates, API Keys, passwords, JWTs, and build artifacts by inspecting the staged path list and diff.

## File Map

### Backend rate-limit

- Modify: `backend/build.gradle` — Bucket4j Java 17+ core 의존성
- Create: `backend/src/main/java/com/trip/whereU/directions/config/DirectionsRateLimitProperties.java` — 설정 바인딩과 Validation
- Modify: `backend/src/main/java/com/trip/whereU/directions/config/NaverDirectionsConfig.java` — rate-limit 설정 활성화
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/RateLimitDecision.java` — 허용 여부와 재시도 시간
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitService.java` — IP별 Bucket 생성·소비
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/ClientIpResolver.java` — 신뢰 가능한 IP 해석
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitFilter.java` — estimate POST 차단과 JSON 응답
- Modify: `backend/src/main/resources/application.yml` — 기본 설정
- Modify: `backend/src/test/resources/application.yml` — 테스트 설정
- Modify: `.env.example` — 환경 변수 이름과 안전한 예제값
- Create: `backend/src/test/java/com/trip/whereU/directions/config/DirectionsRateLimitPropertiesTest.java`
- Modify: `backend/src/test/java/com/trip/whereU/directions/config/NaverDirectionsPropertiesTest.java` — 새 설정 활성화 회귀
- Create: `backend/src/test/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitServiceTest.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/ratelimit/ClientIpResolverTest.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitFilterTest.java`
- Modify: `backend/src/test/java/com/trip/whereU/directions/controller/DirectionsControllerTest.java` — Filter 의존성 반영과 회귀 확인

### Backend single-flight

- Create: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsCacheKey.java` — 반올림 출발지·목적지·옵션 키
- Create: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsRequestCoordinator.java` — 진행 중 요청 공유
- Modify: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsService.java` — 캐시 미스를 coordinator로 실행
- Create: `backend/src/test/java/com/trip/whereU/directions/service/ObservedInFlightMap.java` — 결정적 동시성 테스트 도우미
- Create: `backend/src/test/java/com/trip/whereU/directions/service/DirectionsRequestCoordinatorTest.java`
- Modify: `backend/src/test/java/com/trip/whereU/directions/service/DirectionsServiceTest.java` — 실제 서비스 동시 요청 검증

### Frontend fallback

- Modify: `frontend/src/api/directionsApi.ts` — 429를 `DirectionsRateLimitError`로 변환
- Create: `frontend/src/api/directionsApi.test.ts` — Axios 429 분류 테스트
- Modify: `frontend/src/hooks/useDrivingEstimate.ts` — estimate mutation 재시도 명시적 비활성화
- Modify: `frontend/src/components/RecommendationDirectionsCard.tsx` — `RATE_LIMIT` 상태와 문구
- Modify: `frontend/src/components/RecommendationDirectionsCard.test.tsx` — 429 fallback과 일반 오류 회귀

## Parallel Execution Lanes

- Lane A: Task 1 완료 후 Task 2 실행
- Lane B: Task 3을 Lane A와 병렬 실행
- Lane C: Task 4를 Lane A·B와 병렬 실행
- Integration: Task 1~4의 커밋을 모두 검토한 뒤 Task 5 실행

Lane A의 Task 1과 Task 2는 같은 rate-limit 인터페이스를 사용하므로 순차 실행한다. Lane B는 `DirectionsService`만 수정하고 Lane C는 Frontend만 수정하므로 독립적으로 진행할 수 있다.

---

### Task 1: IP별 Bucket4j 설정과 요청 제한 서비스

**Files:**
- Modify: `backend/build.gradle:20-35`
- Create: `backend/src/main/java/com/trip/whereU/directions/config/DirectionsRateLimitProperties.java`
- Modify: `backend/src/main/java/com/trip/whereU/directions/config/NaverDirectionsConfig.java:1-9`
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/RateLimitDecision.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitService.java`
- Modify: `backend/src/main/resources/application.yml:35-45`
- Modify: `backend/src/test/resources/application.yml:12-16`
- Modify: `.env.example:6-13`
- Create: `backend/src/test/java/com/trip/whereU/directions/config/DirectionsRateLimitPropertiesTest.java`
- Modify: `backend/src/test/java/com/trip/whereU/directions/config/NaverDirectionsPropertiesTest.java:26-42`
- Create: `backend/src/test/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitServiceTest.java`

**Interfaces:**
- Consumes: Caffeine `Cache<String, Bucket>`, Bucket4j `Bucket`, `ConsumptionProbe`, `TimeMeter`
- Produces: `RateLimitDecision DirectionsRateLimitService.tryAcquire(String clientKey)`
- Produces: `DirectionsRateLimitProperties` with accessors `enabled()`, `minuteCapacity()`, `minuteRefillTokens()`, `dailyCapacity()`, `cacheMaximumSize()`, `cacheExpireAfterHours()`

- [ ] **Step 1: Add the Bucket4j dependency and write the failing property-binding tests**

Add this dependency using the Java 17+ artifact documented by Bucket4j:

```groovy
implementation 'com.bucket4j:bucket4j_jdk17-core:8.19.0'
```

Create `DirectionsRateLimitPropertiesTest` with an `ApplicationContextRunner`:

```java
class DirectionsRateLimitPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(NaverDirectionsConfig.class)
			.withPropertyValues(
					"naver.maps.directions-base-url=https://example.com/directions",
					"naver.maps.directions-connect-timeout=3s",
					"naver.maps.directions-read-timeout=7s",
					"naver.maps.directions-monthly-safe-limit=50000",
					"naver.maps.directions-cache-ttl-minutes=10",
					"naver.maps.directions-cache-maximum-size=10000",
					"directions.rate-limit.enabled=true",
					"directions.rate-limit.minute-capacity=10",
					"directions.rate-limit.minute-refill-tokens=10",
					"directions.rate-limit.daily-capacity=100",
					"directions.rate-limit.cache-maximum-size=10000",
					"directions.rate-limit.cache-expire-after-hours=24"
			);

	@Test
	void bindsApprovedValues() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(DirectionsRateLimitProperties.class);
			assertThat(context.getBean(DirectionsRateLimitProperties.class))
					.isEqualTo(new DirectionsRateLimitProperties(true, 10, 10, 100, 10_000, 24));
		});
	}

	@Test
	void rejectsCacheMaximumSizeAboveAbsoluteMaximum() {
		contextRunner
				.withPropertyValues("directions.rate-limit.cache-maximum-size=100001")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsNonPositiveCapacity() {
		contextRunner
				.withPropertyValues("directions.rate-limit.minute-capacity=0")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsNonPositiveExpirationHours() {
		contextRunner
				.withPropertyValues("directions.rate-limit.cache-expire-after-hours=0")
				.run(context -> assertThat(context).hasFailed());
	}
}
```

- [ ] **Step 2: Run the property tests and verify RED**

Run:

```bash
cd backend
./gradlew test --tests com.trip.whereU.directions.config.DirectionsRateLimitPropertiesTest
```

Expected: compilation fails because `DirectionsRateLimitProperties` does not exist.

- [ ] **Step 3: Implement validated properties and approved configuration**

Create:

```java
@Validated
@ConfigurationProperties(prefix = "directions.rate-limit")
public record DirectionsRateLimitProperties(
		boolean enabled,
		@Min(1) long minuteCapacity,
		@Min(1) long minuteRefillTokens,
		@Min(1) long dailyCapacity,
		@Min(1) @Max(100_000) long cacheMaximumSize,
		@Min(1) long cacheExpireAfterHours
) {
}
```

Enable both properties:

```java
@Configuration
@EnableConfigurationProperties({
		NaverDirectionsProperties.class,
		DirectionsRateLimitProperties.class
})
public class NaverDirectionsConfig {
}
```

Add to `application.yml`:

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

Add the same non-secret defaults to `backend/src/test/resources/application.yml` and `.env.example`.

Because `NaverDirectionsConfig` now enables both records, append the same six `directions.rate-limit.*` values to the existing `NaverDirectionsPropertiesTest.contextRunner(...)`. `ApplicationContextRunner` does not automatically load `application.yml`, so this existing test must provide every validated rate-limit value explicitly.

- [ ] **Step 4: Run the property tests and verify GREEN**

Run:

```bash
cd backend
./gradlew test --tests com.trip.whereU.directions.config.DirectionsRateLimitPropertiesTest
./gradlew test --tests com.trip.whereU.directions.config.NaverDirectionsPropertiesTest
```

Expected: both new and existing property test classes pass.

- [ ] **Step 5: Write failing service tests with controllable time**

Create package-private test clocks and wire both clocks into every test service:

```java
private final MutableTimeMeter timeMeter = new MutableTimeMeter();
private final MutableTicker ticker = new MutableTicker();

private DirectionsRateLimitService service(DirectionsRateLimitProperties properties) {
	return new DirectionsRateLimitService(properties, timeMeter, ticker);
}

private DirectionsRateLimitProperties properties(
		boolean enabled,
		long minuteCapacity,
		long minuteRefillTokens,
		long dailyCapacity
) {
	return new DirectionsRateLimitProperties(
			enabled,
			minuteCapacity,
			minuteRefillTokens,
			dailyCapacity,
			10_000,
			24
	);
}

final class MutableTimeMeter implements TimeMeter {
	private final AtomicLong nowNanos = new AtomicLong();

	@Override
	public long currentTimeNanos() {
		return nowNanos.get();
	}

	@Override
	public boolean isWallClockBased() {
		return false;
	}

	void advance(Duration duration) {
		nowNanos.addAndGet(duration.toNanos());
	}
}

final class MutableTicker implements Ticker {
	private final AtomicLong nowNanos = new AtomicLong();

	@Override
	public long read() {
		return nowNanos.get();
	}

	void advance(Duration duration) {
		nowNanos.addAndGet(duration.toNanos());
	}
}
```

Test the public contract:

```java
@Test
void rejectsEleventhRequestFromSameIpAndReturnsRetryAfter() {
	DirectionsRateLimitService service = service(properties(true, 10, 10, 100));

	for (int count = 0; count < 10; count++) {
		assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
	}

	RateLimitDecision rejected = service.tryAcquire("203.0.113.10");
	assertThat(rejected.allowed()).isFalse();
	assertThat(rejected.retryAfterSeconds()).isPositive();
}

@Test
void isolatesBucketsByIp() {
	DirectionsRateLimitService service = service(properties(true, 1, 1, 100));

	assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
	assertThat(service.tryAcquire("203.0.113.10").allowed()).isFalse();
	assertThat(service.tryAcquire("203.0.113.11").allowed()).isTrue();
}

@Test
void refillsWithoutSleeping() {
	DirectionsRateLimitService service = service(properties(true, 1, 1, 100));
	assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
	assertThat(service.tryAcquire("203.0.113.10").allowed()).isFalse();

	timeMeter.advance(Duration.ofMinutes(1));

	assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
}

@Test
void rejectsAfterDailyCapacityAndRefillsAfterTwentyFourHours() {
	DirectionsRateLimitService service = service(properties(true, 1_000, 1_000, 100));

	for (int count = 0; count < 100; count++) {
		assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
	}
	assertThat(service.tryAcquire("203.0.113.10").allowed()).isFalse();

	timeMeter.advance(Duration.ofHours(24));

	assertThat(service.tryAcquire("203.0.113.10").allowed()).isTrue();
}

@Test
void disabledLimiterAlwaysAllows() {
	DirectionsRateLimitService service = service(properties(false, 1, 1, 1));

	assertThat(service.tryAcquire("203.0.113.10")).isEqualTo(RateLimitDecision.permitted());
	assertThat(service.tryAcquire("203.0.113.10")).isEqualTo(RateLimitDecision.permitted());
}

@Test
void expiresUnusedBuckets() {
	DirectionsRateLimitService service = service(properties(true, 1, 1, 100));
	service.tryAcquire("203.0.113.10");
	assertThat(service.bucketCount()).isOne();

	ticker.advance(Duration.ofHours(25));
	service.cleanUp();

	assertThat(service.bucketCount()).isZero();
}

@Test
void capsBucketCacheSize() {
	DirectionsRateLimitService service = service(
			new DirectionsRateLimitProperties(true, 10, 10, 100, 2, 24)
	);
	service.tryAcquire("203.0.113.10");
	service.tryAcquire("203.0.113.11");
	service.tryAcquire("203.0.113.12");
	service.cleanUp();

	assertThat(service.bucketCount()).isLessThanOrEqualTo(2);
}
```

- [ ] **Step 6: Run the service tests and verify RED**

Run:

```bash
cd backend
./gradlew test --tests com.trip.whereU.directions.ratelimit.DirectionsRateLimitServiceTest
```

Expected: compilation fails because `RateLimitDecision` and `DirectionsRateLimitService` do not exist.

- [ ] **Step 7: Implement the decision and service**

Create:

```java
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

	public static RateLimitDecision permitted() {
		return new RateLimitDecision(true, 0);
	}

	public static RateLimitDecision rejected(long retryAfterSeconds) {
		return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
	}
}
```

Implement `DirectionsRateLimitService` with a production constructor and a package-private test constructor:

```java
@Service
public class DirectionsRateLimitService {

	private final DirectionsRateLimitProperties properties;
	private final Cache<String, Bucket> buckets;
	private final TimeMeter timeMeter;

	public DirectionsRateLimitService(DirectionsRateLimitProperties properties) {
		this(properties, TimeMeter.SYSTEM_NANOTIME, Ticker.systemTicker());
	}

	DirectionsRateLimitService(
			DirectionsRateLimitProperties properties,
			TimeMeter timeMeter,
			Ticker ticker
	) {
		this.properties = properties;
		this.timeMeter = timeMeter;
		this.buckets = Caffeine.newBuilder()
				.maximumSize(properties.cacheMaximumSize())
				.expireAfterAccess(Duration.ofHours(properties.cacheExpireAfterHours()))
				.ticker(ticker)
				.build();
	}

	public RateLimitDecision tryAcquire(String clientKey) {
		if (!properties.enabled()) {
			return RateLimitDecision.permitted();
		}
		ConsumptionProbe probe = buckets.get(clientKey, ignored -> newBucket())
				.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			return RateLimitDecision.permitted();
		}
		long retryAfterSeconds = Math.max(
				1,
				(probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L
		);
		return RateLimitDecision.rejected(retryAfterSeconds);
	}

	private Bucket newBucket() {
		return Bucket.builder()
				.withCustomTimePrecision(timeMeter)
				.addLimit(limit -> limit
						.capacity(properties.minuteCapacity())
						.refillGreedy(properties.minuteRefillTokens(), Duration.ofMinutes(1)))
				.addLimit(limit -> limit
						.capacity(properties.dailyCapacity())
						.refillGreedy(properties.dailyCapacity(), Duration.ofHours(24)))
				.build();
	}

	long bucketCount() {
		return buckets.estimatedSize();
	}

	void cleanUp() {
		buckets.cleanUp();
	}
}
```

- [ ] **Step 8: Run focused Backend tests**

Run:

```bash
cd backend
./gradlew test \
  --tests com.trip.whereU.directions.config.DirectionsRateLimitPropertiesTest \
  --tests com.trip.whereU.directions.config.NaverDirectionsPropertiesTest \
  --tests com.trip.whereU.directions.ratelimit.DirectionsRateLimitServiceTest
```

Expected: all focused tests pass.

- [ ] **Step 9: Review and commit Task 1**

Run:

```bash
git diff --check
git diff -- backend/build.gradle backend/src/main backend/src/test .env.example
```

Stage only Task 1 files and commit:

```bash
git add \
  backend/build.gradle \
  backend/src/main/java/com/trip/whereU/directions/config/DirectionsRateLimitProperties.java \
  backend/src/main/java/com/trip/whereU/directions/config/NaverDirectionsConfig.java \
  backend/src/main/java/com/trip/whereU/directions/ratelimit/RateLimitDecision.java \
  backend/src/main/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitService.java \
  backend/src/main/resources/application.yml \
  backend/src/test/resources/application.yml \
  backend/src/test/java/com/trip/whereU/directions/config/DirectionsRateLimitPropertiesTest.java \
  backend/src/test/java/com/trip/whereU/directions/config/NaverDirectionsPropertiesTest.java \
  backend/src/test/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitServiceTest.java \
  .env.example
git diff --cached --check
git commit -m "feat: 자동차 예상시간 IP별 요청 제한 기반을 추가"
```

---

### Task 2: estimate 전용 Filter와 429 API 계약

**Files:**
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/ClientIpResolver.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitFilter.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/ratelimit/ClientIpResolverTest.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitFilterTest.java`
- Modify: `backend/src/test/java/com/trip/whereU/directions/controller/DirectionsControllerTest.java:32-142`

**Interfaces:**
- Consumes: `RateLimitDecision DirectionsRateLimitService.tryAcquire(String clientKey)`
- Produces: `String ClientIpResolver.resolve(HttpServletRequest request)`
- Produces: HTTP 429 `ApiResponse<Void>` with `Retry-After`
- Produces: HTTP 503 `ApiResponse<Void>` if limiter evaluation throws

- [ ] **Step 1: Write failing client IP tests**

```java
class ClientIpResolverTest {

	private final ClientIpResolver resolver = new ClientIpResolver();

	@Test
	void usesRemoteAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.10");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
	}

	@Test
	void ignoresSpoofedForwardedHeaders() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.10");
		request.addHeader("X-Forwarded-For", "198.51.100.5");
		request.addHeader("Forwarded", "for=198.51.100.6");

		assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
	}
}
```

- [ ] **Step 2: Write failing Filter tests**

Use `MockHttpServletRequest`, `MockHttpServletResponse`, `MockFilterChain`, a mocked limiter, and a real Spring Boot 4 `tools.jackson.databind.ObjectMapper`. Initialize a fresh response and chain in `@BeforeEach`, then build the estimate request with an explicit servlet path:

```java
private MockHttpServletResponse response;
private MockFilterChain chain;

@BeforeEach
void setUp() {
	filter = new DirectionsRateLimitFilter(
			new ClientIpResolver(),
			rateLimitService,
			new ObjectMapper()
	);
	response = new MockHttpServletResponse();
	chain = new MockFilterChain();
}

private MockHttpServletRequest estimateRequest(String remoteAddress) {
	MockHttpServletRequest request =
			new MockHttpServletRequest(HttpMethod.POST.name(), "/api/directions/estimate");
	request.setServletPath("/api/directions/estimate");
	request.setRemoteAddr(remoteAddress);
	return request;
}

@Test
void rejectsEstimatePostWith429WithoutCallingDownstream() throws Exception {
	given(rateLimitService.tryAcquire("203.0.113.10"))
			.willReturn(RateLimitDecision.rejected(6));
	MockHttpServletRequest request = estimateRequest("203.0.113.10");
	request.addHeader("X-Forwarded-For", "198.51.100.5");
	MockHttpServletResponse response = new MockHttpServletResponse();
	MockFilterChain chain = new MockFilterChain();

	filter.doFilter(request, response, chain);

	assertThat(response.getStatus()).isEqualTo(429);
	assertThat(response.getHeader("Retry-After")).isEqualTo("6");
	assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
	assertThat(response.getContentAsString()).contains(
			"자동차 시간 요청이 많아 네이버 지도 길찾기로 전환합니다."
	);
	assertThat(response.getContentAsString())
			.doesNotContain(
					"203.0.113.10",
					"198.51.100.5",
					"37.5665",
					"126.978",
					"현재 사용 횟수",
					"limiter unavailable"
			);
	assertThat(chain.getRequest()).isNull();
}

@Test
void allowsEstimatePostWhenTokenIsAvailable() throws Exception {
	given(rateLimitService.tryAcquire("203.0.113.10"))
			.willReturn(RateLimitDecision.permitted());

	filter.doFilter(estimateRequest("203.0.113.10"), response, chain);

	assertThat(chain.getRequest()).isNotNull();
}

@ParameterizedTest
@CsvSource({
		"GET, /api/directions/estimate",
		"POST, /api/directions/availability",
		"GET, /api/directions/availability",
		"POST, /api/recommendations/personalized"
})
void skipsEveryNonTargetMethodOrPath(String method, String path) throws Exception {
	MockHttpServletRequest request = new MockHttpServletRequest(method, path);
	request.setServletPath(path);

	filter.doFilter(request, response, chain);

	then(rateLimitService).shouldHaveNoInteractions();
	assertThat(chain.getRequest()).isNotNull();
}

@Test
void failsClosedWith503WhenLimiterBreaks() throws Exception {
	given(rateLimitService.tryAcquire("203.0.113.10"))
			.willThrow(new IllegalStateException("limiter unavailable"));

	filter.doFilter(estimateRequest("203.0.113.10"), response, chain);

	assertThat(response.getStatus()).isEqualTo(503);
	assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
	assertThat(response.getHeader("Retry-After")).isNull();
	assertThat(response.getContentAsString()).doesNotContain("limiter unavailable");
	assertThat(chain.getRequest()).isNull();
}
```

- [ ] **Step 3: Run the new tests and verify RED**

Run:

```bash
cd backend
./gradlew test \
  --tests com.trip.whereU.directions.ratelimit.ClientIpResolverTest \
  --tests com.trip.whereU.directions.ratelimit.DirectionsRateLimitFilterTest
```

Expected: compilation fails because the resolver and filter do not exist.

- [ ] **Step 4: Implement the IP resolver**

```java
@Component
public class ClientIpResolver {

	public String resolve(HttpServletRequest request) {
		return request.getRemoteAddr();
	}
}
```

Do not inspect or log forwarding headers.

- [ ] **Step 5: Implement the estimate-only Filter**

```java
@Component
public class DirectionsRateLimitFilter extends OncePerRequestFilter {

	private static final String ESTIMATE_PATH = "/api/directions/estimate";
	private static final String RATE_LIMIT_MESSAGE =
			"자동차 시간 요청이 많아 네이버 지도 길찾기로 전환합니다.";
	private static final String UNAVAILABLE_MESSAGE =
			"자동차 시간 요청을 처리하지 못해 네이버 지도 길찾기로 전환합니다.";

	private final ClientIpResolver clientIpResolver;
	private final DirectionsRateLimitService rateLimitService;
	private final ObjectMapper objectMapper;

	public DirectionsRateLimitFilter(
			ClientIpResolver clientIpResolver,
			DirectionsRateLimitService rateLimitService,
			ObjectMapper objectMapper
	) {
		this.clientIpResolver = clientIpResolver;
		this.rateLimitService = rateLimitService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !HttpMethod.POST.matches(request.getMethod())
				|| !ESTIMATE_PATH.equals(request.getServletPath());
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		RateLimitDecision decision;
		try {
			decision = rateLimitService.tryAcquire(clientIpResolver.resolve(request));
		} catch (RuntimeException exception) {
			writeFailure(response, HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE_MESSAGE, null);
			return;
		}
		if (!decision.allowed()) {
			writeFailure(response, HttpStatus.TOO_MANY_REQUESTS, RATE_LIMIT_MESSAGE,
					decision.retryAfterSeconds());
			return;
		}
		filterChain.doFilter(request, response);
	}

	private void writeFailure(
			HttpServletResponse response,
			HttpStatus status,
			String message,
			Long retryAfterSeconds
	) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		if (retryAfterSeconds != null) {
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
		}
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(message));
	}
}
```

Do not log caught exceptions because the Filter has no external dependency and the API response must not expose internal details. Aggregate metrics can be added later without identifiers.

- [ ] **Step 6: Update the controller slice test for the registered Filter**

`@WebMvcTest` already includes `Filter` components. Import only the resolver dependency and mock the limiter service so the component-scanned Filter is registered exactly once:

```java
@Import(ClientIpResolver.class)
@WebMvcTest(DirectionsController.class)
```

Add the mocked service field inside the existing test class:

```java
@MockitoBean
private DirectionsRateLimitService rateLimitService;
```

Autowire `ApplicationContext` and add a registration regression test:

```java
@Autowired
private ApplicationContext applicationContext;

@Test
void registersDirectionsRateLimitFilterExactlyOnce() {
	assertThat(applicationContext.getBeansOfType(DirectionsRateLimitFilter.class)).hasSize(1);
}
```

Add `anyString`, `HttpHeaders`, `header`, and `times` imports. In `@BeforeEach`, allow requests by default:

```java
given(rateLimitService.tryAcquire(anyString())).willReturn(RateLimitDecision.permitted());
```

Add an HTTP contract test:

```java
@Test
void rateLimitedEstimateDoesNotReachController() throws Exception {
	given(rateLimitService.tryAcquire(anyString())).willReturn(RateLimitDecision.rejected(6));

	mockMvc.perform(post("/api/directions/estimate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string(HttpHeaders.RETRY_AFTER, "6"))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.data").doesNotExist())
			.andExpect(jsonPath("$.message").value(
					"자동차 시간 요청이 많아 네이버 지도 길찾기로 전환합니다."
			));

	then(directionsService).shouldHaveNoInteractions();
	then(rateLimitService).should(times(1)).tryAcquire(anyString());
}
```

- [ ] **Step 7: Run Filter and controller tests**

Run:

```bash
cd backend
./gradlew test \
  --tests com.trip.whereU.directions.ratelimit.ClientIpResolverTest \
  --tests com.trip.whereU.directions.ratelimit.DirectionsRateLimitFilterTest \
  --tests com.trip.whereU.directions.controller.DirectionsControllerTest
```

Expected: all tests pass. Confirm 429 responses do not include IP, coordinates, or internal exception text.

- [ ] **Step 8: Review and commit Task 2**

```bash
git diff --check
git add \
  backend/src/main/java/com/trip/whereU/directions/ratelimit/ClientIpResolver.java \
  backend/src/main/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitFilter.java \
  backend/src/test/java/com/trip/whereU/directions/ratelimit/ClientIpResolverTest.java \
  backend/src/test/java/com/trip/whereU/directions/ratelimit/DirectionsRateLimitFilterTest.java \
  backend/src/test/java/com/trip/whereU/directions/controller/DirectionsControllerTest.java
git diff --cached --check
git commit -m "feat: 자동차 예상시간 과다 요청을 차단"
```

---

### Task 3: 동일 경로 single-flight와 성공 결과 캐시

**Files:**
- Create: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsCacheKey.java`
- Create: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsRequestCoordinator.java`
- Modify: `backend/src/main/java/com/trip/whereU/directions/service/DirectionsService.java:23-129`
- Create: `backend/src/test/java/com/trip/whereU/directions/service/ObservedInFlightMap.java`
- Create: `backend/src/test/java/com/trip/whereU/directions/service/DirectionsRequestCoordinatorTest.java`
- Modify: `backend/src/test/java/com/trip/whereU/directions/service/DirectionsServiceTest.java:32-292`

**Interfaces:**
- Produces: package-private `record DirectionsCacheKey(BigDecimal originLatitude, BigDecimal originLongitude, String destinationContentId, String option)`
- Produces: `DirectionsEstimateResponse DirectionsRequestCoordinator.execute(DirectionsCacheKey key, Supplier<DirectionsEstimateResponse> operation)`
- Consumes: existing `DirectionsUsageService.reserveCurrentMonth()`, `NaverDirectionsClient.getDrivingEstimate(...)`

- [ ] **Step 1: Write failing coordinator tests**

```java
private final ObservedInFlightMap observedMap = new ObservedInFlightMap();
private final DirectionsRequestCoordinator coordinator =
		new DirectionsRequestCoordinator(observedMap);

@Test
void sharesOneOperationAcrossConcurrentCallers() throws Exception {
	CountDownLatch operationStarted = new CountDownLatch(1);
	CountDownLatch releaseOperation = new CountDownLatch(1);
	AtomicInteger executions = new AtomicInteger();
	Supplier<DirectionsEstimateResponse> operation = () -> {
		executions.incrementAndGet();
		operationStarted.countDown();
		await(releaseOperation);
		return response();
	};

	Future<DirectionsEstimateResponse> first = executor.submit(
			() -> coordinator.execute(key(), operation)
	);
	assertThat(operationStarted.await(5, TimeUnit.SECONDS)).isTrue();
	Future<DirectionsEstimateResponse> second = executor.submit(
			() -> coordinator.execute(key(), operation)
	);
	assertThat(observedMap.awaitFollower()).isTrue();
	releaseOperation.countDown();

	assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(response());
	assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(response());
	assertThat(executions).hasValue(1);
}

@Test
void removesFailedOperationSoNextCallCanRetry() {
	assertThatThrownBy(() -> coordinator.execute(key(), () -> {
		throw new IllegalStateException("first failure");
	})).isInstanceOf(IllegalStateException.class);

	assertThat(coordinator.execute(key(), this::response)).isEqualTo(response());
}

@Test
void doesNotSerializeDifferentKeys() throws Exception {
	CountDownLatch firstStarted = new CountDownLatch(1);
	CountDownLatch releaseFirst = new CountDownLatch(1);
	Future<DirectionsEstimateResponse> first = executor.submit(() ->
			coordinator.execute(key(), () -> {
				firstStarted.countDown();
				await(releaseFirst);
				return response();
			})
	);
	assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

	Future<DirectionsEstimateResponse> independent = executor.submit(
			() -> coordinator.execute(otherKey(), this::otherResponse)
	);
	assertThat(independent.get(5, TimeUnit.SECONDS)).isEqualTo(otherResponse());

	releaseFirst.countDown();
	assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(response());
}

final class ObservedInFlightMap extends ConcurrentHashMap<
		DirectionsCacheKey,
		CompletableFuture<DirectionsEstimateResponse>
> {
	private final CountDownLatch followerJoined = new CountDownLatch(1);

	@Override
	public CompletableFuture<DirectionsEstimateResponse> putIfAbsent(
			DirectionsCacheKey key,
			CompletableFuture<DirectionsEstimateResponse> value
	) {
		CompletableFuture<DirectionsEstimateResponse> existing =
				super.putIfAbsent(key, value);
		if (existing != null) {
			followerJoined.countDown();
		}
		return existing;
	}

	boolean awaitFollower() throws InterruptedException {
		return followerJoined.await(5, TimeUnit.SECONDS);
	}
}
```

Put `ObservedInFlightMap` in its own package-private test source file so both coordinator and service tests can use it. Use `key()`, `otherKey()`, `otherResponse()`, and `await(CountDownLatch)` helpers with fixed values; make `response()` an `AVAILABLE` response so the first test proves every follower receives the same successful result. The injected map is a package-private test seam that proves the follower observed the leader future before release. Always shut down the fixed two-thread executor in `@AfterEach`.

- [ ] **Step 2: Run coordinator tests and verify RED**

Run:

```bash
cd backend
./gradlew test --tests com.trip.whereU.directions.service.DirectionsRequestCoordinatorTest
```

Expected: compilation fails because `DirectionsCacheKey` and `DirectionsRequestCoordinator` do not exist.

- [ ] **Step 3: Implement cache key and coordinator**

Move the nested key out of `DirectionsService`:

```java
record DirectionsCacheKey(
		BigDecimal originLatitude,
		BigDecimal originLongitude,
		String destinationContentId,
		String option
) {
}
```

Implement the coordinator:

```java
@Component
public class DirectionsRequestCoordinator {

	private final ConcurrentMap<DirectionsCacheKey, CompletableFuture<DirectionsEstimateResponse>>
			inFlight;

	public DirectionsRequestCoordinator() {
		this(new ConcurrentHashMap<>());
	}

	DirectionsRequestCoordinator(
			ConcurrentMap<DirectionsCacheKey, CompletableFuture<DirectionsEstimateResponse>>
					inFlight
	) {
		this.inFlight = inFlight;
	}

	public DirectionsEstimateResponse execute(
			DirectionsCacheKey key,
			Supplier<DirectionsEstimateResponse> operation
	) {
		CompletableFuture<DirectionsEstimateResponse> leader = new CompletableFuture<>();
		CompletableFuture<DirectionsEstimateResponse> existing = inFlight.putIfAbsent(key, leader);
		if (existing != null) {
			return await(existing);
		}
		try {
			DirectionsEstimateResponse response = operation.get();
			leader.complete(response);
			return response;
		} catch (RuntimeException | Error failure) {
			leader.completeExceptionally(failure);
			throw failure;
		} finally {
			inFlight.remove(key, leader);
		}
	}

	private DirectionsEstimateResponse await(
			CompletableFuture<DirectionsEstimateResponse> future
	) {
		try {
			return future.get();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("진행 중인 자동차 경로 대기가 중단되었습니다.", exception);
		} catch (ExecutionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new IllegalStateException(cause);
		}
	}
}
```

- [ ] **Step 4: Write a failing service-level concurrency test**

Construct `DirectionsService` with an observed real coordinator and mocked repository, usage service, and NAVER client. Make the leader return a non-cached fallback so the test cannot pass merely because Caffeine served a late follower:

```java
@Test
void concurrentSameRouteFallbackRunsOnceAndLaterRequestRetries() throws Exception {
	CountDownLatch clientStarted = new CountDownLatch(1);
	CountDownLatch releaseClient = new CountDownLatch(1);
	AtomicInteger clientCalls = new AtomicInteger();
	NaverDirectionsResult success = result(25, 12_300, 0);
	given(tourismContentRepository.findByContentId("126508"))
			.willReturn(Optional.of(content("126508", "경복궁", 37.578822, 126.976993)));
	given(usageService.reserveCurrentMonth()).willReturn(true);
	given(client.getDrivingEstimate(37.5665, 126.978, 37.578822, 126.976993))
			.willAnswer(invocation -> {
				if (clientCalls.getAndIncrement() == 0) {
					clientStarted.countDown();
					assertThat(releaseClient.await(5, TimeUnit.SECONDS)).isTrue();
					return Optional.empty();
				}
				return Optional.of(success);
			});

	ObservedInFlightMap observedMap = new ObservedInFlightMap();
	DirectionsService service = service(new DirectionsRequestCoordinator(observedMap));
	Future<DirectionsEstimateResponse> first = executor.submit(() -> service.estimate(request()));
	assertThat(clientStarted.await(5, TimeUnit.SECONDS)).isTrue();
	Future<DirectionsEstimateResponse> second =
			executor.submit(() -> service.estimate(request()));
	assertThat(observedMap.awaitFollower()).isTrue();
	releaseClient.countDown();

	DirectionsEstimateResponse fallback = fallback(
			"126508", "경복궁", DirectionsFallbackReason.ROUTE_NOT_FOUND
	);
	assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(fallback);
	assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(fallback);
	then(usageService).should(times(1)).reserveCurrentMonth();
	then(client).should(times(1)).getDrivingEstimate(
			37.5665, 126.978, 37.578822, 126.976993
	);

	DirectionsEstimateResponse later = service.estimate(request());
	assertThat(later).isEqualTo(available("126508", "경복궁", success));
	then(usageService).should(times(2)).reserveCurrentMonth();
	then(client).should(times(2)).getDrivingEstimate(
			37.5665, 126.978, 37.578822, 126.976993
	);
}
```

Reuse the same `ObservedInFlightMap` test helper in `DirectionsServiceTest` and shut down the executor in `@AfterEach`.

- [ ] **Step 5: Run the service test and verify RED**

Run:

```bash
cd backend
./gradlew test --tests com.trip.whereU.directions.service.DirectionsServiceTest.concurrentSameRouteFallbackRunsOnceAndLaterRequestRetries
```

Expected: test fails because `DirectionsService` does not yet use the coordinator.

- [ ] **Step 6: Integrate single-flight into DirectionsService**

Inject `DirectionsRequestCoordinator` as the fifth constructor parameter and update the test helper to pass either a fresh real coordinator or the coordinator supplied by the concurrency test:

```java
private final DirectionsRequestCoordinator requestCoordinator;

public DirectionsService(
		TourismContentRepository tourismContentRepository,
		DirectionsUsageService usageService,
		NaverDirectionsClient client,
		NaverDirectionsProperties properties,
		DirectionsRequestCoordinator requestCoordinator
) {
	this.tourismContentRepository = tourismContentRepository;
	this.usageService = usageService;
	this.client = client;
	this.requestCoordinator = requestCoordinator;
	this.cache = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(properties.directionsCacheTtlMinutes()))
			.maximumSize(properties.directionsCacheMaximumSize())
			.build();
}
```

In `DirectionsServiceTest`, preserve existing calls through an overload:

```java
private DirectionsService service() {
	return service(new DirectionsRequestCoordinator());
}

private DirectionsService service(DirectionsRequestCoordinator requestCoordinator) {
	return new DirectionsService(
			tourismContentRepository,
			usageService,
			client,
			new NaverDirectionsProperties(
					"https://example.com",
					Duration.ofSeconds(3),
					Duration.ofSeconds(7),
					50_000,
					10,
					100
			),
			requestCoordinator
	);
}
```

Keep the first successful response cache lookup. For a cache miss:

```java
return requestCoordinator.execute(cacheKey, () -> {
	DirectionsEstimateResponse responseAfterWait = cache.getIfPresent(cacheKey);
	if (responseAfterWait != null) {
		return responseAfterWait;
	}
	return loadEstimate(request, destination, cacheKey);
});
```

Move reservation and NAVER interaction into:

```java
private DirectionsEstimateResponse loadEstimate(
		DirectionsEstimateRequest request,
		TourismContent destination,
		DirectionsCacheKey cacheKey
) {
	if (!usageService.reserveCurrentMonth()) {
		return fallback(destination, DirectionsFallbackReason.MONTHLY_LIMIT_REACHED);
	}
	try {
		Optional<NaverDirectionsResult> result = client.getDrivingEstimate(
				request.originLatitude(),
				request.originLongitude(),
				destination.getLatitude(),
				destination.getLongitude()
		);
		if (result.isEmpty()) {
			return fallback(destination, DirectionsFallbackReason.ROUTE_NOT_FOUND);
		}
		DirectionsEstimateResponse response = available(destination, result.orElseThrow());
		cache.put(cacheKey, response);
		return response;
	} catch (RestClientException | IllegalStateException exception) {
		return fallback(destination, DirectionsFallbackReason.NAVER_API_UNAVAILABLE);
	}
}
```

Remove the old nested `DirectionsCacheKey` from `DirectionsService`.

- [ ] **Step 7: Run coordinator and directions service tests**

Run:

```bash
cd backend
./gradlew test \
  --tests com.trip.whereU.directions.service.DirectionsRequestCoordinatorTest \
  --tests com.trip.whereU.directions.service.DirectionsServiceTest
```

Expected: all tests pass, including existing cache and fallback tests.

- [ ] **Step 8: Review and commit Task 3**

```bash
git diff --check
git add \
  backend/src/main/java/com/trip/whereU/directions/service/DirectionsCacheKey.java \
  backend/src/main/java/com/trip/whereU/directions/service/DirectionsRequestCoordinator.java \
  backend/src/main/java/com/trip/whereU/directions/service/DirectionsService.java \
  backend/src/test/java/com/trip/whereU/directions/service/ObservedInFlightMap.java \
  backend/src/test/java/com/trip/whereU/directions/service/DirectionsRequestCoordinatorTest.java \
  backend/src/test/java/com/trip/whereU/directions/service/DirectionsServiceTest.java
git diff --cached --check
git commit -m "fix: 동일 자동차 경로 중복 호출을 방지"
```

---

### Task 4: Frontend 429 분류와 네이버 지도 fallback

**Files:**
- Modify: `frontend/src/api/directionsApi.ts:1-34`
- Create: `frontend/src/api/directionsApi.test.ts`
- Modify: `frontend/src/hooks/useDrivingEstimate.ts:1-6`
- Modify: `frontend/src/components/RecommendationDirectionsCard.tsx:1-339`
- Modify: `frontend/src/components/RecommendationDirectionsCard.test.tsx:1-277`

**Interfaces:**
- Produces: `class DirectionsRateLimitError extends Error`
- Produces: `number | null DirectionsRateLimitError.retryAfterSeconds`
- Consumes: Backend HTTP 429 and optional `Retry-After`
- Produces: Component-local fallback reason `'RATE_LIMIT'`

- [ ] **Step 1: Write failing API error-classification tests**

Mock `apiClient.post` without adding a new test dependency:

```typescript
const request: DirectionsEstimateRequest = {
  originLatitude: 37.5665,
  originLongitude: 126.978,
  destinationContentId: '126508',
}

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
}))

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: mocks.post,
  },
}))

beforeEach(() => {
  mocks.post.mockReset()
})

it('429를 DirectionsRateLimitError로 변환한다', async () => {
  mocks.post.mockRejectedValue({
    isAxiosError: true,
    response: {
      status: 429,
      headers: { 'retry-after': '6' },
    },
  })

  await expect(fetchDrivingEstimate(request)).rejects.toMatchObject({
    name: 'DirectionsRateLimitError',
    retryAfterSeconds: 6,
  })
})

it.each([undefined, '', '0', '-1', '1.5', 'not-a-number'])(
  'Retry-After %s는 노출 가능한 초 단위 정수가 아니면 null로 처리한다',
  async (retryAfter) => {
    mocks.post.mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 429,
        headers: retryAfter === undefined ? {} : { 'retry-after': retryAfter },
      },
    })

    await expect(fetchDrivingEstimate(request)).rejects.toMatchObject({
      name: 'DirectionsRateLimitError',
      retryAfterSeconds: null,
    })
  },
)

it('일반 네트워크 오류는 그대로 전달한다', async () => {
  const error = new Error('network unavailable')
  mocks.post.mockRejectedValue(error)

  await expect(fetchDrivingEstimate(request)).rejects.toBe(error)
})

it('503 Axios 오류는 rate-limit 오류로 바꾸지 않는다', async () => {
  const error = {
    isAxiosError: true,
    response: { status: 503, headers: {} },
  }
  mocks.post.mockRejectedValue(error)

  await expect(fetchDrivingEstimate(request)).rejects.toBe(error)
})

it('정상 estimate 응답은 기존 데이터 계약을 유지한다', async () => {
  const estimate = {
    status: 'AVAILABLE',
    destinationContentId: '126508',
    destinationName: '경복궁',
    travelTimeMinutes: 25,
    distanceMeters: 12_300,
    tollFare: 0,
    calculatedAt: '2026-07-20T15:00:00+09:00',
    fallbackReason: null,
  }
  mocks.post.mockResolvedValue({
    data: { success: true, data: estimate, message: null },
  })

  await expect(fetchDrivingEstimate(request)).resolves.toEqual(estimate)
})
```

- [ ] **Step 2: Run the API test and verify RED**

Run:

```bash
cd frontend
npm test -- src/api/directionsApi.test.ts
```

Expected: the 429 test fails because `DirectionsRateLimitError` does not exist.

- [ ] **Step 3: Implement the typed 429 error**

Add the Axios import and typed error:

```typescript
import axios from 'axios'

export class DirectionsRateLimitError extends Error {
  readonly retryAfterSeconds: number | null

  constructor(retryAfterSeconds: number | null) {
    super('자동차 시간 요청이 제한되었습니다.')
    this.name = 'DirectionsRateLimitError'
    this.retryAfterSeconds = retryAfterSeconds
  }
}
```

Wrap only the estimate call:

```typescript
export async function fetchDrivingEstimate(
  request: DirectionsEstimateRequest,
): Promise<DirectionsEstimate> {
  try {
    const response = await apiClient.post<ApiResponse<DirectionsEstimate>>(
      '/directions/estimate',
      request,
    )
    if (!response.data.success || !response.data.data) {
      throw new Error(response.data.message || '자동차 예상 시간을 불러오지 못했습니다.')
    }
    return response.data.data
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 429) {
      const retryAfter = Number(error.response.headers['retry-after'])
      throw new DirectionsRateLimitError(
        Number.isSafeInteger(retryAfter) && retryAfter > 0 ? retryAfter : null,
      )
    }
    throw error
  }
}
```

- [ ] **Step 4: Run the API tests and verify GREEN**

Run:

```bash
cd frontend
npm test -- src/api/directionsApi.test.ts
```

Expected: all API tests pass, including invalid `Retry-After`, 503 passthrough, and success regression cases.

- [ ] **Step 5: Write failing card fallback tests**

Change the existing complete module mock to a partial mock so the real exported error class remains available, and import `DirectionsRateLimitError` in the test:

```typescript
import { DirectionsRateLimitError } from '../api/directionsApi'

vi.mock('../api/directionsApi', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api/directionsApi')>(),
  fetchDirectionsAvailability: mocks.fetchDirectionsAvailability,
  fetchDrivingEstimate: mocks.fetchDrivingEstimate,
}))
```

Add:

```typescript
it('429이면 재시도 없이 현재 위치를 포함한 네이버 지도 버튼을 보여준다', async () => {
  mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'AVAILABLE' })
  mocks.requestCurrentPosition.mockResolvedValue({ latitude: 37.5, longitude: 127 })
  mocks.fetchDrivingEstimate.mockRejectedValue(new DirectionsRateLimitError(6))
  const user = userEvent.setup()
  renderCard()

  await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))

  expect(await screen.findByText('요청이 많아 네이버 지도에서 길찾기를 계속해 주세요.'))
    .toBeInTheDocument()
  expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
  const link = screen.getByRole('link', { name: '네이버 지도에서 길찾기' })
  const url = new URL(link.getAttribute('href') ?? '')
  expect(url.searchParams.get('slat')).toBe('37.5')
expect(url.searchParams.get('slng')).toBe('127')
expect(mocks.fetchDrivingEstimate).toHaveBeenCalledOnce()
})

it('일반 estimate 오류는 기존 경로 오류 문구를 유지한다', async () => {
  mocks.fetchDirectionsAvailability.mockResolvedValue({ status: 'AVAILABLE' })
  mocks.requestCurrentPosition.mockResolvedValue({ latitude: 37.5, longitude: 127 })
  mocks.fetchDrivingEstimate.mockRejectedValue(new Error('network error'))
  const user = userEvent.setup()
  renderCard()

  await user.click(screen.getByRole('button', { name: '자동차 시간 확인' }))

  expect(await screen.findByText('자동차 예상 시간을 불러오지 못했어요.'))
    .toBeInTheDocument()
})
```

- [ ] **Step 6: Run the component test and verify RED**

Run:

```bash
cd frontend
npm test -- src/components/RecommendationDirectionsCard.test.tsx
```

Expected: the 429-specific message test fails because every estimate error becomes `ROUTE`.

- [ ] **Step 7: Implement RATE_LIMIT state**

Import `DirectionsRateLimitError`, extend `FallbackReason`, and classify the caught error:

```typescript
type FallbackReason =
  | GeolocationErrorCode
  | 'AVAILABILITY'
  | 'DESTINATION_COORDINATES_MISSING'
  | 'RATE_LIMIT'
  | 'ROUTE'
```

```typescript
} catch (error) {
  if (!activeRef.current) {
    return
  }
  setState({
    phase: 'NAVER_MAP_REQUIRED',
    origin,
    fallbackReason: error instanceof DirectionsRateLimitError
      ? 'RATE_LIMIT'
      : 'ROUTE',
  })
}
```

Add:

```typescript
case 'RATE_LIMIT':
  return '요청이 많아 네이버 지도에서 길찾기를 계속해 주세요.'
```

Do not add `RATE_LIMIT` to `isRetryableGeolocationFailure`, so the card shows no retry button.

Make the production mutation policy explicit in `useDrivingEstimate.ts`:

```typescript
export function useDrivingEstimate() {
  return useMutation({
    mutationFn: fetchDrivingEstimate,
    retry: false,
  })
}
```

- [ ] **Step 8: Run focused Frontend tests**

Run:

```bash
cd frontend
npm test -- \
  src/api/directionsApi.test.ts \
  src/components/RecommendationDirectionsCard.test.tsx
```

Expected: all focused tests pass.

- [ ] **Step 9: Review and commit Task 4**

```bash
git diff --check
git add \
  frontend/src/api/directionsApi.ts \
  frontend/src/api/directionsApi.test.ts \
  frontend/src/hooks/useDrivingEstimate.ts \
  frontend/src/components/RecommendationDirectionsCard.tsx \
  frontend/src/components/RecommendationDirectionsCard.test.tsx
git diff --cached --check
git commit -m "feat: 요청 제한 시 네이버 지도 전환을 추가"
```

---

### Task 5: 통합 회귀, 보안 검사, 개발 로그

**Files:**
- Verify: all Task 1-4 files
- Update externally: Notion `whereU 개발 로그`

**Interfaces:**
- Consumes: all committed Backend and Frontend changes
- Produces: green full test/build evidence, clean Git scope, Notion implementation record

- [ ] **Step 1: Run the complete Backend test suite**

Run:

```bash
cd backend
./gradlew test
```

Expected: `BUILD SUCCESSFUL`, zero failed tests.

- [ ] **Step 2: Run all Frontend tests**

Run:

```bash
cd frontend
npm test
```

Expected: every Vitest file and test passes.

- [ ] **Step 3: Run Frontend lint and production build**

Run:

```bash
cd frontend
npm run lint
npm run build
```

Expected: ESLint exits 0 and Vite reports a successful production build.

- [ ] **Step 4: Verify request-limit and single-flight behavior from test reports**

Run:

```bash
! rg -n 'failures="[1-9]|errors="[1-9]' backend/build/test-results/test
```

Expected: no output and exit 0.

Confirm each expected test class has a generated XML report:

```bash
for test_class in \
  com.trip.whereU.directions.config.DirectionsRateLimitPropertiesTest \
  com.trip.whereU.directions.config.NaverDirectionsPropertiesTest \
  com.trip.whereU.directions.ratelimit.DirectionsRateLimitServiceTest \
  com.trip.whereU.directions.ratelimit.ClientIpResolverTest \
  com.trip.whereU.directions.ratelimit.DirectionsRateLimitFilterTest \
  com.trip.whereU.directions.service.DirectionsRequestCoordinatorTest \
  com.trip.whereU.directions.service.DirectionsServiceTest
do
  test -f "backend/build/test-results/test/TEST-${test_class}.xml"
done
```

Expected: the loop exits 0.

- [ ] **Step 5: Run Secret and sensitive-file checks**

Run:

```bash
git ls-files | rg '(^|/)(\.env($|\.)|application-secret\.(yml|yaml)$|firebase-adminsdk\.json$|.*\.(pem|key|p12|jks|keystore)$)'
```

Expected: only explicitly allowed example files, or no output.

Confirm no build output or `.DS_Store` is tracked:

```bash
! git ls-files | rg '(^|/)(dist|build)/|(^|/)\.DS_Store$'
```

Expected: no output and exit 0.

Confirm there are no unexpected untracked files:

```bash
git ls-files --others --exclude-standard
```

Expected: no output. If the user owns an unrelated untracked file, report its path and leave it untouched instead of deleting or committing it.

Run high-confidence tracked and staged source scans:

```bash
git grep -n -I -E -e \
  '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AIza[0-9A-Za-z_-]{35}|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|gh[pousr]_[0-9A-Za-z]{20,}|github_pat_[0-9A-Za-z_]{20,}|sk-[0-9A-Za-z_-]{20,}'

git diff --cached | rg -n -e \
  '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AIza[0-9A-Za-z_-]{35}|AKIA[0-9A-Z]{16}|ASIA[0-9A-Z]{16}|gh[pousr]_[0-9A-Za-z]{20,}|github_pat_[0-9A-Za-z_]{20,}|sk-[0-9A-Za-z_-]{20,}'
```

Expected: no output from either scan. Treat exit 1 from `rg` as “no match”; any matching line blocks push and must be reviewed without copying the value into Notion.

- [ ] **Step 6: Verify Git scope and commit separation**

Run:

```bash
git status --short --branch
git log --oneline -6
```

Expected:

- `.DS_Store`, `.env`, build output, Secret files are absent.
- Task 1/2/3 Backend commits and Task 4 Frontend commit are distinct.
- No unrelated user change is staged or committed.

- [ ] **Step 7: Record the implementation in Notion**

Create a new `whereU 개발 로그` page containing:

- every implementation commit hash and Korean commit title
- related Backend and Frontend files
- IP policy: 10/minute, 100/24 hours
- 429 and `Retry-After` API contract
- single-flight result
- Backend/Frontend test counts
- lint/build result
- Secret scan result
- next development task
- separate `운영 배포 전 TODO` section for trusted proxy policy, NAT/shared-IP load test, real MySQL/NAVER E2E, log inspection, rotating-IP/10,000-bucket eviction limits, and multi-instance Redis migration
- mark proxy/NAT verification, privacy-preserving access-log inspection, and normal-user 429 load testing as deployment blockers rather than ordinary follow-up ideas

Never include actual IP samples from users, API Keys, passwords, JWT values, or `.env` contents.

- [ ] **Step 8: Stop for review before push or merge**

Do not push, create a PR, or merge until the user reviews:

- commit list
- full verification results
- security scan result
- Notion log

Report the exact branch, commit hashes, and any remaining runtime-only verification gap.
