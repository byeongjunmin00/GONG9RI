# mypage API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

## 구매자 마이페이지

### GET /api/buyer/mypage/purchases — 구매 완료 목록

- 응답: `200 OK`
  ```json
  [
    {
      "paymentId": 10,
      "productId": 1,
      "productName": "제주 감귤 5kg",
      "amount": 18000,
      "status": "PAID",
      "paidAt": "2026-07-24T14:35:00"
    }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

### GET /api/buyer/mypage/teams — 공구 참여 목록

성사/미성사 팀 포함하여 본인이 참여한 팀 전체를 반환한다.

- 응답: `200 OK`
  ```json
  [
    {
      "teamId": 3,
      "productId": 1,
      "productName": "제주 감귤 5kg",
      "currentCount": 10,
      "maxParticipants": 10,
      "status": "SUCCESS",
      "deadline": "2026-07-31T23:59:59",
      "joinedAt": "2026-07-24T14:30:00"
    },
    {
      "teamId": 5,
      "productId": 2,
      "productName": "경북 사과 3kg",
      "currentCount": 3,
      "maxParticipants": 8,
      "status": "FAILED",
      "deadline": "2026-07-20T23:59:59",
      "joinedAt": "2026-07-18T09:00:00"
    }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## 판매자 마이페이지

### GET /api/seller/mypage/products — 내가 등록한 상품 목록

- 응답: `200 OK`
  ```json
  [
    {
      "productId": 1,
      "name": "제주 감귤 5kg",
      "basePrice": 25000,
      "maxParticipants": 10,
      "createdAt": "2026-07-24T10:00:00"
    }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

### GET /api/seller/mypage/revenue — 수익 현황

내 상품 전체에 대한 결제 집계를 반환한다.

- 응답: `200 OK`
  ```json
  {
    "totalRevenue": 540000,
    "paidCount": 30,
    "refundedCount": 5
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

### GET /api/seller/mypage/teams — 내 상품 공구 참여 현황

내가 등록한 상품에 개설된 공구팀과 각 팀의 참여 현황을 반환한다.

- 응답: `200 OK`
  ```json
  [
    {
      "teamId": 3,
      "productId": 1,
      "productName": "제주 감귤 5kg",
      "currentCount": 10,
      "maxParticipants": 10,
      "status": "SUCCESS",
      "deadline": "2026-07-31T23:59:59",
      "createdAt": "2026-07-24T10:00:00"
    }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## 알림

### GET /api/buyer/mypage/notifications — 구매자 알림 목록

- 응답: `200 OK`
  ```json
  [
    {
      "notificationId": 1,
      "type": "TEAM_REFUNDED",
      "message": "참여하신 공구팀이 미성사되어 환불 처리되었습니다.",
      "relatedTeamId": 5,
      "isRead": false,
      "createdAt": "2026-08-10T10:00:00"
    }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

### GET /api/seller/mypage/notifications — 판매자 알림 목록

- 응답: `200 OK`
  ```json
  [
    {
      "notificationId": 2,
      "type": "TEAM_REFUNDED",
      "message": "등록하신 상품의 공구팀이 미성사되어 환불 처리되었습니다.",
      "relatedTeamId": 5,
      "isRead": false,
      "createdAt": "2026-08-10T10:00:00"
    }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |
