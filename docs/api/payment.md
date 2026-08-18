# payment API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

> **PortOne(V2, 샌드박스) 연동 이후(docs/dev/payment/portone/design.md)** — 결제는 더 이상 한 번의
> 호출로 끝나지 않는다: **① 요청 접수(`POST /api/payments`) → ② 프론트가 PortOne 결제창(카카오페이
> 간편결제만)을
> 열어 사용자가 결제 → ③ 서버 확정(`POST /api/payments/{paymentId}/confirm`)** 순서다. 서버는 클라이언트가
> 보내는 "성공했다"는 신호를 그대로 믿지 않고, ③에서 PortOne API를 직접 재조회해서 실제 승인 상태·금액이
> 일치할 때만 확정한다. 결제 확정을 놓친 경우(브라우저 종료 등)를 대비한 안전망으로 PortOne 웹훅도 별도로
> 같은 재검증을 수행한다.

## POST /api/payments — 결제 요청 접수

혼자구매 또는 공구팀 참가 결제를 "요청 접수"한다 — 이 응답만으로는 아직 결제가 완료된 것이 아니다
(`status: "PENDING"`). `teamId`가 null이면 혼자구매(정가 `basePrice` 적용), 값이 있으면 공구팀 참가
결제(해당 팀의 목표 인원 `maxParticipants` 기준 가격 구간 적용 — 팀 생성 시점에 고정된 값이라, 같은
팀이면 먼저 결제하든 나중에 결제하든 금액이 항상 동일하다).

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | productId | Long | Y | 결제할 상품 ID |
  | teamId | Long | N | 공구팀 ID — null이면 혼자구매 |

- 응답: `201 Created`
  ```json
  {
    "paymentId": 10,
    "memberId": 7,
    "productId": 1,
    "productName": "제주 감귤 5kg",
    "teamId": 3,
    "amount": 18000,
    "status": "PENDING",
    "paidAt": "2026-07-24T14:35:00",
    "pgPaymentId": "pay_3f9a1c2e-...",
    "portoneStoreId": "store-...",
    "portoneChannelKey": "channel-key-..."
  }
  ```

  > 혼자구매인 경우 응답의 `teamId`는 null. `pgPaymentId`/`portoneStoreId`/`portoneChannelKey`는
  > 프론트가 `PortOne.requestPayment({ storeId, channelKey, paymentId: pgPaymentId, orderName:
  > productName, totalAmount: amount, currency: "CURRENCY_KRW", payMethod: "EASY_PAY" })`를 호출할 때
  > 그대로 쓰는 값이다(카카오페이 간편결제만 지원 — 콘솔에 연결된 테스트 채널이 카카오페이라
  > `payMethod`는 `CARD`가 아니라 `EASY_PAY`여야 한다). `storeId`/`channelKey`는 비밀값이 아니라
  > 브라우저 SDK에 그대로 노출되는 공개 식별자다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 유효성 실패 |
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `PRODUCT_NOT_YET_OPEN` | 409 | 오픈예정 시각이 아직 안 지난 상품(product/product-launch) |
  | `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
  | `TEAM_FULL` | 409 | 정원 초과 (동시 요청 경합 시) |
  | `FORBIDDEN` | 403 | 판매자 계정으로 결제 시도 (구매자만 결제 가능) |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## POST /api/payments/{paymentId}/confirm — 결제 확정

프론트가 `PortOne.requestPayment(...)` 호출이 성공적으로 끝난 뒤 호출한다. 서버는 이 요청의 파라미터를
신뢰하지 않고, **PortOne API(`GET /payments/{paymentId}`)를 직접 재조회**해서 실제 결제 상태가
`PAID`이고 금액(`amount.total`)이 우리가 기록해둔 `amount`와 정확히 일치할 때만 결제를 `PAID`로
확정한다. 일치하지 않으면(위변조 의심 포함) 확정하지 않는다.

- 경로 변수: `paymentId` (Long)
- 요청 body: 없음

- 응답: `200 OK` (확정된 결제, `status: "PAID"`)
  ```json
  {
    "paymentId": 10,
    "memberId": 7,
    "productId": 1,
    "productName": "제주 감귤 5kg",
    "teamId": 3,
    "amount": 18000,
    "status": "PAID",
    "paidAt": "2026-07-24T14:35:00",
    "pgPaymentId": "pay_3f9a1c2e-..."
  }
  ```
  > 이미 `PAID`인 결제에 다시 호출해도 그대로 현재 상태를 반환한다(멱등, 중복 확정 호출 방어).

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 |
  | `FORBIDDEN` | 403 | 본인 결제가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `PAYMENT_VERIFICATION_FAILED` | 409 | PortOne 재조회 결과 상태가 `PAID`가 아니거나 금액이 요청 금액과 다름(확정하지 않음, 결제는 `PENDING`으로 유지되거나 PortOne이 `FAILED`로 응답한 경우만 `FAILED`로 전환) |
  | `PAYMENT_GATEWAY_ERROR` | 503 | PortOne API 통신 실패(네트워크 오류 등) |

---

## GET /api/payments/{paymentId} — 결제 내역 단건 조회

- 경로 변수: `paymentId` (Long)

- 응답: `200 OK`
  ```json
  {
    "paymentId": 10,
    "memberId": 7,
    "productId": 1,
    "productName": "제주 감귤 5kg",
    "teamId": 3,
    "amount": 18000,
    "status": "PAID",
    "paidAt": "2026-07-24T14:35:00",
    "pgPaymentId": "pay_3f9a1c2e-...",
    "portoneStoreId": null,
    "portoneChannelKey": null
  }
  ```

  > `status`는 `PENDING`(승인 대기) / `PAID`(결제완료) / `FAILED`(승인 실패) / `REFUND_PENDING`(취소
  > 비동기 처리 중) / `REFUNDED`(환불 완료 — 팀 미성사 자동환불) 중 하나. `portoneStoreId`/`portoneChannelKey`는
  > `POST /api/payments` 응답에만 채워지고, 조회/확정 응답에서는 항상 null(프론트가 이미 create 응답에서
  > 받은 값을 쓰므로 재조회 불필요).

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 내역 |
  | `FORBIDDEN` | 403 | 본인 결제가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## POST /api/webhooks/portone — PortOne 웹훅 수신 (PG 전용, 세션 인증 없음)

PortOne이 결제/취소 상태 변화를 알려주는 콜백. 사람(브라우저)이 호출하는 API가 아니라 **PortOne 서버가
직접 호출**하므로 세션 인증을 요구하지 않는다(`SecurityConfig`에서 permitAll) — 대신 **서명 검증이 곧
인증 역할**을 한다.

- 요청 헤더:
  | 헤더 | 설명 |
  |------|------|
  | `webhook-id` | 이 웹훅 이벤트의 고유 id — 멱등성 키(재전송 시 동일) |
  | `webhook-timestamp` | 유닉스 초 — 5분 이상 차이나면 리플레이 의심으로 거부 |
  | `webhook-signature` | 공백으로 구분된 `v1,<base64서명>` 목록(하나라도 일치하면 유효) — Standard Webhooks 스펙, `HMAC-SHA256({webhook-id}.{webhook-timestamp}.{raw_body})` |

- 요청 body (PortOne이 보내는 그대로, 예시):
  ```json
  {
    "type": "Transaction.Paid",
    "timestamp": "2024-04-25T10:00:00.000Z",
    "data": { "paymentId": "pay_3f9a1c2e-...", "storeId": "...", "transactionId": "..." }
  }
  ```
  처리하는 `type`: `Transaction.Paid`/`Transaction.Failed`(→ 서버가 PortOne을 재조회해 확정/실패
  반영, `POST .../confirm`과 동일한 재검증 로직 재사용) · `Transaction.Cancelled`(→ 환불 최종 확정) ·
  `Transaction.CancelPending`(→ 로그만, 상태 변경 없음). **그 외 알 수 없는 `type`은 에러 없이
  무시한다**(PortOne 공식 문서의 하위 호환 원칙).

- 응답: `200 OK` (본문 없음) — 서명 검증 통과 + 처리 완료(또는 무시) 시.
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `WEBHOOK_VERIFICATION_FAILED` | 401 | 서명 검증 실패, 타임스탬프 만료(리플레이 의심), 필수 헤더 누락 |
  | `VALIDATION_FAILED` | 400 | 서명은 유효하나 본문이 올바른 JSON이 아님 |

  > 같은 `webhook-id`가 재전송(PortOne 정책상 최대 5회)돼도 멱등성 키(Redis, TTL 24시간)로 중복
  > 처리하지 않는다 — Redis 장애 시에는 멱등성 체크 없이 처리를 계속 진행한다(fail-open, 실제 반영
  > 로직 자체가 결제 상태 기반으로 이미 멱등하다).
