# 003-price-tier-min-count-validation — 가격 구간 최소 인원 서버 검증 추가 (로그)

## Attempt 1 — 2026-08-19  ✅ PASS
- 시도: `PriceTierRequest.minCount`에 `@Min(2)` 추가(`@NotNull`과 함께). `ProductRegisterRequest.priceTiers`가 이미 `@Valid`로 선언돼 있어 리스트 각 원소에 검증이 그대로 캐스케이드됨(컨트롤러/서비스 변경 불필요). `docs/api/product.md`의 `priceTiers[].minCount` 설명에 "2 이상" 명시. `ProductControllerTest`에 `minCount=1`로 등록 시 `400 VALIDATION_FAILED`를 확인하는 테스트 추가.
- 결과: `./gradlew test --tests "com.gong9ri.gong9ri.controller.ProductControllerTest"` — 27개 중 24개 통과, 3개 실패. 신규 테스트(`priceTiers의 minCount가 2 미만이면 400 VALIDATION_FAILED`)는 통과.
- 원인(3개 실패는 본 작업과 무관): `bestPrice`/`category` 필터/`DEADLINE` 정렬 검증 3건 실패는 변경 전(`git stash`로 원복 후 재실행) 동일하게 발생하는 기존 결함 — Redis 캐시 등 테스트 간 상태 공유로 인한 격리 문제로 추정, 이번 작업(minCount 검증) 범위 밖이라 그대로 둠. 전체 스위트(`./gradlew test`)에서도 같은 3건 + `ProductCachingTest` 3건(동일하게 stash 전후 동일 발생)만 실패, 그 외 351개 통과.
- 증거(API 샘플):
  - `POST /api/products` body에 `priceTiers: [{"minCount": 1, "price": 22000}]` 포함 → `400 {"success":false,"code":"VALIDATION_FAILED",...}`
  - `POST /api/products` body에 `priceTiers: [{"minCount": 2, "price": 22000}]` 포함 → `201 Created` (기존 정상 케이스 회귀 없음)

## Attempt 2 — 2026-08-19  ✅ RESOLVED (Attempt 1의 원인 추정 정정)

- 배경: Attempt 1에서 "Redis 캐시 등 테스트 간 상태 공유로 인한 격리 문제로 추정"이라고 남긴 추정이 **틀렸음**을 뒤이은 조사로 확인함. 정정 기록.
- 검증: `ProductControllerTest`에 캐시(`CacheManager`)를 매 테스트 전에 비우는 `@BeforeEach`를 추가해 재실행 → 실패 3건이 **한 글자도 안 바뀌고 그대로 재현**됨(같은 파일 줄 번호, 같은 기대값/실제값). 캐시가 원인이었다면 이 조치로 사라졌어야 하므로, 캐시 격리 문제가 아니라는 게 반증됨(해당 `@BeforeEach`는 되돌림, 커밋 안 함).
- 진짜 원인: 로컬 MySQL(`gong9ri_db`, 테스트가 그대로 사용하는 실제 dev DB)에 이전 수동 `bootRun` 세션이 남긴 잔여 데이터가 있었음 — `product` 테이블에 상품 3개(`id=2918~2920`, "유기농 사과 5kg"/"블루투스 이어폰"/"캠핑 텐트 4인용"), 거기 딸린 `price_tier` 5행, `group_buy_team` 1행(`id=704`). `ProductService.list()` 쿼리에 `ORDER BY`가 없어(`docs/dev/product/crud/design.md`에 이미 기록된 특성) 이 잔여 상품들이 테스트가 그 순간 새로 만든 상품과 뒤섞여 순서/개수/최저가 기대값이 흔들렸던 것.
  - 참고: 같은 날 `docs/logs/ai/buyer-chatbot/002-buyer-chatbot-concurrent-timeout-bug.md`에서도 완전히 별개 작업 중 동일한 3+3건 실패를 관찰하고 "로컬 MySQL 잔여 상품 데이터"로 정확히 같은 원인을 지목했었음(그 작업 범위 밖이라 정리는 보류됨) — 이번에 실제로 원인 데이터를 찾아 제거함.
- 조치: 의존관계 순서대로(`group_buy_team` → `price_tier` → `product`) 잔여 행 삭제(사용자 확인 후 진행. 참여자/결제/리뷰/찜/문의 등 다른 테이블에서 이 데이터를 참조하는 행이 0건임을 먼저 확인).
- 결과: `./gradlew test` 전체 재실행 → **358개 전부 통과**(`BUILD SUCCESSFUL`). `ProductControllerTest`/`ProductCachingTest` 실패 6건 전부 해소.
- 교훈: 이 저장소 테스트는 격리된 테스트 전용 DB가 아니라 로컬 dev MySQL을 그대로 쓴다 — 수동으로 `bootRun`해서 만든 상품/팀 데이터는 테스트 실행 전에 정리해야 한다(자동 롤백 대상이 아님, `@Transactional`은 테스트 자신이 그 트랜잭션 안에서 만든 데이터만 되돌린다). 근본적으로는 `list()` 쿼리에 `ORDER BY`를 추가하는 게 재발을 막겠지만, 그건 캐시 무효화 전략(`docs/dev/product/crud/design.md`의 "전체 무효화" 근거가 "어느 페이지인지 특정 불가"에 있음)과 얽혀 있어 별도 계획으로 다뤄야 함 — 이번엔 로컬 데이터 정리로만 해소.
