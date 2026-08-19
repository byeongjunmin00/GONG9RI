# 003-price-tier-min-count-validation — 가격 구간 최소 인원 서버 검증 추가 (로그)

## Attempt 1 — 2026-08-19  ✅ PASS
- 시도: `PriceTierRequest.minCount`에 `@Min(2)` 추가(`@NotNull`과 함께). `ProductRegisterRequest.priceTiers`가 이미 `@Valid`로 선언돼 있어 리스트 각 원소에 검증이 그대로 캐스케이드됨(컨트롤러/서비스 변경 불필요). `docs/api/product.md`의 `priceTiers[].minCount` 설명에 "2 이상" 명시. `ProductControllerTest`에 `minCount=1`로 등록 시 `400 VALIDATION_FAILED`를 확인하는 테스트 추가.
- 결과: `./gradlew test --tests "com.gong9ri.gong9ri.controller.ProductControllerTest"` — 27개 중 24개 통과, 3개 실패. 신규 테스트(`priceTiers의 minCount가 2 미만이면 400 VALIDATION_FAILED`)는 통과.
- 원인(3개 실패는 본 작업과 무관): `bestPrice`/`category` 필터/`DEADLINE` 정렬 검증 3건 실패는 변경 전(`git stash`로 원복 후 재실행) 동일하게 발생하는 기존 결함 — Redis 캐시 등 테스트 간 상태 공유로 인한 격리 문제로 추정, 이번 작업(minCount 검증) 범위 밖이라 그대로 둠. 전체 스위트(`./gradlew test`)에서도 같은 3건 + `ProductCachingTest` 3건(동일하게 stash 전후 동일 발생)만 실패, 그 외 351개 통과.
- 증거(API 샘플):
  - `POST /api/products` body에 `priceTiers: [{"minCount": 1, "price": 22000}]` 포함 → `400 {"success":false,"code":"VALIDATION_FAILED",...}`
  - `POST /api/products` body에 `priceTiers: [{"minCount": 2, "price": 22000}]` 포함 → `201 Created` (기존 정상 케이스 회귀 없음)
