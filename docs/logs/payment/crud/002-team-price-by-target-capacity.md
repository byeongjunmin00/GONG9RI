# 002-team-price-by-target-capacity — 공구팀 결제 가격 산정 기준을 currentCount → maxParticipants로 변경 (로그)

## Attempt 1 — 2026-08-13

- 시도: 계획 문서(`docs/dev/ongoing/payment-team-price-by-target-capacity.md`)에 따라
  `PaymentService.resolveTeamPrice(Product, GroupBuyTeam)`의 tier 조회 비교 기준을
  `team.getCurrentCount()`에서 `team.getMaxParticipants()`로 변경했다(`PaymentService.java`
  `resolveTeamPrice` 메서드, 약 183~196행). 나머지 순회 로직(오름차순 순회, 만족하는 마지막 구간
  채택, 없으면 basePrice 유지)과 `create()`가 이 메서드를 호출하는 지점·인자(`resolveTeamPrice(product,
  team)`)는 계획대로 그대로 두었다. `requireRoomOrAlreadyJoined()`(정원 초과 방어, `currentCount`
  기준)는 계획에서 명시한 대로 손대지 않았다.
- 프론트 안내 문구 2곳 수정(기능 변경 없음, 텍스트만):
  - `checkout.html:59~62`: "결제 시점의 팀 인원 수를 기준으로" → "팀의 목표 인원(정원)을 기준으로"로
    바꾸고, "같은 팀이면 먼저/나중에 결제해도 금액이 항상 동일하다"는 문장을 추가했다.
  - `seller/products/new.html:108~113`, `seller/products/edit.html`(동일 문구): "모집 인원 구간별
    1인당 가격" 문구에, 이 구간이 "한 팀 안에서 인원이 늘어남에 따라"가 아니라 "상품의 정원 자체가
    어느 구간에 속하는지"에 따라 정해지고, 정원을 나중에 수정하면 그 이후 새로 만들어지는 팀부터만
    다른 구간이 적용된다는 설명을 보강했다.
- 신규 테스트 추가: `PaymentControllerTest.create_team_multipleTimestamps_sameAmountRegardlessOfCurrentCount`
  — 정원 10, tier(3→22000, 5→20000, 10→18000)인 상품에서 **같은 팀** 하나를 만들고, currentCount=1
  (팀장만 참가), currentCount=5(중간, 1<5<10), currentCount=10(정원이 다 찬 시점) 세 시점에서 각각
  다른 멤버로 결제를 생성해, 세 결제의 `amount`가 모두 18000(=maxParticipants=10 기준으로 만족하는
  가장 큰 구간의 가격)으로 동일함을 검증했다. 계획의 평가 기준 1번을 충족하는 시나리오다.
- design.md/api 문서 갱신, ongoing→changes 이동은 이번 Generate 범위가 아니므로 진행하지 않았다
  (Evaluate 단계에서 처리).

## Attempt 1 결과 — 2026-08-13 ✅ (컴퓨팅 평가 기준 통과, 최종 Evaluate는 별도 단계)

- `./gradlew compileJava` — 성공.
- `./gradlew test --tests "*PaymentControllerTest*"` — 전체 통과. 신규 테스트 포함, 기존 tier 관련
  케이스(`create_team_success_appliesTierPrice`, `create_success_forExistingParticipant_evenIfTeamFull`)
  도 그대로 통과(계획 문서의 "조사한 사실"에서 추정한 대로 결과값이 바뀌지 않음을 실제 실행으로
  재확인).
- `./gradlew test`(전체) — 191개 중 4개 실패(`ProductControllerTest`, `ProductCachingTest` 3건).
  이번 변경(`git stash`)을 제거한 baseline에서도 **동일하게 4개가 실패**하는 것을 확인했다 — Payment
  관련 변경과 무관한 기존 실패(추정: Redis 미기동 등 로컬 환경 이슈)이며, 이번 계획 범위 밖이라 손대지
  않았다.

## Evaluate — 2026-08-13 ✅ PASS

- **계산적 평가**:
  - `docs/dev-doc-guide.md`, `docs/workflow/evaluate-guide.md`, `docs/code-convention.md`,
    `docs/logs-guide.md`를 먼저 읽고 절차를 따랐다.
  - `./gradlew test`(전체, 로컬 MySQL 기동 확인 후 실행) — **191개 중 4개 실패**
    (`ProductControllerTest` 1건 + `ProductCachingTest` 3건), generator 보고와 동일한 결과.
  - **독립 재검증**: generator가 "baseline에서도 동일하게 4개 실패"라고 주장한 것을 단순히 믿지 않고
    직접 재확인했다. 이번 결제 관련 변경분만(`PaymentService.java`, `checkout.html`,
    `seller/products/{new,edit}.html`, `PaymentControllerTest.java`) `git stash`로 제거한 뒤
    `./gradlew test --tests "*ProductControllerTest*" --tests "*ProductCachingTest*"`를 실행 →
    **동일하게 4개 실패**(17개 중 4개). 이후 `git stash pop`으로 원복.
  - 실패 원인을 gradle 리포트(`build/test-results/test/TEST-com.gong9ri.gong9ri.service.ProductCachingTest.xml`)에서
    직접 확인 — 전부 `AssertionFailedError: expected: <15> but was: <16>` (건수 불일치). Redis가
    아니라 **로컬 MySQL에 이전 테스트 실행에서 누적된 데이터(테스트 간 격리/초기화 미비)로 인한 환경
    이슈**로 판단된다(캐시 자체는 `spring.cache.type: simple`이라 테스트에서 Redis를 쓰지 않음). 이번
    결제 가격 산정 로직 변경과는 무관함을 stash 재검증으로 확인했다.
  - `PaymentControllerTest` 전체(신규 테스트 포함) 통과 재확인.
- **추론적 평가**:
  - `git diff`로 실제 코드 변경을 계획 문서와 대조: `PaymentService.resolveTeamPrice()`가
    `team.getCurrentCount()` → `team.getMaxParticipants()`로 바뀐 것 외에 순회 로직·시그니처·호출
    지점은 그대로 — 계획한 "비교 기준 하나만 바꾸는" 스코프와 정확히 일치. `requireRoomOrAlreadyJoined()`,
    `confirm()`/웹훅/환불 흐름 등 "제외" 항목은 손대지 않음을 확인.
    프론트 문구 수정(`checkout.html`, `seller/products/new.html`, `edit.html`)도 계획에 적힌 문구
    그대로 반영됐고 구조/스크립트 변경 없음.
    신규 테스트(`create_team_multipleTimestamps_sameAmountRegardlessOfCurrentCount`)는 계획의
    평가 기준 1번(currentCount=1/중간/정원 다 찬 시점 세 결제 금액이 모두 동일)을 그대로 검증.
  - `docs/code-convention.md` 준수 확인: 변경이 `service` 계층 내부 비교 기준 1줄 + 주석 추가에
    그쳐 계층 책임 분리(controller/service/repository) 위반 없음, 로깅/트랜잭션/DI 방식 변경 없음.
  - 계획 범위 밖 확장(범위 초과) 없음을 확인.
  - 세션 내 다른(무관한) SNS 로그인 작업(`build.gradle`, `SecurityConfig.java`, `AuthController.java`
    등)이 같은 워킹 디렉터리에서 동시에 변경되고 있는 것을 인지했으나, 이번 결제 변경분과는 완전히
    분리된 파일들이라 이번 평가 대상에서 제외했다(건드리지 않음).
- **결론**: **통과**. `docs/dev/payment/crud/design.md`를 새 가격 기준(목표 인원 `maxParticipants`
  기준, 팀 생애 동안 불변, 동일 팀 내 금액 항상 동일)으로 갱신하고, `docs/api/payment.md`의
  `POST /api/payments` 설명도 갱신했다. `docs/policy/team-success-criteria.md`의 "근거" 절 문구도
  옛 가격 모델 전제를 없애도록 다듬었다(정책 규칙 자체는 변경 없음). `docs/dev/ongoing/
  payment-team-price-by-target-capacity.md`를 `docs/dev/payment/crud/changes/002-team-price-by-target-capacity.md`로
  채번 이동했다.
