# 포트원(PortOne) V2 실결제 연동 (payment/portone) — Design

## 개요

`payment/crud`가 만드는 "결제 요청 접수"(`PENDING`)를 실제 PG(PortOne V2, 샌드박스/테스트 모드,
카카오페이 간편결제만)로 확정·환불까지 연결하는 기능이다. 핵심 원칙은 **클라이언트가 보내는 "성공했다"는
신호를 그대로 믿지 않고, 서버가 PortOne API로 직접 재조회한 결과로만 결제를 확정한다**는 것 — 결제
생성(`payment/crud`) 따로, 확정(이 기능)이 따로다.

이 기능이 다루는 것: ① 결제 확정(`confirm`, 클라이언트 호출 + 웹훅 안전망), ② PortOne 웹훅 수신·서명
검증, ③ 실제 PortOne 결제취소 API 호출·결과 반영을 한 곳(`PaymentCancellationExecutor`)에 모아두고,
공구팀 미성사 자동환불(`team/deadline-check`)과 사용자 자발적 환불 요청(`refund/request` — 참여
취소·판매자 승인/상품별 자동환불)이 이 실행기를 공유한다. **다루지 않는 것**(스코프 밖,
`docs/dev/payment/portone/changes/001-portone.md`): 카드·계좌이체·가상계좌·다른 간편결제사(네이버페이
등), `team/join`과 `payment/create`의 결합 방식 재설계.
**계획 수립 시점(001-portone)엔 "사용자 자발적 결제취소"가 스코프 밖이었으나, 이후 `refund/request`
개념이 이 기능의 취소 실행 경로를 재사용하는 방식으로 그 스코프를 열었다(2026-08-14,
`docs/dev/refund/request/design.md`) — PortOne 취소 API 호출 자체는 여기 한 곳에만 존재하고,
새 트리거(판매자 승인)만 추가된 형태다.
**계획 수립 시점엔 "카드 결제만"으로 스코프를 잡았으나, 실제 포트원 콘솔에 연결된 테스트 채널이
카카오페이(PG Provider: `kakaopay`)로 확인돼 카카오페이 간편결제로 스코프를 정정했다(사용자 승인,
2026-08-12) — 카카오페이는 `payMethod`를 `EASY_PAY`로 보내야 하며 `CARD`로는 결제창이 열리지 않는다.

## API / 인터페이스

- `POST /api/payments` — 결제 요청 접수(`payment/crud` 책임, 응답이 `PENDING`으로 바뀐 것만 이 기능의
  영향)
- `POST /api/payments/{paymentId}/confirm` — 서버가 PortOne 재조회로 결제를 확정
- `POST /api/webhooks/portone` — PortOne 웹훅 수신(세션 인증 없음, 서명 검증이 인증 역할)
- 상세 요청/응답/에러: `docs/api/payment.md`

## 데이터 모델

- `payment` — 상세: `docs/db/payment.md`. 이 기능이 추가한 것: `pg_payment_id`(PortOne에 보낸
  가맹점 채번 결제 식별자) 컬럼, `PaymentStatus`에 `PENDING`/`FAILED`/`REFUND_PENDING` 3개 상태 추가.
- 신규 테이블 없음. 웹훅 멱등성 키는 Redis에 짧은 TTL(24시간)로만 기록한다(DB 저장 없음).

### 결제 상태 전이

```
PENDING --(서버가 PortOne 재조회: status=PAID && amount 일치)--> PAID
   |
   +--(서버가 PortOne 재조회: status=FAILED)--> FAILED

PAID --(PortOne 취소 SUCCEEDED, 즉시)--------------------------> REFUNDED
   |
   +--(PortOne 취소 REQUESTED, 비동기)--> REFUND_PENDING --(웹훅 Transaction.Cancelled)--> REFUNDED
   |
   +--(PortOne 취소 FAILED 응답)--> PAID 그대로 유지(로그만 남김, 수동 확인 필요 — 자동 재시도 없음)
```

## 결제 생성 → 확정 흐름

1. `PaymentService.create()`(`payment/crud`)가 가맹점 채번 `pgPaymentId`(`"pay_" + UUID`)를 만들어
   `PENDING` 결제를 저장하고, 프론트가 PortOne 결제창을 열 때 필요한 값(`pgPaymentId`,
   `portoneStoreId`, `portoneChannelKey`, `productName`을 orderName으로, `amount`를 totalAmount로)을
   응답에 실어 보낸다. **이 시점에는 판매자 수익 요약(`seller_revenue_summary`)을 증가시키지 않는다**
   — 아직 승인 여부를 서버가 확인하지 못했기 때문이다(이전 `payment/crud`는 결제 생성 즉시 증가시켰는데,
   실제 PG가 없던 가짜 결제라 가능했던 방식이고 이제는 버그가 된다).
2. 프론트(`checkout.js`)가 전역 `PortOne.requestPayment({...})`(CDN `<script
   src="https://cdn.portone.io/v2/browser-sdk.js">`로 로드, npm/번들러 도입 없음)로 결제창을 연다.
   사용자가 카카오페이 테스트 결제를 마치면 `PortOne.requestPayment`가 resolve된다.
3. 프론트가 `POST /api/payments/{paymentId}/confirm`을 호출한다. `PaymentService.confirm()`은
   **PortOne API(`GET /payments/{paymentId}`, `Authorization: PortOne {API_SECRET}`)를 직접
   재조회**해서 응답의 `status`가 `PAID`이고 `amount.total`이 우리가 기록해둔 `payment.amount`와
   정확히 일치할 때만 `Payment.confirm()`(`PENDING → PAID`)을 호출하고, 그때 비로소
   `sellerRevenueSummaryRepository.incrementPaid(...)`를 호출한다. 금액이 다르면 위변조 의심으로
   보고 확정하지 않는다(`PENDING` 유지, 재시도 여지를 남김). PortOne이 `FAILED`로 응답하면
   `Payment.fail()`(`PENDING → FAILED`)로 전환한다.
4. 웹훅(`Transaction.Paid`/`Transaction.Failed`)이 오면 `PaymentService.confirmByPgPaymentId()`가
   같은 재검증 로직을 pgPaymentId 기준으로 재사용한다 — 클라이언트가 confirm 호출을 못한 경우(결제 직후
   브라우저 종료 등)의 안전망이다. 이미 `PENDING`이 아니면(이미 확정/실패 처리됨) 스킵한다(멱등).
- **동시 확정 방지(비관적 락, 2026-08-20)**: `confirm()`과 `confirmByPgPaymentId()`는 각각
  `PaymentRepository.findByIdForUpdate`/`findByPgPaymentIdForUpdate`(QueryDSL
  `setLockMode(PESSIMISTIC_WRITE)`)로 `Payment` 행을 잠근 뒤에만 `PENDING` 게이트를 확인한다 —
  클라이언트 confirm()과 웹훅이 정상적으로 거의 동시에 들어올 수 있는데(4번이 바로 그 겹침의 안전망),
  락 없이 조회만 하면 둘 다 `PENDING`을 읽고 통과해 판매자 수익이 두 번 증가하고 알림도 두 번 발행될
  수 있었다(코드리뷰 2026-08-20 발견, `changes/002-confirm-concurrency-lock.md`). 먼저 락을 잡은
  트랜잭션이 커밋(상태를 `PAID`/`FAILED`로 전환)해야 다른 트랜잭션이 그 다음 PENDING 게이트에서
  걸러진다.

## PortOne 웹훅 수신 · 서명 검증

- 엔드포인트는 `SecurityConfig`에서 `permitAll`이다 — PortOne 서버가 직접 호출하므로 세션 인증을 요구할
  수 없다. **서명 검증(`PortOneWebhookVerifier`)이 곧 이 엔드포인트의 인증**이다.
- 서명 대상 문자열은 `"{webhook-id}.{webhook-timestamp}.{raw_body}"`이고, `raw_body`는 실제 수신
  바이트 그대로여야 한다 — 그래서 `PortOneWebhookController`는 `@RequestBody` DTO 역직렬화를 쓰지
  않고 `HttpServletRequest.getInputStream()`으로 원문을 직접 읽는다(재직렬화 시 공백 등이 달라지면
  서명이 깨진다).
- `webhook-signature` 헤더는 공백으로 구분된 `v1,<base64>` 여러 개일 수 있어, 하나라도
  `MessageDigest.isEqual`(상수시간 비교)로 일치하면 유효로 본다. 시크릿(`whsec_...`)은 접두사를 뗀
  나머지를 base64 디코드해 HMAC 키 바이트로 쓴다. `HMAC-SHA256`은 순수 `javax.crypto.Mac`으로 직접
  구현했다 — `io.portone:server-sdk`가 Maven Central에 실제로 존재하는지 확인되지 않아 검증 없이
  의존성을 추가하지 않았다(불필요한 의존성 지양이라는 프로젝트 스타일과도 맞음).
- **시크릿이 설정되지 않으면 무조건 거부한다(fail-closed)** — 서명 검증=인증이라, 트래픽 제어
  (`RateLimitFilter`)의 fail-open과는 반대 원칙이다(실패를 통과시키면 인증 우회가 됨).
- `webhook-timestamp`가 현재 시각과 5분 이상 벌어지면 리플레이 의심으로 거부한다.
- `webhook-id`를 멱등성 키로 Redis(기존 `RateLimitFilter`/`LoginAttemptGuard`와 같은 Redis)에 TTL
  24시간(PortOne 최대 5회 재전송 정책 감안)으로 기록해 중복 처리를 막는다. **Redis 장애 시
  fail-open**(멱등성 체크 없이 처리 계속) — 실제 반영 로직(`confirmByPgPaymentId`,
  `confirmRefundedByPgPaymentId`)이 이미 결제 상태 기반으로 멱등하게 구현돼 있어, 최악의 경우도 "한
  번 더 확인하고 끝"일 뿐 중복 반영으로 이어지지 않는다.
- **모르는 `type`은 에러 없이 무시한다**(PortOne 공식 문서의 하위 호환 원칙). 처리하는 타입:
  `Transaction.Paid`/`Transaction.Failed`(→ `PaymentService.confirmByPgPaymentId`),
  `Transaction.Cancelled`(→ `PaymentRefundService.confirmRefundedByPgPaymentId`),
  `Transaction.CancelPending`(→ 로그만, 상태 변경 없음 — 실제 최종 확정은 `Transaction.Cancelled`
  웹훅을 기다린다).

## 공구팀 미성사 자동환불의 PortOne 결제취소 연동

- 기존 `team/deadline-check`(`TeamDeadlineService.processDeadline`)는 비관적 락(`findByIdForUpdate`)
  트랜잭션 안에서 `Payment.refund()`(DB 상태만 전환)를 직접 호출했다. 이제 실제 환불은 PortOne
  결제취소 API를 호출해 성공을 확인한 뒤에만 이루어져야 하는데, **그 외부 HTTP 호출(지연 가능)을 락을
  잡은 트랜잭션 안에서 하면 락을 오래 붙잡게 된다** — 그래서 두 단계로 분리했다:
  1. `processDeadline`(락 보유 트랜잭션)은 그 팀의 `PAID` 결제 id 목록만 모아
     `TeamPaymentsRefundRequestedEvent(teamId, paymentIds)`를 발행한다. 이 시점에는 결제 상태·판매자
     수익 요약을 건드리지 않는다.
  2. `TeamPaymentsRefundRequestedEventListener`(`@Async` + `@TransactionalEventListener(phase =
     AFTER_COMMIT)`)가 그 트랜잭션이 **실제로 커밋된 이후에만**(락이 이미 풀린 뒤) 이 이벤트를
     소비한다 — 결제 건마다 고정 사유 문구("공구팀 미성사로 인한 환불")로 `PaymentCancellationExecutor.
     cancelOne(paymentId, reason)`을 호출하는 라우팅만 담당한다(취소 실행 자체는 아래 공유 실행기 참고).
  - **취소 실행 로직 추출·공유(`PaymentCancellationExecutor`, 2026-08-14, `refund/request` 작업에서
    추출)**: "취소 대상 조회 → PortOne 호출 → 결과 반영"을 원래 이 리스너 안에만 있던 코드에서 별도
    빈으로 뽑아냈다 — `refund/request`(판매자 승인/상품별 자동환불)도 정확히 같은 절차를 타야 해서,
    PortOne 취소 API를 호출하는 코드를 두 곳에 중복 작성하지 않기 위함이다. `PaymentCancellationExecutor.
    cancelOne(paymentId, reason)`이 `PaymentRefundService.findCancelTarget()`(짧은 읽기전용 조회)로
    취소 대상(pgPaymentId)을 확인하고, `PortOneClient.cancelPayment(pgPaymentId, reason)`을 호출한
    뒤(트랜잭션 밖), 그 결과를 `PaymentRefundService.applyCancelResult()`(별도 트랜잭션)로 반영한다.
    한 건의 취소 호출이 실패해도 예외를 그 건에서만 로그로 남기고 삼킨다(호출자의 다른 처리를 막지
    않음). `PaymentRefundService`가 이 오케스트레이션을 직접 하지 않는 이유는 그대로다 —
    `findCancelTarget`/`applyCancelResult`는 서로 다른 트랜잭션 경계를 가진 별도 메서드라 같은 클래스
    안에서 self-invocation하면 프록시(트랜잭션 경계)를 안 타기 때문에, 별도 빈(`PaymentCancellationExecutor`)
    으로 분리했다.
  - **호출자 두 곳(같은 실행기 공유)**: (1) `TeamPaymentsRefundRequestedEventListener` — 공구팀
    미성사 자동환불(이 기능 고유 트리거), (2) `RefundRequestApprovedEventListener`(`refund/request`
    개념) — 판매자 수동 승인 또는 상품별 "참여 취소 시 자동 환불" 설정에 따른 승인, 둘 다 같은
    `AFTER_COMMIT` + `@Async` 원칙을 따른다(상세: `docs/dev/refund/request/design.md`).
- `applyCancelResult`는 PortOne 취소 응답(`cancellation.status`)에 따라:
  - `SUCCEEDED`(즉시 완료) → `Payment.refund()`(`PAID → REFUNDED`) +
    `sellerRevenueSummaryRepository.applyRefund(...)`(판매자 수익 요약 감소) + 팀 결제라면
    `TeamRefundedEvent(teamId, sellerId, List.of(buyerMemberId))` 발행(알림 생성 트리거,
    `docs/dev/notification/refund-alert/design.md`).
  - `REQUESTED`(비동기 처리 중) → `Payment.markRefundPending()`(`PAID → REFUND_PENDING`)만 하고
    수익 요약·알림은 건드리지 않는다. 이후 웹훅 `Transaction.Cancelled`가 오면
    `PaymentRefundService.confirmRefundedByPgPaymentId()`가 같은 확정 로직(`confirmRefunded`)을
    실행해 `REFUNDED`로 최종 전환한다.
  - `FAILED` → 결제는 `PAID` 상태로 그대로 두고 ERROR 로그만 남긴다(자동 재시도 없음, 수동 확인
    필요 — 이번 스코프에서는 재시도 정책까지 다루지 않는다).
- **알려진 동작 변화(팀 단위 알림 배치 → 결제 건별 개별 알림)**: 이전에는 `TeamDeadlineService`가
  팀 하나당 `TeamRefundedEvent`를 정확히 1번 발행해 판매자가 알림을 1건만 받았다. 지금은 결제 건이
  실제로 확정될 때마다(비동기 PortOne 확인 시점이 결제 건마다 다를 수 있음) 개별 발행되므로, 같은
  팀에 결제가 여러 건이면 판매자가 그 건수만큼 여러 번 알림을 받을 수 있다
  (`docs/dev/notification/refund-alert/design.md`에도 기록).

## 규칙 / 검증

- 카카오페이 간편결제만 지원(`payMethod: "EASY_PAY"` 고정, 프론트 하드코딩 — 콘솔에 연결된 테스트 채널이
  카카오페이라서). 다른 결제수단은 스코프 밖.
- 서버 재검증 없이는 어떤 경로로도 결제가 `PAID`가 되지 않는다 — 클라이언트 confirm 호출도, 웹훅도
  전부 PortOne API를 직접 재조회한다(웹훅 페이로드의 `type`만 보고 상태를 그대로 믿지 않음).
- 판매자 수익 요약(`seller_revenue_summary`) 증가/감소는 항상 "서버가 실제로 확인한 시점"에만
  일어난다 — 생성(create)·환불요청 발행(processDeadline) 시점에는 절대 건드리지 않는다.
- PortOne 결제취소 API 호출은 어떤 DB 트랜잭션(비관적 락이든 아니든)도 붙잡지 않은 채로만 이루어진다
  (`PaymentRefundService.findCancelTarget`/`applyCancelResult`가 짧은 트랜잭션으로 앞뒤를 감싸고,
  실제 HTTP 호출은 `TeamPaymentsRefundRequestedEventListener`가 트랜잭션 밖에서 수행).
- 환경변수(`OPENAI_API_KEY`와 같은 패턴, 실제 값은 레포에 없음): `PORTONE_STORE_ID`,
  `PORTONE_CHANNEL_KEY`, `PORTONE_API_SECRET`, `PORTONE_WEBHOOK_SECRET`(`application.yaml`의
  `portone.*`).
- 테스트는 `PortOneClient`를 `@MockitoBean`으로 대체해 실제 PortOne 네트워크 호출을 절대 하지 않는다
  (`AiProductSuggestionServiceTest`가 `ChatClient.Builder`를 목으로 대체하는 것과 같은 패턴).
- **실측 완료**: 로컬 bootRun(실제 PortOne API Secret/Store ID/Channel Key)으로 브라우저에서 카카오페이
  QR 결제를 실제로 완료해 `PENDING → PAID` 확정, 위조 웹훅(서명 없음) 거부(프로덕션에서 `401
  WEBHOOK_VERIFICATION_FAILED` 확인), 공구팀 미성사 자동환불의 실제 PortOne 결제취소 API 호출까지
  전부 실측 검증됨(`docs/dev/payment/portone/changes/001-portone.md`, `docs/logs/payment/portone/001-portone.md`
  Attempt 3~5). 특히 Attempt 5에서는 PortOne 서버에 직접 결제 상세를 조회해 `status: CANCELLED`,
  `cancellations[0].trigger: "API"`, 그리고 `Transaction.Ready`/`Transaction.Paid`/`Transaction.Cancelled`
  웹훅 3건이 전부 우리 프로덕션 웹훅 엔드포인트로 전송되어 각각 `200`으로 정상 처리됐음을 확인했다.

## 프론트엔드 (최소 연동)

- `checkout.html`에 `<script src="https://cdn.portone.io/v2/browser-sdk.js"></script>`를 추가해
  전역 `window.PortOne`을 로드한다(npm/번들러 도입 없음, `@stomp/stompjs` CDN 로드와 같은 방식).
- `checkout.js`의 "결제하기" 버튼 핸들러: `POST /api/payments`로 `PENDING` 결제를 만든 뒤, 응답의
  `pgPaymentId`/`portoneStoreId`/`portoneChannelKey`/`productName`/`amount`로
  `PortOne.requestPayment({...})`를 호출한다. 성공하면 `POST /api/payments/{paymentId}/confirm`을
  호출해 서버 확정 결과를 받아 완료 화면을 보여준다. 실패/취소/확정 거부는 각각 사용자에게 안내만
  하고 재시도는 사용자가 버튼을 다시 눌러야 한다(자동 재시도 없음, 디자인/UX 다듬기는 스코프 밖).

## 관련 코드 위치

- `entity/Payment.java` — `pgPaymentId` 필드, `confirm()`/`fail()`/`markRefundPending()`/`refund()`
- `entity/PaymentStatus.java` — `PENDING`/`PAID`/`FAILED`/`REFUND_PENDING`/`REFUNDED`
- `dto/PaymentResponse.java` — `pgPaymentId`/`portoneStoreId`/`portoneChannelKey` 추가
- `repository/PaymentRepository.java` — `findByPgPaymentId(pgPaymentId)`,
  `findByIdForUpdate`/`findByPgPaymentIdForUpdate`(비관적 락, `confirm`/`confirmByPgPaymentId` 전용,
  2026-08-20)
- `client/PortOneClient.java`(인터페이스) / `PortOneApiClient.java`(실제 구현, `RestClient`) —
  `getPayment`/`cancelPayment`
- `client/PortOnePaymentDetail.java`, `client/PortOneCancelResult.java` — 응답 값 DTO
- `client/PortOneWebhookVerifier.java` — Standard Webhooks HMAC 서명 검증
- `service/PaymentService.java` — `create`(요청 접수, 수익 증가 없음), `confirm`(클라이언트 확정),
  `confirmByPgPaymentId`(웹훅 확정), `applyVerificationResult`(공통 검증·확정 로직)
- `service/PaymentRefundService.java` — `findCancelTarget`, `applyCancelResult`,
  `confirmRefundedByPgPaymentId`(웹훅 최종 확정)
- `service/PaymentCancellationExecutor.java` — 취소 실행 공유 로직(`cancelOne`, 2026-08-14 추출,
  `refund/request`와 공유)
- `service/PortOneWebhookService.java` — 서명검증→타임스탬프→멱등성→타입별 라우팅
- `service/TeamDeadlineService.java` — `processDeadline`이 이제 `TeamPaymentsRefundRequestedEvent`만
  발행(상세: `docs/dev/team/deadline-check/design.md`)
- `event/TeamPaymentsRefundRequestedEvent.java` / `event/TeamPaymentsRefundRequestedEventListener.java`
- `controller/PaymentController.java` — `POST /{paymentId}/confirm` 추가
- `controller/PortOneWebhookController.java` — raw body 직접 읽기
- `config/SecurityConfig.java` — `/api/webhooks/portone` permitAll
- `application.yaml` / `src/test/resources/application.yaml` — `portone.*` 환경변수
- 프론트: `static/checkout.html`(CDN 스크립트 태그), `static/js/checkout.js`
- 테스트:
  - `client/PortOneWebhookVerifierTest.java` — HMAC 서명 검증 순수 단위 테스트(정상/위조/시크릿
    미설정/헤더 누락)
  - `service/PortOneWebhookServiceTest.java` — 서명 실패 거부, 타임스탬프 만료 거부, 멱등성(중복
    webhook-id 무시), 타입별 라우팅, 모르는 타입 무시, Redis 장애 fail-open
  - `service/PaymentRefundServiceTest.java` — 취소 응답별(SUCCEEDED/REQUESTED/FAILED) 상태 전환,
    웹훅 최종 확정, 멱등성
  - `event/TeamPaymentsRefundRequestedEventListenerTest.java` — 순수 라우팅 로직만 검증(실행기 위임
    호출 확인, 2026-08-14 실행기 추출에 맞춰 축소 — 취소 실행 자체의 대상별 검증/실패 격리는
    `service/PaymentCancellationExecutorTest.java`로 이전)
  - `controller/PaymentControllerTest.java` — create 응답이 `PENDING`으로 바뀐 것 회귀 확인 +
    confirm 성공/금액불일치/PG상태불일치/게이트웨이오류/타인결제 시나리오(`PortOneClient`
    `@MockitoBean`)
  - `service/TeamDeadlineServiceTest.java` — `processDeadline`이 결제 상태를 직접 바꾸지 않고
    `TeamPaymentsRefundRequestedEvent`만 발행하는지(`@RecordApplicationEvents`)
  - `event/TeamDeadlineEventFlowTest.java` — 실제 커밋 후 PortOne(목) 취소 확인까지 이어지는
    end-to-end(SUCCEEDED 전체 환불, REQUESTED로 `REFUND_PENDING` 대기)
  - `service/SellerRevenueSummaryTest.java` / `SellerRevenueSummaryConcurrencyTest.java` — 수익
    요약 증가가 confirm() 시점으로, 감소가 PortOne 취소 확인(비동기) 이후로 옮겨진 것 회귀 확인
  - `service/PaymentConfirmConcurrencyTest.java`(신규, 2026-08-20) — 클라이언트 `confirm()`과 웹훅
    `confirmByPgPaymentId()`가 같은 결제를 동시에 확정 시도해도 비관적 락으로 정확히 한 번만 확정되고,
    알림도 정확히 1회만 발행되는지 검증
