# SKILLS.md

## 프로젝트 아키텍처

Frontend

React
→ Axios
→ Spring Boot API

Backend

Controller
→ Service
→ Repository
→ MySQL

외부 연동

한국관광공사 OpenAPI
→ Service
→ DB 저장

---

## 패키지 구조

com.trip.whereU

* auth
* member
* tourism
* demand
* recommendation
* forecast
* map
* global

각 패키지는

* controller
* service
* repository
* dto
* entity

구조를 사용한다.

---

## 관광 데이터 처리

OpenAPI 데이터를 수집한 후

즉시 화면에 표시하지 않는다.

1. 데이터 수집
2. 데이터 정제
3. 데이터 분석
4. 추천 점수 계산
5. 사용자 제공

순서로 처리한다.

---

## 관광 수요 강도 계산

수요 강도는

Min-Max Scaling

방식을 사용한다.

결과 범위

0 ~ 1

예시

0.0 ~ 0.3

낮음

0.3 ~ 0.7

보통

0.7 ~ 1.0

높음

---

## 추천 알고리즘

추천 점수 계산 예시

추천점수 =
(관광자원점수 × 0.7)
+
((1 - 수요강도) × 0.3)

점수가 높을수록 추천 우선순위가 높다.

알고리즘 변경 시 변경 이유를 설명한다.

---

## JPA 규칙

기본 Fetch 전략

LAZY

사용

N+1 문제 발생 가능성을 설명한다.

필요 시

* Fetch Join
* EntityGraph

사용

---

## API 설계

모든 API는

ResponseEntity 사용

응답 형식

{
"success": true,
"data": {}
}

예외 응답

{
"success": false,
"message": "오류 내용"
}

---

## 지도 기능

Kakao Map API 사용

지도 기능 구현 시

* 마커
* 오버레이
* 클러스터링

적용 가능 여부를 검토한다.

---

## 성능 규칙

OpenAPI를 매 요청마다 호출하지 않는다.

가능하면 DB에 저장 후 사용한다.

캐싱이 필요한 경우

Redis 사용을 검토한다.

---

## 테스트

Backend

* JUnit5
* Mockito

Frontend

* Vitest

Service 계층 로직은 테스트 코드를 함께 작성한다.

---

## 설명 규칙

코드 생성 후 반드시 설명

1. 요청 흐름
2. 실행 순서
3. DB 접근 과정
4. 외부 API 호출 과정
5. 성능 영향

학습자가 이해할 수 있도록 단계적으로 설명한다.
