# payment API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

## POST /api/payments — 결제 생성

혼자구매 또는 공구팀 참가 시 결제를 생성한다.
`teamId`가 null이면 혼자구매(정가 `basePrice` 적용), 값이 있으면 공구팀 참가 결제(현재 `current_count` 기준 가격 구간 적용).

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
    "status": "PAID",
    "paidAt": "2026-07-24T14:35:00"
  }
  ```

  > 혼자구매인 경우 응답의 `teamId`는 null.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 유효성 실패 |
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `TEAM_NOT_FOUND` | 404 | 존재하지 않는 팀 |
  | `TEAM_FULL` | 409 | 정원 초과 (동시 요청 경합 시) |
  | `UNAUTHORIZED` | 401 | 미인증 |

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
    "paidAt": "2026-07-24T14:35:00"
  }
  ```

  > `status`는 `PAID`(결제완료) 또는 `REFUNDED`(환불 — 팀 미성사 시 일괄 처리).

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 내역 |
  | `FORBIDDEN` | 403 | 본인 결제가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |
