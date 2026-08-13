# 공구팀 결제 가격 산정 기준을 실시간 인원수 → 목표 인원(정원)으로 변경

대상: payment/crud (`docs/dev/payment/crud/design.md`가 이 기능의 SSOT)
담당: 미정 (Generate 시작 전 배정 필요)

## 배경 / 요구

현재 `PaymentService.create()`는 공구팀 결제 요청을 접수할 때, **결제 요청이 들어온 그 순간**의
`team.getCurrentCount()`(실시간 참여 인원)를 기준으로 `PriceTier`를 조회해 단가를 확정한다
(`resolveTeamPrice(Product, GroupBuyTeam)`). 그 결과 같은 팀 안에서도 먼저 결제한 사람과 나중에
결제한 사람이 서로 다른 단가로 확정되고, 이후 인원이 더 늘어도 이미 확정된 결제에 차액을 돌려주는
로직이 없어 형평성 문제가 있다(사용자와 코드를 직접 조사해 확인).

이번 계획의 목표는 **가격 계산 기준 하나만** 바꾸는 것이다: 결제 시점의 실시간 인원수 대신, 그 팀의
목표 인원(정원, `GroupBuyTeam.maxParticipants`)을 기준으로 `PriceTier`를 조회한다. 이렇게 하면 같은
팀에 속한 모든 결제자가 언제 결제하든(1번째든 마지막이든) 항상 동일한 금액을 낸다.

## 스코프

### 포함
- `PaymentService.resolveTeamPrice(Product, GroupBuyTeam)`가 tier 조회에 쓰는 기준값을
  `team.getCurrentCount()`에서 `team.getMaxParticipants()`로 바꾼다(`create()`가 이 메서드를
  호출하는 지점, `PaymentService.java` 약 66~72행 / `resolveTeamPrice` 약 183~194행).
- 관련 문서(`docs/dev/payment/crud/design.md`, `docs/api/payment.md`)의 "금액 계산" 설명을 새 기준에
  맞게 갱신한다(Evaluate 통과 후 design.md 갱신 시).
- 같은 팀 내에서 여러 시점에 결제해도 항상 동일 금액이 찍히는지 검증하는 테스트를 추가한다.
- **프론트 안내 문구 수정 (기능 변경 없음, 텍스트만)**:
  - `checkout.html:59-61`의 "공구팀 결제는 결제 시점의 팀 인원 수를 기준으로 서버가 최종 금액을
    확정합니다"라는 문구를 새 기준("팀의 목표 인원(정원)을 기준으로 확정")에 맞게 고친다.
  - `seller/products/new.html:108` 및 `seller/products/edit.html`의 동일 안내 문구("모집 인원
    구간별 1인당 가격")를, 이 구간이 "한 팀 안에서 인원이 늘어남에 따라"가 아니라 "상품의 정원
    자체를 판매자가 바꿀 때"에만 다른 구간이 적용된다는 점이 드러나도록 보강한다.
  - 조사 결과 이 두 곳 외에 삭제·신규 구현이 필요한 프론트 코드는 없음을 확인했다(실시간 가격
    인하 알림, "현재 적용 구간" 하이라이트 등의 기능이 원래 존재하지 않았음). 판매자의 다구간
    가격 입력 UI(`seller-product-new.js:86-148`)는 상품 정원을 나중에 수정하면 새로 생성되는
    팀에 다른 구간이 적용될 수 있어(`ProductService.java:100-104`) 여전히 유효하므로 유지한다.

### 제외 (이번 계획에서 손대지 않음)
- **결제 확정 방식**: `create()`(요청 접수, `PENDING`) → `confirm()`/웹훅(PortOne 재조회 후 확정)
  2단계 흐름은 그대로 유지한다. 참여 즉시 결제(요청 접수)되는 현재 UX도 바꾸지 않는다.
  (`docs/dev/payment/portone/design.md`)
- **미성사(마감 초과) 자동환불**: `TeamDeadlineService.processDeadline` → `TeamPaymentsRefundRequestedEvent`
  → `PaymentRefundService`로 이어지는 전액 환불 흐름은 그대로 유지한다.
  (`docs/policy/refund-trigger.md`, `docs/dev/payment/portone/design.md`)
- **정원(`TEAM_FULL`) 판정**: `PaymentService.requireRoomOrAlreadyJoined()`와 `TeamService.join()`이
  쓰는 `currentCount` 기반 정원 초과 방어 로직은 그대로 유지한다 — 이건 "가격"이 아니라 "자리가
  남아있는지"를 보는 별개 관심사라 스코프 밖.
- **팀 성사(`RECRUITING → SUCCESS`) 판정 로직**: `GroupBuyTeam.increaseParticipant()`의
  `currentCount == maxParticipants` 체크는 그대로 유지한다. (`docs/policy/team-success-criteria.md`)
- **`Product.maxParticipants`/`PriceTier` 데이터 자체**: 상품 등록·수정 시 정원값·구간표를 입력하는
  방식은 바꾸지 않는다.
- **과거에 이미 확정된 Payment row에 대한 소급 정정·마이그레이션**: 아래 "기존 데이터 영향" 참고.

## 조사한 사실 (설계의 근거)

- `GroupBuyTeam.maxParticipants`는 팀 생성 시점(`TeamService.create()`, `TeamService.java` 64행)에
  `product.getMaxParticipants()` 값을 복제해 저장되고, 그 이후 이 값을 바꾸는 setter/메서드가
  엔티티에 없다(`GroupBuyTeam.java` 확인) — 즉 한 팀의 생애 동안 고정값이다. 상품을 나중에 수정
  (`PUT /api/products/{id}`)해 `Product.maxParticipants`가 바뀌어도, 이미 만들어진 `GroupBuyTeam`의
  값은 그 영향을 받지 않는다(복제된 스냅샷이라 연결이 끊어져 있음).
- 따라서 `team.getMaxParticipants()`를 가격 산정 기준으로 쓰는 것은 `currentCount`와 달리 **동시
  갱신 경합이 없는 불변값 읽기**다 — 이번 변경 자체에는 추가적인 동시성 제어가 필요하지 않다.
  (참고로 `currentCount`는 `team/join`의 참가 동시성 제어 대상이지만, 그 값 자체를 가격 계산에서
  더 이상 쓰지 않게 되므로 이번 변경과는 무관해진다.)
- `Payment.amount`는 `create()` 시점에 계산되어 저장된 뒤 다시 계산되지 않는다. `confirm()`/
  `confirmByPgPaymentId()`(`applyVerificationResult`)는 PortOne 재조회 결과의 금액과 **이미 저장된
  `payment.amount`가 일치하는지 대조**만 할 뿐, `amount`를 재계산하지 않는다(`PaymentService.java`
  96~172행). 그러므로 이번 변경은 신규로 `create()`되는 Payment row부터만 적용되며, 기존에 이미
  저장된(과거 다른 기준으로 확정된) Payment row는 그대로 유지된다.
- `PaymentControllerTest`의 기존 tier 관련 케이스(`create_team_success_appliesTierPrice`,
  `create_success_forExistingParticipant_evenIfTeamFull`)를 코드로 추적한 결과, 두 케이스 모두
  fixture상 `currentCount`와 `maxParticipants`가 같거나(정원이 다 찬 팀) tier 구간이 두 값 사이에
  추가로 걸리지 않는 값이라, 이번 변경 후에도 **결과값(금액)이 그대로 나올 것으로 보인다** — 다만
  이는 코드 추적 기반 추정이며, Generate 단계에서 실제 실행으로 재확인이 필요하다.

## 접근 (설계 방향)

- `resolveTeamPrice(Product, GroupBuyTeam)`가 `PriceTierRepository.findByProductIdOrderByMinCountAsc`로
  가져온 구간을 순회할 때, 각 구간의 `minCount`와 비교하는 대상을 `team.getCurrentCount()`에서
  `team.getMaxParticipants()`로 바꾼다. 나머지 순회 로직(오름차순 순회, 만족하는 마지막 구간 채택,
  만족하는 구간이 없으면 `basePrice` 유지)은 그대로 둔다 — "무엇과 비교하느냐"만 바뀐다.
- `create()`가 `resolveTeamPrice`를 호출하는 지점, 인자 전달 방식은 바뀌지 않는다(이미 `team` 객체
  전체를 넘기고 있으므로 메서드 시그니처 변경도 필요 없어 보인다 — 다만 시그니처를 유지할지 여부는
  Generate 단계의 구현 판단으로 남긴다).
- `requireRoomOrAlreadyJoined()`(정원 초과 방어)는 `currentCount` 기반을 유지하므로 이 메서드는
  건드리지 않는다.

## 영향 범위 (계층)

- **service**: `service/PaymentService.java` — `resolveTeamPrice()` (그리고 이를 호출하는 `create()`)
- **테스트**: `controller/PaymentControllerTest.java` — 신규 시나리오 추가 + 기존 tier 관련 케이스
  회귀 확인
- **문서(SSOT/명세)**: `docs/dev/payment/crud/design.md`(금액 계산 규칙 문구), `docs/api/payment.md`
  (`POST /api/payments` 설명 중 "현재 current_count 기준 가격 구간 적용" 문구)
- **프론트(텍스트만)**: `checkout.html:59-61`, `seller/products/new.html:108`,
  `seller/products/edit.html`(동일 안내 문구) — 구조/스크립트 변경 없이 안내 문구만 수정
- 영향 없음(확인함): `entity/{Payment,GroupBuyTeam,PriceTier}.java`, `repository/PriceTierRepository.java`,
  `controller/PaymentController.java`, `TeamService.java`, `PaymentRefundService.java`,
  `TeamDeadlineService.java`, `product.js`(가격 구간표 렌더링), `checkout.js`(PortOne amount 전달) —
  전부 스키마·API 계약·다른 흐름 변경 없이 `resolveTeamPrice` 내부 비교 기준 하나만 바뀌는 변경이라
  코드 변경이 필요하지 않을 것으로 보인다(조사로 확인: PortOne 결제 금액과 영수증 표시는 서버
  응답값을 그대로 쓰고 프론트가 재계산하지 않음).

## 관련 문서와의 정합성 확인

- `docs/db/price_tier.md`: "사용하는 기능" 절에 `payment/create (구간별 가격 계산)`이라고만 적혀 있고
  기준이 `currentCount`인지 `maxParticipants`인지는 이 문서에 명시돼 있지 않다 — 표/컬럼 정의 자체는
  수정할 내용이 없다. (스키마 변경 없음)
- `docs/dev/payment/crud/design.md`: "개요"와 "규칙/검증" 절에 명시적으로
  `현재 current_count 시점 기준으로 price_tier를 조회`한다고 적혀 있어 이번 변경으로 **직접 갱신
  대상**이다(Evaluate 통과 후 SSOT 갱신 시 반영).
- `docs/api/payment.md`: `POST /api/payments` 설명에 "현재 `current_count` 기준 가격 구간 적용"이라는
  문구가 있어 이 역시 갱신 대상이다.
- `docs/policy/team-success-criteria.md`: 이 정책의 "규칙"(정원 도달 시 `RECRUITING → SUCCESS` 전환)
  자체는 이번 변경과 무관해 바뀌지 않는다. "근거" 절에 "정원이 다 차는 순간 가격(베스트 공구가)이
  확정되므로"라는 문구가 있는데, 이는 상태 전환 시점을 설명하는 근거일 뿐 가격 **계산 기준**을
  규정하는 문장은 아니라서 이번 변경과 직접 충돌하지는 않는 것으로 판단된다 — 다만 문구가 옛 가격
  모델(정원 도달 전까지는 미확정)을 전제로 쓰인 것이라, Evaluate 이후 필요하면 문구만 다듬는다(정책
  "규칙" 자체는 변경 없음).
- `docs/policy/refund-trigger.md`, `docs/dev/payment/portone/design.md`: 미성사 환불·PortOne 확정
  흐름은 `Payment.amount`(이미 저장된 값)를 그대로 취소/대조하는 구조라 이번 변경과 무관하다(영향 없음
  확인).

## 기존 데이터(과거 Payment row) 영향

- 마이그레이션 없음. `Payment.amount`는 생성 시점에 고정 저장되고 이후 재계산되지 않으므로, 이번
  변경은 **신규로 생성되는 결제부터만** 적용된다. 이미 다른 기준으로 확정된 과거 Payment row(다른
  구매자가 이미 다른 단가로 `PAID` 확정된 건)를 소급해서 정정하거나 차액을 환불하는 로직은 이번
  계획의 범위가 아니다(사용자 확인 필요 시 별도 계획으로 분리).

## 평가(통과) 기준

1. **신규 테스트**: 정원 N, price tier 여러 구간이 설정된 상품에서, 서로 다른 `currentCount` 시점
   (예: 팀장 참가 시점 `currentCount=1`, 중간 시점 `currentCount=k`(1<k<N), 정원이 다 찬 시점
   `currentCount=N`)에 각각 결제를 생성했을 때, **세 결제의 `amount`가 모두 동일**하고 그 값이
   "`minCount <= maxParticipants`를 만족하는 구간 중 가장 큰 구간의 가격"과 일치하는지 검증한다.
2. **회귀 확인**: 기존 `PaymentControllerTest`의 모든 케이스(혼자구매, 기존 tier 케이스 2건,
   `TEAM_FULL`/`ALREADY_JOINED` 방어, 각종 404/403/401)가 그대로 통과하는지 확인한다. 특히 기존 tier
   케이스 2건은 "조사한 사실"에서 결과가 바뀌지 않을 것으로 추정했지만 실제 실행으로 재확인한다.
3. `./gradlew test` 전체 통과.
4. **프론트 문구 확인(수동)**: `checkout.html`, `seller/products/new.html`/`edit.html`의 안내 문구가
   새 가격 기준을 정확히 설명하는지 육안으로 확인한다(자동 테스트 대상 아님).

## 리스크 / 전제

- 사용자 경험 변화(의도된 것): 정원이 큰 상품에서 팀 초반에 참가·결제한 사람이 이전보다 더 낮은(또는
  다른) 금액을 내게 될 수 있다 — 팀 전체가 항상 "목표 인원 기준 단가"로 통일되기 때문이며, 이번
  계획의 목적 자체다.
- `PriceTier`에 `team.maxParticipants`와 정확히 일치하는 `minCount` 구간이 없어도 되는 기존 동작
  (만족하는 가장 큰 구간 채택, 없으면 `basePrice` 유지)은 그대로 유지된다 — 이 부분의 동작 변경은
  없다.
- 이번 변경은 결제 확정(`confirm`/웹훅)·환불(`refund`) 트랜잭션·이벤트 구조를 전혀 건드리지 않는다.
