# TourAPI 추천 지역 매핑 운영 절차

추천 API는 기본적으로 whereU 지역 코드에서 TourAPI 법정동 코드를 파생한다.

예시:

```text
26-26380 -> 26-380
```

이 fallback으로 관광지 카드가 연결되지 않는 지역만 `tourism_region_tour_api_mapping`에 명시 매핑을 추가한다.

## fallback 미매칭 지역 확인

```sql
SELECT
    r.region_code,
    r.region_name,
    CONCAT(
        SUBSTRING_INDEX(r.region_code, '-', 1),
        '-',
        RIGHT(SUBSTRING_INDEX(r.region_code, '-', -1), 3)
    ) AS fallback_legal_dong_code
FROM tourism_region r
LEFT JOIN tourism_region_tour_api_mapping m
    ON m.region_code = r.region_code
    AND m.enabled = true
LEFT JOIN tourism_content c
    ON c.legal_dong_code = CONCAT(
        SUBSTRING_INDEX(r.region_code, '-', 1),
        '-',
        RIGHT(SUBSTRING_INDEX(r.region_code, '-', -1), 3)
    )
    AND c.content_type_id = '12'
WHERE m.id IS NULL
  AND c.id IS NULL
ORDER BY r.region_code;
```

## 명시 매핑 추가

fallback 법정동 코드가 실제 TourAPI 응답의 `lDongRegnCd-lDongSignguCd`와 다를 때만 추가한다.

```sql
INSERT INTO tourism_region_tour_api_mapping (
    region_code,
    region_name,
    legal_dong_code,
    default_content_type_id,
    default_category_code,
    enabled,
    updated_at
) VALUES (
    '추천_REGION_CODE',
    '지역명',
    'TourAPI_법정동코드',
    '12',
    NULL,
    true,
    NOW()
)
ON DUPLICATE KEY UPDATE
    region_name = VALUES(region_name),
    legal_dong_code = VALUES(legal_dong_code),
    default_content_type_id = VALUES(default_content_type_id),
    default_category_code = VALUES(default_category_code),
    enabled = VALUES(enabled),
    updated_at = NOW();
```

특정 분류만 추천 카드로 보여야 하면 `default_category_code`에 `NA040500` 같은 신분류체계 코드를 넣는다.

## 반영 확인

```bash
curl "http://localhost:8080/api/recommendations/value?limit=10"
```

응답의 각 추천 item에서 `tourismContents[0].firstImage`, `title`, `address`가 채워지는지 확인한다.

## 추천 TOP 지역 자동 수집

가성비 추천 TOP 10 지역의 관광정보를 한 번에 수집하려면 관리자 API를 호출한다.

```bash
curl -X POST "http://localhost:8080/api/admin/tourism-contents/sync/recommendation-top?limit=10&pageSize=10&contentTypeId=12&arrange=C"
```

운영 환경에서 `TOURISM_CONTENT_ADMIN_TOKEN`을 설정한 경우 `X-Admin-Token` 헤더를 함께 보낸다.

```bash
curl -X POST \
  -H "X-Admin-Token: [REDACTED]" \
  "http://localhost:8080/api/admin/tourism-contents/sync/recommendation-top?limit=10&pageSize=10&contentTypeId=12&arrange=C"
```

특정 신분류체계 관광지만 수집하려면 분류 조건을 추가한다.

```bash
curl -X POST \
  -H "X-Admin-Token: [REDACTED]" \
  "http://localhost:8080/api/admin/tourism-contents/sync/recommendation-top?limit=10&pageSize=10&contentTypeId=12&arrange=C&lclsSystm1=NA&lclsSystm2=NA04&lclsSystm3=NA040500"
```

## 맞춤 추천 TOP 지역 자동 수집

테마별 맞춤 추천 지역의 관광정보도 관리자 API로 미리 수집한다.

```bash
curl -X POST \
  -H "X-Admin-Token: [REDACTED]" \
  "http://localhost:8080/api/admin/tourism-contents/sync/personalized-top?themes=NATURE&limit=20&pageSize=10&contentTypeId=12&arrange=C"
```

여러 테마를 조합하려면 `themes`를 반복해서 전달한다.

```bash
curl -X POST \
  -H "X-Admin-Token: [REDACTED]" \
  "http://localhost:8080/api/admin/tourism-contents/sync/personalized-top?themes=NATURE&themes=FOOD&limit=20&pageSize=10&contentTypeId=12&arrange=C"
```

수집 후 맞춤 추천 API에서 해당 지역의 관광지 카드가 채워졌는지 확인한다.

```bash
curl "http://localhost:8080/api/recommendations/personalized?themes=NATURE&limit=20"
```

## 추천 후보 지역 사전 수집

추천 점수가 바뀌어도 관광지 카드 이미지가 최대한 유지되도록, 가성비 추천과 테마별 맞춤 추천 후보 지역을 합쳐 미리 수집한다.

운영 환경에서는 이 API를 수동으로 호출하거나, 이후 배치 작업에서 주기적으로 호출한다. 추천 조회 API는 외부 TourAPI를 직접 호출하지 않고 DB에 저장된 관광정보만 조회한다.

```bash
curl -X POST \
  -H "X-Admin-Token: [REDACTED]" \
  "http://localhost:8080/api/admin/tourism-contents/sync/recommendation-candidates?limit=100&pageSize=10&contentTypeId=12&arrange=C"
```

`themes`를 생략하면 모든 맞춤 추천 테마를 각각 조회해 후보 지역을 수집한다. 특정 테마만 보강하려면 `themes`를 전달한다.

```bash
curl -X POST \
  -H "X-Admin-Token: [REDACTED]" \
  "http://localhost:8080/api/admin/tourism-contents/sync/recommendation-candidates?themes=NATURE&themes=FOOD&limit=100&pageSize=10&contentTypeId=12&arrange=C"
```

Postman에서는 `POST` 메서드로 같은 URL을 입력하고, `Headers`에 `X-Admin-Token`, `Params`에 `limit`, `pageSize`, `contentTypeId`, `arrange`, 필요한 경우 `themes`를 추가한다. Body는 비워둔다.
