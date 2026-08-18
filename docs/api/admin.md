# admin API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`
>
> 이 아래 전부 관리자(`Role.ADMIN`) 세션 로그인이 필요하다. 비로그인은 `401 UNAUTHORIZED`, 관리자가
> 아닌 로그인 사용자는 `403 FORBIDDEN`(`docs/dev/admin/design.md`).

## GET /api/admin/dashboard — 관리자 대시보드 요약

- 응답: `200 OK`
  ```json
  {
    "totalMembers": 42,
    "totalBuyers": 30,
    "totalSellers": 11,
    "totalProducts": 15,
    "totalPayments": 87,
    "pendingRefundRequests": 3
  }
  ```

---

## GET /api/admin/members — 회원 목록 조회

- 요청: 쿼리 파라미터
  | 파라미터 | 타입 | 필수 | 기본값 | 설명 |
  |----------|------|------|--------|------|
  | page | int | N | 0 | 페이지 번호 (0-based) |
  | size | int | N | 20 | 페이지 크기 |

- 응답: `200 OK`
  ```json
  {
    "content": [
      {
        "memberId": 1,
        "username": "hong1234",
        "name": "홍길동",
        "email": "hong@example.com",
        "role": "BUYER",
        "emailVerified": true,
        "suspended": false,
        "createdAt": "2026-08-10T05:53:47.456061"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42
  }
  ```

---

## POST /api/admin/members/{memberId}/suspend — 회원 정지

- 경로 변수: `memberId` (Long)
- 응답: `204 No Content`
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `MEMBER_NOT_FOUND` | 404 | 존재하지 않는 회원 |
  | `FORBIDDEN` | 403 | 관리자가 아니거나, 관리자 본인 계정을 대상으로 시도(자기 자신을 잠그는 것 방지) |

---

## POST /api/admin/members/{memberId}/unsuspend — 회원 정지 해제

- 경로 변수: `memberId` (Long)
- 응답: `204 No Content`
- 에러: `MEMBER_NOT_FOUND`(404)

---

## DELETE /api/admin/members/{memberId} — 회원 삭제

> 상품·결제·리뷰·공구팀(리더)·팀 참여·찜·문의(작성/답변)·환불요청·챗봇 세션 중 하나라도 이 회원을
> 참조하는 행이 있으면 거절된다(`MEMBER_HAS_ACTIVITY`) — 정지를 대신 쓸 것. 허용되는 경우엔 이
> 회원의 알림/AI 제안 로그/판매자 매출 요약도 함께 삭제된다.

- 경로 변수: `memberId` (Long)
- 응답: `204 No Content`
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `MEMBER_NOT_FOUND` | 404 | 존재하지 않는 회원 |
  | `MEMBER_HAS_ACTIVITY` | 409 | 상품·결제·리뷰 등 활동 기록이 있어 삭제할 수 없음 — 정지를 이용할 것 |
  | `FORBIDDEN` | 403 | 관리자가 아니거나, 관리자 본인 계정을 대상으로 시도 |

---

## GET /api/admin/refund-requests — 환불 요청 전체 현황 (읽기 전용)

> 판매자 범위 없이 전체를 본다. 승인/거절 액션은 없다 — 각 판매자 마이페이지(`docs/api/refund.md`)에서 처리.

- 요청: 쿼리 파라미터
  | 파라미터 | 타입 | 필수 | 기본값 | 설명 |
  |----------|------|------|--------|------|
  | page | int | N | 0 | 페이지 번호 (0-based) |
  | size | int | N | 20 | 페이지 크기 |
  | status | String | N | (없음) | `PENDING`/`APPROVED`/`REJECTED` 중 하나. 생략하면 전체 |

- 응답: `200 OK` — `content`의 각 항목은 `docs/api/refund.md`의 `RefundRequestResponse`와 동일 형태.
  ```json
  {
    "content": [
      {
        "refundRequestId": 1,
        "paymentId": 10,
        "productId": 4,
        "productName": "제주 감귤 5kg",
        "teamId": null,
        "amount": 25000,
        "paymentStatus": "PAID",
        "status": "PENDING",
        "reason": "단순 변심",
        "rejectionReason": null,
        "requestedAt": "2026-08-18T12:00:00",
        "decidedAt": null
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 3
  }
  ```
