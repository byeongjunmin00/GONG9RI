# 001-portone — 포트원(PortOne) V2 실결제 연동 (로그)

## Attempt 1 — 2026-08-12

- 시도: 승인된 계획(`docs/dev/ongoing/payment-portone.md`)대로 PortOne V2(샌드박스, 카드결제만) 연동을 구현했다.
  - **엔티티/DB**: `PaymentStatus`에 `PENDING`/`FAILED`/`REFUND_PENDING` 3개 상태를 추가(기존 `PAID`/`REFUNDED`와 합쳐 5개), `Payment`에 `pgPaymentId`(PortOne 가맹점 채번 결제 식별자, UNIQUE 인덱스) 컬럼과 `confirm()`/`fail()`/`markRefundPending()`을 추가했다. 기존 4-arg 생성자(즉시 PAID)는 레거시/테스트 전용으로 그대로 남기고, 실제 흐름은 새 5-arg 생성자(PENDING 시작)를 쓰도록 분리했다 — 기존에 4-arg 생성자로 결제 이력을 직접 만드는 다른 테스트(mypage, revenue 등)를 건드리지 않기 위한 선택.
  - **결제 생성→확정**: `PaymentService.create()`는 이제 `pgPaymentId`를 채번해 `PENDING` 결제만 만들고(판매자 수익 요약 증가 없음), 신규 `PaymentService.confirm()`/`confirmByPgPaymentId()`가 PortOne `GET /payments/{paymentId}`를 직접 재조회해 `status=PAID` && 금액 일치를 확인한 뒤에만 `PAID` 확정 + 수익 요약 증가를 수행하도록 옮겼다(브리핑에서 지적한 "확정 시점 이전에 수익이 잡히는 버그" 수정).
  - **PortOne 클라이언트**: `client/PortOneClient`(인터페이스) + `PortOneApiClient`(`RestClient` 기반 실제 구현, 별도 SDK 의존성 추가 없음) + `PortOnePaymentDetail`/`PortOneCancelResult` DTO를 신규 작성했다. `io.portone:server-sdk`의 Maven Central 존재 여부가 확인되지 않아 추가하지 않았다(브리핑 지시대로).
  - **웹훅**: `PortOneWebhookController`(raw body를 `HttpServletRequest.getInputStream()`으로 직접 읽음, `@RequestBody` 미사용) + `PortOneWebhookService`(서명검증→타임스탬프 신선도(5분)→멱등성(Redis, webhook-id, TTL 24시간, fail-open)→타입별 라우팅, 모르는 type은 무시) + `client/PortOneWebhookVerifier`(Standard Webhooks HMAC-SHA256, `javax.crypto.Mac`으로 직접 구현, 시크릿 미설정 시 fail-closed)를 신규 작성했다. `SecurityConfig`에 `/api/webhooks/portone` POST를 permitAll로 추가했다.
  - **환불(팀 미성사) → PortOne 취소 연동**: `TeamDeadlineService.processDeadline()`이 더 이상 `Payment.refund()`를 직접 호출하지 않고, 비관적 락 트랜잭션 안에서는 환불 대상 결제 id 목록만 모아 신규 `TeamPaymentsRefundRequestedEvent`를 발행하도록 바꿨다. 신규 `TeamPaymentsRefundRequestedEventListener`(`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`)가 그 트랜잭션이 커밋된 이후에만(락이 풀린 뒤) 결제 건마다 `PortOneClient.cancelPayment()`를 호출하고, 신규 `PaymentRefundService`(트랜잭션 밖에서 조회 → 트랜잭션 안에서 결과 반영으로 분리)가 응답(`SUCCEEDED`/`REQUESTED`/`FAILED`)에 따라 `REFUNDED`/`REFUND_PENDING`/(PAID 유지+ERROR 로그)로 반영한다. `REQUESTED`(비동기)로 대기 중인 결제는 웹훅 `Transaction.Cancelled`가 `PaymentRefundService.confirmRefundedByPgPaymentId()`로 최종 확정한다. 기존 `TeamRefundedEvent`(알림 트리거)는 이제 `PaymentRefundService`가 결제 건별로 발행하도록 발행 주체를 옮겼다(팀 단위 1회 배치 → 결제 건별 개별 발행으로 동작이 바뀜, 문서에 명시).
  - **프론트 최소 연동**: `checkout.html`에 `<script src="https://cdn.portone.io/v2/browser-sdk.js">` CDN 태그를 추가(npm/번들러 도입 없음)하고, `checkout.js`의 "결제하기" 핸들러를 `POST /api/payments`(요청 접수) → `PortOne.requestPayment({...})`(카드 고정) → `POST /api/payments/{paymentId}/confirm`(서버 확정) 3단계로 변경했다.
  - **환경변수**: `application.yaml`에 `portone.store-id`/`channel-key`/`api-secret`/`webhook-secret`/`api-base-url`을 `PORTONE_*` 환경변수로 추가(`OPENAI_API_KEY`와 같은 패턴, 실제 값은 레포에 없음). `src/test/resources/application.yaml`에는 실호출이 나가지 않도록 더미 값만 채웠다(테스트는 전부 `PortOneClient`를 `@MockitoBean`으로 대체).
  - **문서**: `docs/api/payment.md`(요청/확정/웹훅 계약), `docs/db/payment.md`(신규 컬럼·상태값), `docs/dev/payment/crud/design.md`(PENDING 시작으로 정정), `docs/dev/team/deadline-check/design.md`(환불 트리거가 이벤트만 발행하도록 흐름 갱신), `docs/dev/notification/refund-alert/design.md`(발행 주체·배치→개별 발행 동작 변화 기록), 신규 `docs/dev/payment/portone/design.md`를 작성/갱신했다.
  - **테스트**: 신규 순수 단위 테스트(`PortOneWebhookVerifierTest`, `PortOneWebhookServiceTest`, `PaymentRefundServiceTest`, `TeamPaymentsRefundRequestedEventListenerTest` — 전부 Mockito, Spring 컨텍스트/네트워크 없음)를 추가했고, 기존 통합 테스트(`PaymentControllerTest`, `TeamDeadlineServiceTest`, `TeamDeadlineEventFlowTest`, `SellerRevenueSummaryTest`, `SellerRevenueSummaryConcurrencyTest`)를 새 흐름(요청접수→확정, 환불의 비동기 PG 취소 확인)에 맞게 갱신했다 — 전부 `PortOneClient`를 `@MockitoBean`으로 대체해 실제 PortOne 네트워크 호출 없이 검증한다.
  - `./gradlew compileJava`, `./gradlew compileTestJava`, `./gradlew test`(로컬 MySQL/Redis 기동 상태, 180개 테스트), `./gradlew build`를 직접 실행해 모두 통과하는 것까지 확인했다(회귀로 깨진 기존 테스트 4개 파일을 새 흐름에 맞춰 고쳐가며 반복 실행함 — 상세 원인은 Evaluator가 기록).
- **이번 시도에서 하지 않은 것(스코프 밖 확인)**: 실제 PortOne 샌드박스 결제창을 브라우저로 열어 테스트카드로 결제를 완료하는 실측(브리핑에서 지시된 이번 Generate 단계의 한계 — 브라우저 도구 없음). 간편결제/계좌이체/가상계좌, 사용자 자발적 결제취소, `team/join`-`payment/create` 결합 재설계는 계획대로 손대지 않았다.

## Attempt 2 — 2026-08-12 (스코프 정정)

- 시도: 실제 브라우저(Claude in Chrome)로 사용자의 포트원 관리자콘솔(`admin.portone.io/integration-v2/manage/channel`) 화면을 직접 열람해, Attempt 1이 전제한 "카드 결제만"이 실제 설정과 다르다는 것을 발견했다 — 콘솔에 연결된 테스트 채널의 결제대행사가 카드 PG가 아니라 **카카오페이**(PG Provider: `kakaopay`, MID: `TC0ONETIME`)였다. 사용자가 앞서 전달한 "cid: TC0ONETIME"은 포트원 Store ID가 아니라 이 카카오페이 채널의 MID였음도 이때 확인됨.
- 포트원 공식 문서(`developers.portone.io` 저장소의 `pg/v2/kakaopay.mdx` 원문, GitHub API로 직접 확인)로 카카오페이는 `payMethod`를 `"CARD"`가 아니라 `"EASY_PAY"`로 보내야 함을 확인 — `"CARD"`로 두면 이 채널로는 결제창이 열리지 않는다.
- 사용자에게 "카카오페이로 스코프 변경" vs "카드 PG 채널 추가 연결" 중 선택을 물었고, **카카오페이로 스코프 변경**을 승인받았다(2026-08-12).
- 반영: `checkout.js`의 `payMethod: 'CARD'` → `'EASY_PAY'`로 수정(+ 근거 주석 추가). `docs/dev/ongoing/payment-portone.md`(스코프 정정, 취소선으로 이전 결정 남김), `docs/dev/payment/portone/design.md`, `docs/api/payment.md`의 "카드 결제만" 서술을 전부 "카카오페이 간편결제만"/`EASY_PAY`로 갱신. 서버측 코드(`PortOneClient`, `PaymentService` 등)는 `payMethod`를 다루지 않아(프론트 전용 파라미터) 변경 불필요.
- `./gradlew test --rerun`으로 캐시 없이 재검증 — 기존 180개 테스트 전부 재통과(payMethod는 서버 로직에 없어 회귀 없음).
- 미완료로 남은 것(Attempt 1과 동일): 실제 브라우저로 카카오페이 샌드박스 결제창을 열어 결제를 완료하는 실측.

## Attempt 3 — 2026-08-12 ✅ PASS (실제 브라우저 E2E 실측)

- 시도: 로컬에서 `PORTONE_STORE_ID`/`PORTONE_CHANNEL_KEY`/`PORTONE_API_SECRET` 실제 값으로 `bootRun`(포트 8081, 기존에 다른 세션이 8080을 점유 중이라 충돌 피함)을 띄우고, 테스트 판매자·구매자 계정과 상품(productId=3103)을 API로 생성한 뒤, 실제 Claude Browser로 로그인 → `checkout.html?productId=3103` → "결제하기" 클릭까지 진행했다.
- **실제 포트원 카카오페이 샌드박스 결제창이 정상적으로 열림**(QR결제 화면, 실제 QR 코드 생성 확인) — Attempt 2에서 고친 `payMethod: EASY_PAY`가 실제로 유효함이 이걸로 확정됐다. ("카톡결제" 탭은 "데모 환경에서는 지원하지 않는 서비스"라고 안내됨 — QR 스캔만 가능.)
- 사용자가 실제 휴대폰으로 QR을 스캔해 카카오페이 샌드박스 결제를 완료했다.
- **결과(실측 증거)**:
  - `POST /api/payments` → `201`(PENDING 접수), `POST /api/payments/2494/confirm` → `200`, 응답 지연 **450ms**(실제 PortOne API 왕복 — mock이 아님을 뒷받침).
  - 서버 로그: `PaymentService` "결제 확인 완료: paymentId=2494, pgPaymentId=pay_5256687b-78a0-4e62-b449-c93531254fb0, amount=1000"(21:47:56, 요청 시점 21:46:29로부터 87초 후 — 실제 QR 스캔 소요 시간과 일치).
  - DB: `payment` 테이블 `id=2494, status=PAID, pg_payment_id=pay_5256687b-...` 확인.
  - `seller_revenue_summary`(seller_id=5290): `paid_count=1, total_revenue=1000`, `updated_at=21:47:56` — **confirm 시점에만** 증가함을 실측 확인(요청 접수 시점 21:46:29엔 증가하지 않음 — Attempt 1에서 고친 "확정 전 매출 집계" 버그가 실제 시나리오에서도 재발하지 않음).
  - 화면: "결제가 완료되었습니다 / 상태 PAID / 1,000원" 정상 렌더링.
- **여전히 미실측(다음 단계)**: 웹훅 서명 검증(로컬은 공인 URL이 없어 포트원이 웹훅을 못 보냄 — Railway 배포 후 실측 필요), 공구팀 미성사 자동환불의 실제 PortOne 취소 API 호출(별도 시나리오 셋업 필요).
