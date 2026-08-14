# 결제 환불 요청/승인 (refund/request) — Design

## 개요

결제 완료(`PAID`) 건에 대한 환불을 "요청 → 승인/거절"이라는 별도 절차로 다룬다. 지금까지 유일한
환불 경로는 `team/deadline-check`가 공구팀 미성사를 감지했을 때 시스템이 자동으로 실행하는 전액
환불뿐이었다(`docs/dev/payment/portone/design.md`) — 이 기능은 그 스코프를 "구매자가 자발적으로
원해서 환불을 요청하는" 경로로 확장한다.

생성 경로는 두 가지이며 서로 겹치지 않는다:

1. **공구팀 참여 취소가 자동 생성** (`team/leave` → `TeamService.leave` → `RefundRequestService.
   createFromTeamLeave`) — 팀이 딸린 결제(`payment.team != null`) 전용, 사유 입력 없음("참여
   취소"가 곧 사유). 상품별 "참여 취소 시 자동 환불"(`product.auto_refund_on_cancel`) 설정이 켜져
   있으면 판매자 승인 없이 즉시 승인·처리되고, 꺼져 있으면 대기(`PENDING`) 상태로 남아 판매자
   승인을 기다린다.
2. **구매자 직접 요청** (`POST /api/payments/{paymentId}/refund-requests` →
   `RefundRequestService.createDirect`) — 솔로 구매(`payment.team == null`) 건에만 허용, 사유 입력
   필수. 이미 배송됐을 수 있어 자동 환불 설정과 무관하게 항상 판매자 승인/거절 절차를 거친다.

## 매우 중요한 제약 — 팀 결제는 SUCCESS 전환 후 어떤 경로로도 환불 불가

**팀이 딸린 결제(`payment.team != null`)의 환불은 오직 경로 1(참여 취소)로만 일어난다.** 사용자가
명시적으로 확정한 악용 방지 규칙이다 — 예를 들어 2인 목표 팀에 한 사람이 계정 2개로 결제한 뒤
하나만 환불하면, 실질적으로 1인 결제인데 2인 구간 할인가로 사는 셈이 되기 때문이다.

이 제약은 서로 독립된 두 코드 가드의 조합으로 성립한다(어느 한쪽만으로는 불충분, 반드시 둘 다):

- **가드 A** — `TeamService.leave()`: 팀 상태가 `RECRUITING`이 아니면(`SUCCESS`/`FAILED`)
  `TEAM_NOT_RECRUITING`(409)으로 거절한다. 팀이 정원을 채워 `SUCCESS`로 전환된 뒤에는 그 팀의
  어떤 참여자도 더는 참여를 취소할 수 없다 — 경로 1(참여 취소로 인한 환불요청 자동 생성) 자체가
  막힌다.
- **가드 B** — `RefundRequestService.createDirect()`: `payment.getTeam() != null`이면(소유권 확인
  이후, 다른 어떤 조건보다도 먼저) `TEAM_PAYMENT_REFUND_NOT_ALLOWED`(409)로 무조건 거절한다. 팀
  결제는 팀 상태(RECRUITING/SUCCESS/FAILED)와 무관하게 이 경로 자체를 탈 수 없다 — 경로 2가
  원천적으로 팀 결제를 대상으로 하지 않는다.

판매자 승인/거절 API(`approve`/`reject`)는 `payment.team`을 직접 재검사하지 않고 "이미 존재하는
`PENDING` `RefundRequest`"만 처리한다. 이게 안전한 이유는 **팀 결제에 대한 `PENDING`
`RefundRequest`가 생성되는 유일한 경로가 `createFromTeamLeave()`뿐이고, 그 메서드는
`TeamService.leave()` 한 곳에서만 호출되기 때문**이다 — 위 가드 A·B로 생성 경로 자체가 막혀
있어, SUCCESS 팀의 결제에 대해 승인 API를 호출해도 애초에 승인할 `PENDING` 요청이 존재하지
않는다(`REFUND_REQUEST_NOT_FOUND`).

> 예외적으로 참여 취소(RECRUITING 시점)로 생긴 `PENDING` 요청이 아직 판매자 결정 전인 상태에서,
> 그 사이 다른 사람이 빈 자리를 채워 팀이 SUCCESS로 전환되고, 그 뒤에 판매자가 그 요청을 승인하는
> 시나리오는 실행 가능하다. 이건 위반이 아니다 — 이미 `leave()`가 그 사람의 참여를 제거하고
> `currentCount`를 감소시켰으므로, 그 사람은 SUCCESS로 전환된 팀의 "현재 참여자"가 아니라서
> 위에서 말한 악용(인원수 대비 저가 구간 유지)이 성립하지 않는다.

## API / 인터페이스

- `POST /api/payments/{paymentId}/refund-requests` — 솔로 구매 건 직접 요청
- `POST /api/refund-requests/{refundRequestId}/approve` — 판매자 승인
- `POST /api/refund-requests/{refundRequestId}/reject` — 판매자 거절(사유 템플릿)
- `GET /api/buyer/mypage/refund-requests`, `GET /api/seller/mypage/refund-requests` — 조회
- 참여 취소로 인한 자동 생성은 별도 엔드포인트 없음(`POST /api/teams/{teamId}/leave`의 부수효과)
- 상세 요청/응답/에러: `docs/api/refund.md`, `docs/api/team.md`(leave), `docs/api/mypage.md`

## 데이터 모델

- `refund_request` — 상세: `docs/db/refund_request.md`
- `product.auto_refund_on_cancel`(상품 단위 설정) — 상세: `docs/db/product.md`
- 관계: `refund_request.payment_id → payment.id`, `refund_request.requester_id → member.id`

## 상태 전이

```
(생성) PENDING --판매자 approve()/자동환불 approve()--> APPROVED --(AFTER_COMMIT)--> PortOne 취소 실행
   |                                                                                  (payment/portone 재사용)
   +--판매자 reject(rejectionReason)--> REJECTED (payment는 PAID 그대로 유지)
```

- `APPROVED` 전환 시점에는 DB 상태만 바뀐다 — 실제 PortOne 결제취소 호출은
  `RefundRequestApprovedEvent`를 통해 그 트랜잭션이 커밋된 이후(`AFTER_COMMIT`)에만 실행된다
  (`TeamService.leave()`가 팀 row 비관적 락을 쥔 채로 자동환불 승인을 호출하는 경우가 있어서, 락이
  풀린 뒤에만 외부 HTTP를 호출해야 함).
- 그 뒤 결제 상태 전이는 기존 `payment/portone`의 취소 실행 경로(`PaymentCancellationExecutor`)를
  그대로 탄다 — `PAID → REFUNDED`(즉시) 또는 `PAID → REFUND_PENDING → REFUNDED`(비동기,
  `Transaction.Cancelled` 웹훅 대기). 상세: `docs/dev/payment/portone/design.md`.

## 규칙 / 검증

- `createDirect`: 로그인 필요(`UNAUTHORIZED`), `BUYER`만(`FORBIDDEN`), 본인 결제만(`FORBIDDEN`),
  팀 결제 거절(`TEAM_PAYMENT_REFUND_NOT_ALLOWED`, 409 — 위 "매우 중요한 제약" 참고), `PAID` 상태만
  (`PAYMENT_NOT_REFUNDABLE`, 409), 같은 결제에 대기 중인 요청이 이미 있으면 거절
  (`REFUND_REQUEST_ALREADY_EXISTS`, 409), `reason` 필수(`VALIDATION_FAILED`, 400).
- `createFromTeamLeave`: `TeamService.leave()`가 같은 트랜잭션에서만 호출(공개 메서드지만 실제
  호출자는 하나뿐). `reason`은 항상 `null`. 상품의 `autoRefundOnCancel`이 켜져 있으면 저장 즉시
  `approve()` + `RefundRequestApprovedEvent` 발행까지 이 메서드 안에서 끝낸다.
- `approve`/`reject`: `SELLER`만(`FORBIDDEN`), 본인 상품에 대한 요청만(`FORBIDDEN`,
  `findWithOwnerCheck`로 `payment.product.seller` 확인), `PENDING` 상태만
  (`REFUND_REQUEST_ALREADY_DECIDED`, 409). `reject`는 자유 텍스트가 아니라 `RefundRejectionReason`
  enum(`ALREADY_SHIPPED`/`ALREADY_USED`/`POLICY_VIOLATION`/`OTHER`) 중 하나만 허용
  (`VALIDATION_FAILED`, 400).
- 참여 취소 시점 정원 반환과 환불 요청 생성은 분리된 절차다 — 자리는 `leave()` 즉시 반환되지만,
  실제 돈이 오가는 환불은(자동환불 설정이 없으면) 판매자 승인을 기다린다.

## 구매자 환불 불가 명시적 확인 (프론트, C)

팀이 SUCCESS로 전환되면 위 제약 때문에 환불이 불가능해진다는 사실을, 팀 신설/참가 이전에 구매자가
체크박스로 명시적으로 확인해야만 진행할 수 있게 한다(단순 안내 문구만으로는 부족하다는 사용자
확인 사항).

- `product.html`의 `#refund-notice-checkbox` — 체크 전까지 "신규 팀 신설하기"(`create-team-btn`)와
  각 팀의 "참가하기"(`.team-item-join-btn`) 버튼이 비활성 상태를 유지한다(`product.js`의
  `updateCreateTeamButtonState`/`updateJoinButtonsState`, 기존 "목표 인원 선택 전 신설 버튼
  비활성" 가드와 동일한 패턴).
- "혼자 구매하기"는 이 게이트와 무관하다(솔로 구매는 이 환불 불가 제약의 대상이 아니라서).
- 이 확인은 프론트엔드 UI 가드뿐이다(백엔드가 "확인함" 플래그를 별도로 저장·검증하지는 않음) —
  계획 문서가 요구한 범위(신설/참가 버튼 게이팅)에 한정된 설계다.

## 관련 코드 위치

- `entity/{RefundRequest,RefundRequestStatus,RefundRejectionReason}.java`
- `repository/RefundRequestRepository(+Custom/Impl).java` — QueryDSL. `findByIdWithPaymentAndProduct`
  (단건, 소유권 확인용), `findAllByRequesterIdWithPaymentAndProduct`/`findAllBySellerIdWithPaymentAndProduct`
  (마이페이지 목록). 3단계 경로(`refundRequest.payment.product.seller`)는 QueryDSL 기본
  `PathInits`(DIRECT2, 2단계까지만 자동 초기화) 제약으로 그대로 체이닝하면 NPE가 나서, 별도
  `QProduct` alias로 우회했다(단건 조회는 seller까지 fetch join하지 않고 지연로딩 1회로 대체 — N+1
  아님).
- `service/RefundRequestService.java` — `createDirect`/`createFromTeamLeave`/`approve`/`reject`
- `service/PaymentCancellationExecutor.java` — 실제 PortOne 취소 실행(공유, `docs/dev/payment/
  portone/design.md`에서 상세)
- `event/{RefundRequestApprovedEvent,RefundRequestApprovedEventListener}.java` — 승인/자동환불 →
  `AFTER_COMMIT` → `PaymentCancellationExecutor` 호출
- `controller/RefundRequestController.java`
- `dto/{RefundRequestCreateRequest,RefundRequestRejectRequest,RefundRequestResponse}.java`
- `entity/Product.java` — `autoRefundOnCancel` 필드(상품 단위 자동환불 설정)
- `entity/GroupBuyTeam.java` — `decreaseParticipant()`/`changeLeader()`(참여 취소 지원, 상세는
  `docs/dev/team/crud/design.md`)
- `service/TeamService.java` — `leave()`(참여 취소, 상세는 `docs/dev/team/crud/design.md`)
- `common/exception/ErrorCode.java` — `TEAM_PAYMENT_REFUND_NOT_ALLOWED`/`PAYMENT_NOT_REFUNDABLE`/
  `REFUND_REQUEST_NOT_FOUND`/`REFUND_REQUEST_ALREADY_DECIDED`/`REFUND_REQUEST_ALREADY_EXISTS`/
  `TEAM_NOT_RECRUITING` 추가
- `BuyerMypageController/Service`, `SellerMypageController/Service` — `refund-requests` 조회 추가
- 프론트: `product.html`/`js/product.js`(환불 불가 확인 체크박스), `seller/products/{new,edit}.html`
  + `js/seller-product-{new,edit}.js`(상품별 자동환불 체크박스), `buyer/mypage.html` +
  `js/buyer-mypage.js`(환불 요청 상태 조회), `seller/mypage.html` + `js/seller-mypage.js`(환불 요청
  목록/승인/거절)
- 테스트:
  - `controller/RefundRequestControllerTest.java`(20케이스) — 직접 요청 성공/팀결제거절/본인아님/
    PAID아님/중복대기중/사유누락/결제없음/판매자거절/비로그인, 승인 성공/본인아님/없음/이미처리/
    구매자거절/비로그인, 거절 성공/사유누락/본인아님/이미처리
  - `controller/TeamControllerTest.java` — 참여 취소(leave) 11케이스(환불 요청 자동생성 포함,
    상세는 `docs/dev/team/crud/design.md`)
  - `service/PaymentCancellationExecutorTest.java` — 대상 있음/대상없음(스킵)/PortOne 실패 격리
  - `event/RefundRequestApprovedEventFlowTest.java` — 판매자 승인 → AFTER_COMMIT → PortOne(목) →
    REFUNDED end-to-end, 상품별 자동환불 설정 → 참여 취소 → 승인 절차 없이 같은 경로로 REFUNDED
  - `controller/BuyerMypageControllerTest.java`/`SellerMypageControllerTest.java` —
    `refund-requests` 목록 성공/스코핑/반대역할403/비로그인401
