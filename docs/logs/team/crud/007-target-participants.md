# 007-target-participants — 공구팀 신설 시 목표 인원(price_tier) 선택 (로그)

## Attempt 1 — 2026-08-13  ✅ PASS(구현 완료, Evaluate는 별도 단계)
- 시도: 승인된 계획(`docs/dev/ongoing/team-target-participants.md`)대로 구현.
  - `ErrorCode.INVALID_TARGET_PARTICIPANTS`(400) 추가.
  - `TeamCreateRequest`(`@NotNull Integer targetParticipants`) 신규 DTO 추가.
  - `TeamController.create()`가 `@Valid @RequestBody TeamCreateRequest`를 받도록 변경.
  - `TeamService.create()`에 `PriceTierRepository`를 생성자 주입 추가. `productId`로
    `findByProductIdOrderByMinCountAsc` 조회 → `minCount` 목록에 `targetParticipants`가
    존재하는지 검증 → 없으면 `INVALID_TARGET_PARTICIPANTS`, 있으면 그 값으로
    `GroupBuyTeam.maxParticipants`를 설정(기존 `product.getMaxParticipants()` 대체).
  - 프론트(`product.html`/`product.js`): `price_tier.minCount`별 라디오 버튼 그룹(`target-participants-*`)을
    추가해 상세 조회 응답의 `priceTiers`로 동적으로 채운다. 아무것도 선택 안 하면
    `create-team-btn`이 `disabled` 상태를 유지하고, 선택 시 그 값을 `targetParticipants`로
    `POST /products/{id}/teams` body에 담아 보낸다. `components.css`에 라디오 그룹 최소 스타일 추가.
  - `TeamControllerTest`: 기존 `create_success`/`create_forbidden_seller`/`create_unauthorized`/
    `create_productNotFound` 4케이스에 `price_tier` 픽스처(`savePriceTiers`)와 요청 body(`toJson`,
    `ObjectMapper` — `PaymentControllerTest`와 동일한 `tools.jackson.databind.ObjectMapper` import 패턴
    사용) 추가. 신규 `create_invalidTargetParticipants`(목록에 없는 값 → 400
    `INVALID_TARGET_PARTICIPANTS`), `create_missingTargetParticipants`(필드 누락 → 400
    `VALIDATION_FAILED`) 케이스 추가. `create_success`에서 선택값(5)이 생성된
    `GroupBuyTeam.maxParticipants`와 일치하는지 응답 jsonPath + 리포지토리 조회 양쪽으로 확인.
  - `Product.maxParticipants`/DB 스키마는 계획대로 건드리지 않음. `PaymentService`/
    `PaymentControllerTest`/`ProductControllerTest`도 계획에서 "수정 불필요" 확인된 대로 손대지 않음.
- 결과:
  - `./gradlew compileJava`, `./gradlew compileTestJava` 모두 성공.
  - `./gradlew test` 전체: 203개 중 199개 통과, 4개 실패
    (`ProductControllerTest.list_publicAccess`, `ProductCachingTest`의 3케이스).
    이 4개는 이번 변경과 무관한 **기존 환경 이슈**로 판단됨 — 근거: (1) 이번 시도에서
    `Product`/`ProductController`/`ProductService`/캐싱 관련 코드는 전혀 건드리지 않았음,
    (2) `ProductControllerTest`만 단독 실행(`--tests`)해도 동일하게 재현됨(다른 테스트와의
    상호작용 문제가 아님), (3) 테스트가 로컬 실제 MySQL(`gong9ri_db`, `src/test/resources/application.yaml`)에
    접속하도록 구성돼 있어, `content[0]`처럼 순서에 의존하는 단언이 DB에 남아있는 이전 데이터에
    따라 흔들리는 것으로 보임(H2 등 격리된 테스트 DB가 아님). `git stash`로 원상태와 직접 비교는
    권한 정책상 차단되어 못 했음 — 단정하지 않고 "이번 변경과 직접 연관은 없어 보인다"로만 기록.
  - `TeamControllerTest`(신규 3케이스 포함 전체)와 `PaymentControllerTest`만 지정 실행하면
    둘 다 전부 통과(`BUILD SUCCESSFUL`) — 계획의 평가 기준(신설 API 계약 변경, 결제 tier 가격
    로직 무영향)과 일치.
- 증거(API 계약 기준, MockMvc):
  - `POST /api/products/{id}/teams` body `{"targetParticipants":5}`(product의 price_tier
    minCount가 2/5/10) → `201 {"data":{"currentCount":1,"maxParticipants":5,"status":"RECRUITING"}}`,
    저장된 `GroupBuyTeam.maxParticipants == 5`.
  - 같은 상품에 `{"targetParticipants":3}`(목록에 없음) → `400 {"code":"INVALID_TARGET_PARTICIPANTS"}`.
  - body 없이(`{}`) → `400 {"code":"VALIDATION_FAILED"}`.
- 다음: Evaluate 단계에서 `./gradlew test` 재확인 및 위 4개 실패가 이번 변경과 무관한지 최종
  판정, design.md 갱신 + ongoing→changes 이동은 Evaluate 몫.

## Evaluate — 2026-08-13  ✅ PASS

- 결과:
  - `./gradlew test`(전체 스위트) 재실행: 203개 중 199개 통과, 4개 실패
    (`ProductControllerTest.list_publicAccess`, `ProductCachingTest`의 3케이스) — generator 보고와 동일.
  - **독립 재검증(`git stash`로 이번 변경분만 제외 후 baseline 비교)**: 이번 작업 관련 파일 전부
    (`ErrorCode.java`, `TeamController.java`, `TeamService.java`, `TeamCreateRequest.java`,
    `TeamControllerTest.java`, `product.html`/`product.js`/`components.css`, `docs/api/team.md`,
    `docs/db/{group_buy_team,price_tier,product}.md`, 이 로그·ongoing 문서)를 `git stash push -u -- <경로들>`로
    스택시켜 작업 트리를 순수 baseline(origin/main 상태)으로 되돌린 뒤 `./gradlew test --tests
    "*ProductControllerTest*" --tests "*ProductCachingTest*"`를 실행 — **동일하게 17개 중 4개 실패, 같은
    테스트명·같은 assertion 라인(`ProductCachingTest.java:95/137/206`, `ProductControllerTest.list_publicAccess`)**.
    즉 이번 Generate가 손대지 않은 순수 baseline에서도 똑같이 재현됨 → **이번 변경과 무관함을 확정**(추정이
    아니라 직접 비교로 검증 완료). `git stash pop`으로 원상 복구 후 작업 트리가 다시 정확히 이전 상태임을
    `git status`로 확인.
  - 이전 payment 가격 변경 Evaluate(`docs/logs/payment/crud/002-team-price-by-target-capacity.md`)와 **같은
    4개 테스트·같은 원인**(로컬 실제 MySQL에 누적된 데이터 때문에 순서/개수에 의존하는 단언이 흔들리는 기존
    환경 이슈) — 이번 변경이 새로운 원인을 추가하지 않았다.
  - 스코프 재실행 `./gradlew test --tests "*TeamControllerTest*" --tests "*PaymentControllerTest*"` →
    `BUILD SUCCESSFUL`(신규 3케이스 포함 `TeamControllerTest` 전체, `PaymentControllerTest` 전체 통과) —
    계획의 평가 기준(계약 변경, 결제 tier 가격 로직 무영향)과 일치.
- 추론적 평가(계획·컨벤션 대조):
  - `git diff`로 실제 코드 확인 — `ErrorCode`/`TeamCreateRequest`/`TeamController`/`TeamService`/테스트가
    승인된 계획(`docs/dev/ongoing/team-target-participants.md`)의 태스크 목록과 정확히 일치.
  - **스코프 준수 확인**: `Product` 엔티티·`product` 테이블에 컬럼 추가 없음(`git diff` 대상에
    `Product.java`/스키마 마이그레이션 없음). `product.maxParticipants` 필드 자체는 건드리지 않고 문서상
    의미만 재정의(`docs/db/product.md`) — 계획과 일치. `PaymentService`/`PaymentControllerTest`/
    `ProductControllerTest`도 계획대로 미수정.
  - `docs/code-convention.md` 준수: 생성자 주입(`PriceTierRepository` 추가도 `final` 필드+생성자),
    `@Valid`+Bean Validation(`TeamCreateRequest`), 계층 책임 분리(컨트롤러는 위임만, 검증/생성 로직은
    서비스), 매직넘버 없음.
  - `docs/api/team.md`는 이미 planner 단계에서 갱신돼 있었고, 실제 구현(요청 body 필드명·에러코드·응답
    형식)과 정확히 일치함을 재확인.
- 통과 시 후속 조치 수행:
  - `docs/dev/team/crud/design.md`에 "팀 신설 시 목표 인원 선택" 규칙과 관련 코드 위치(`TeamCreateRequest`,
    `PriceTierRepository`, `INVALID_TARGET_PARTICIPANTS`) 반영해 SSOT 갱신.
  - `docs/dev/ongoing/team-target-participants.md` → `docs/dev/team/crud/changes/002-target-participants.md`로
    채번 이동(기존 최대 번호 001 다음).
- 최종 판정: **PASS**.
