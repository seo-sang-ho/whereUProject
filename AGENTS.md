# AGENTS.md

## Project Overview

프로젝트명: whereU

whereU는 한국관광공사 OpenAPI를 활용하여 관광 수요 데이터와 관광 자원 데이터를 분석하고 시각화하는 데이터 기반 관광 분석 플랫폼이다.

목표는 단순 관광지 조회 서비스가 아니라 사용자에게 여행 의사결정을 지원하는 인사이트를 제공하는 것이다.

주요 기능

* 관광 수요 신호등
* 가성비 여행지 추천
* 개인 맞춤 추천
* 관광 수요 예측

---

## Technology Stack

### Frontend

* React
* Vite
* TypeScript
* Axios
* React Query
* Naver Map API

### Backend

* Java 21
* Spring Boot
* Spring Data JPA
* Spring Security
* JWT

### Database

* MySQL

### Deployment

* Docker

---

## Architecture Rules

### Layer Responsibilities

Controller

* 요청 수신
* 응답 반환

Service

* 비즈니스 로직 처리

Repository

* 데이터 접근

Entity

* 데이터 모델

DTO

* 요청 및 응답 모델

### Development Rules

* 비즈니스 로직은 Service 계층에 작성한다.
* Entity를 API 응답으로 직접 노출하지 않는다.
* Controller는 비즈니스 로직을 포함하지 않는다.
* DTO를 통해 요청과 응답을 처리한다.

---

## AI Working Rules

코드 작성 전 반드시 다음 내용을 설명한다.

1. 기능 목적
2. 데이터 흐름
3. API 설계
4. DB 구조
5. 예상 성능 영향

설명 없이 바로 코드를 생성하지 않는다.

---

## Learning Rules

개발자는 Spring Boot를 학습 중이다.

새로운 기술이나 라이브러리를 도입할 경우 다음 내용을 설명한다.

* 왜 사용하는가
* 어떤 문제를 해결하는가
* 기존 방식과 차이점은 무엇인가

단순 구현보다 이해를 우선한다.

---

## Data Analysis Rules

이 프로젝트는 단순 데이터 조회보다 분석 기능을 우선한다.

응답은 가능한 경우 수치 자체보다 의미를 해석하여 제공한다.

예시

* 관광 수요 점수 90
  → 매우 혼잡한 상태

* 관광 수요 점수 20
  → 비교적 여유로운 상태

---

## Map Rules

지도 기능 구현 시 다음을 고려한다.

* Marker
* Overlay
* Clustering
* Lazy Loading
* 성능 최적화

지도 이동 시 필요한 범위만 조회한다.

---

## Location Rules

모든 위치 정보는 다음 컬럼을 사용한다.

* latitude
* longitude

주변 조회 시

1. 범위 조회
2. 거리 계산

순으로 처리한다.

위치 기반 조회 성능을 고려하여 인덱스 사용을 검토한다.

---

## Modification Rules

기존 코드를 수정할 때

* 먼저 현재 구조를 분석한다.
* 추측으로 리팩토링하지 않는다.
* 동작 중인 기능을 임의로 제거하지 않는다.
* 필요한 범위만 최소 수정한다.
* 대규모 구조 변경은 먼저 제안한다.

---

## Security Rules

### Secret Management

다음 파일은 민감 정보 파일로 간주한다.

* .env
* .env.*
* application-secret.yml
* application-secret.yaml
* firebase-adminsdk.json
* *.pem
* *.key
* *.p12
* *.jks
* *.keystore

실제 Secret 값은 생성, 수정, 저장하지 않는다.

필요한 경우

* 예제 파일 작성
* 환경 변수 이름 제안
* 설정 방법 설명

만 수행한다.

### Hard Coding

민감 정보는 코드에 직접 작성하지 않는다.

예시

* JWT Secret
* API Key
* DB Password
* OAuth Secret

환경 변수 또는 Secret 설정을 사용한다.

### Git Rules

민감 파일은 Git에 포함하지 않는다.

새로운 Secret 파일 생성 시 .gitignore 등록 여부를 검토한다.

### API Security Checklist

새 API 작성 시 검토한다.

* 인증(Authentication)
* 권한(Authorization)
* Validation
* Exception Handling
* CORS
* CSRF

### Logging Rules

다음 정보는 로그에 출력하지 않는다.

* 비밀번호
* JWT
* Access Token
* Refresh Token
* API Key

민감 정보는 마스킹 처리한다.

### Before Commit Checklist

* Secret 하드코딩 여부
* 민감 파일 Git 추적 여부
* Validation 적용 여부
* 인증/인가 누락 여부
* 예외 처리 누락 여부
* 로그 민감 정보 노출 여부
