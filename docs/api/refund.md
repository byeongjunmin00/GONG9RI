# refund API

> `requesterId`/`requesterName`은 2026-08-20 추가 — 판매자·관리자 화면이 상품명만 보여줘서
> **누구의 환불 요청인지 알 수 없었다**. 구매자 본인 조회에서는 자기 정보가 그대로 내려온다.

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

> **매우 중요한 제약**: 팀이 딸린 결제(`teamId != null`)의 환불은 오직 참여 취소(`POST
> /api/teams/{teamId}/leave`, `docs/api/team.md`)로만 일어난다. 아래 "직접 환불 요청"은 솔로 구매
> (`teamId == null`) 건에만 허용된다 — 팀이 딸린 결제로 시도하면 `409
> TEAM_PAYMENT_REFUND_NOT_ALLOWED`로 거절된다(2인 목표 팀에 계정 2개로 결제 후 하나만 환불하면
> 실질적 1인 결제인데 2인 구간 할인가로 사는 악용을 막기 위함, 사용자 확인).

---

## POST /api/payments/{paymentId}/refund-requests — 솔로 구매 건 직접 환불 요청

구매자가 팀 없이 혼자 구매한 `PAID` 결제에 대해 환불을 요청한다. 이미 배송됐을 수 있어 항상 판매자
승인/거절 절차를 거친다(상품별 "참여 취소 시 자동 환불" 설정과 무관).

- 경로 변수: `paymentId` (Long)
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | reason | String | Y | 환불을 요청하는 사유(자유 텍스트) |

- 응답: `201 Created`
  ```json
  {
    "refundRequestId": 1,
    "paymentId": 10,
    "productId": 1,
    "productName": "제주 감귤 5kg",
    "teamId": null,
    "requesterId": 7,
    "requesterName": "이환불",
    "amount": 25000,
    "paymentStatus": "PAID",
    "status": "PENDING",
    "reason": "단순 변심",
    "rejectionReason": null,
    "requestedAt": "2026-08-14T10:00:00",
    "decidedAt": null,
    "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | `reason` 누락 |
  | `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 |
  | `FORBIDDEN` | 403 | 본인 결제가 아님, 또는 판매자 계정으로 시도 |
  | `TEAM_PAYMENT_REFUND_NOT_ALLOWED` | 409 | 팀이 딸린 결제(`teamId != null`) — 직접 환불 요청 대상 아님 |
  | `PAYMENT_NOT_REFUNDABLE` | 409 | 결제 상태가 `PAID`가 아님 |
  | `REFUND_REQUEST_ALREADY_EXISTS` | 409 | 같은 결제에 이미 처리 대기 중인 요청이 있음 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## POST /api/refund-requests/{refundRequestId}/approve — 판매자 환불 요청 승인

판매자가 본인 상품에 대한 대기 중인 환불 요청을 승인한다. 승인 즉시 응답은 `APPROVED`로 바뀌지만, 실제
PortOne 결제취소는 이 트랜잭션이 커밋된 이후 비동기로 실행된다(`docs/dev/payment/portone/design.md`의
기존 취소 실행 경로 재사용) — 결제 상태는 `PAID → REFUND_PENDING(비동기 처리 중) → REFUNDED` 또는
`PAID → REFUNDED`(즉시 완료)로 뒤이어 전환된다.

- 경로 변수: `refundRequestId` (Long)
- 요청 body: 없음

- 응답: `200 OK`
  ```json
  {
    "refundRequestId": 1,
    "paymentId": 10,
    "productId": 1,
    "productName": "제주 감귤 5kg",
    "teamId": null,
    "requesterId": 7,
    "requesterName": "이환불",
    "amount": 25000,
    "paymentStatus": "PAID",
    "status": "APPROVED",
    "reason": "단순 변심",
    "rejectionReason": null,
    "requestedAt": "2026-08-14T10:00:00",
    "decidedAt": "2026-08-14T11:00:00"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `REFUND_REQUEST_NOT_FOUND` | 404 | 존재하지 않는 환불 요청 |
  | `FORBIDDEN` | 403 | 본인 상품에 대한 요청이 아님, 또는 구매자 계정으로 시도 |
  | `REFUND_REQUEST_ALREADY_DECIDED` | 409 | 이미 승인/거절 처리된 요청 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## POST /api/refund-requests/{refundRequestId}/reject — 판매자 환불 요청 거절

거절 사유는 자유 텍스트가 아니라 정해진 템플릿 중 하나를 고른다(사용자 확인 사항). 거절되면 결제는
`PAID` 그대로 유지된다.

- 경로 변수: `refundRequestId` (Long)
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | rejectionReason | String(enum) | Y | `ALREADY_SHIPPED` / `ALREADY_USED` / `POLICY_VIOLATION` / `OTHER` 중 하나 |

  > 각 값의 설명 문구(응답의 `rejectionReason`에 그대로 담기는 텍스트):
  > - `ALREADY_SHIPPED`: "상품이 이미 발송되어 환불이 어렵습니다."
  > - `ALREADY_USED`: "이미 사용/소비된 상품으로 환불이 어렵습니다."
  > - `POLICY_VIOLATION`: "환불 정책 상 요건을 충족하지 않아 환불이 어렵습니다."
  > - `OTHER`: "판매자 사정으로 환불 요청을 거절했습니다."

- 응답: `200 OK`
  ```json
  {
    "refundRequestId": 1,
    "paymentId": 10,
    "productId": 1,
    "productName": "제주 감귤 5kg",
    "teamId": null,
    "requesterId": 7,
    "requesterName": "이환불",
    "amount": 25000,
    "paymentStatus": "PAID",
    "status": "REJECTED",
    "reason": "단순 변심",
    "rejectionReason": "상품이 이미 발송되어 환불이 어렵습니다.",
    "requestedAt": "2026-08-14T10:00:00",
    "decidedAt": "2026-08-14T11:00:00"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | `rejectionReason` 누락/잘못된 값 |
  | `REFUND_REQUEST_NOT_FOUND` | 404 | 존재하지 않는 환불 요청 |
  | `FORBIDDEN` | 403 | 본인 상품에 대한 요청이 아님, 또는 구매자 계정으로 시도 |
  | `REFUND_REQUEST_ALREADY_DECIDED` | 409 | 이미 승인/거절 처리된 요청 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## 환불 요청 목록 조회 (마이페이지)

- `GET /api/buyer/mypage/refund-requests` — 본인이 요청한(또는 본인 참여 취소로 자동 생성된) 환불
  요청 전체(대기/승인/거절 포함). 상세: `docs/api/mypage.md`.
- `GET /api/seller/mypage/refund-requests` — 내가 등록한 상품에 대한 환불 요청 전체. 상세:
  `docs/api/mypage.md`.

## 참여 취소로 자동 생성되는 환불 요청 (참고, 별도 엔드포인트 없음)

`POST /api/teams/{teamId}/leave`(`docs/api/team.md`)가 성공하고 취소한 사람의 `PAID` 결제가 있으면
이 개념의 `RefundRequest`가 자동 생성된다(`reason: null`). 상품별 `autoRefundOnCancel` 설정이 켜져
있으면 승인 절차 없이 즉시 `APPROVED`로 생성되고, 꺼져 있으면 `PENDING`으로 남아 위 승인/거절
엔드포인트로 판매자가 처리해야 한다.
