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
      "paidAt": "2026-07-24T14:35:00",
      "teamId": null,
      "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg",
      "shipmentStatus": "IN_TRANSIT",
      "shipmentStatusLabel": "배송중",
      "trackingCarrier": "CJ대한통운",
      "trackingNumber": "123456789012"
    }
  ]
  ```

  > `teamId`: 팀이 딸린 결제면 값이 있고, 혼자구매면 `null`. `null`인(솔로 구매) `PAID` 결제만
  > `docs/api/refund.md`의 직접 환불 요청 대상이다.
  >
  > `shipmentStatus`/`shipmentStatusLabel`/`trackingCarrier`/`trackingNumber`(2026-08-21 `007` 추가):
  > 판매자가 `PATCH /api/seller/mypage/orders/{paymentId}/shipment`로 입력한 배송 정보를 읽기 전용으로
  > 노출한다. `shipmentStatus`는 `PRODUCT_PREPARING`/`SHIPPING_PREPARING`/`IN_TRANSIT`/`DELIVERED` 중
  > 하나이며 기본값은 `PRODUCT_PREPARING`이다. `trackingCarrier`/`trackingNumber`는 판매자가 아직
  > 입력하지 않았으면 `null`이다.

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
      "joinedAt": "2026-07-24T14:30:00",
      "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg"
    },
    {
      "teamId": 5,
      "productId": 2,
      "productName": "경북 사과 3kg",
      "currentCount": 3,
      "maxParticipants": 8,
      "status": "FAILED",
      "deadline": "2026-07-20T23:59:59",
      "joinedAt": "2026-07-18T09:00:00",
      "imageUrl": null
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
      "createdAt": "2026-07-24T10:00:00",
      "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg"
    }
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

### GET /api/seller/mypage/orders — 주문·배송 내역 (2026-08-21 추가)

내 상품을 결제한 구매자 정보와 배송 준비 상태를 반환한다(`docs/dev/mypage/view/changes/005-seller-order-shipment-management.md`). 결제 상태·팀 상태로부터 서버가 계산한 `preparationStatus`/`preparationStatusLabel`을 함께 내려준다 — 프론트는 이 값을 그대로 배지로 표시하면 된다.

- 응답: `200 OK`
  ```json
  [
    {
      "paymentId": 10,
      "buyerName": "홍길동",
      "buyerEmail": "buyer1@test.com",
      "productId": 1,
      "productName": "유기농 딸기 1kg",
      "amount": 15000,
      "status": "PAID",
      "paidAt": "2026-08-21T14:35:00",
      "teamId": null,
      "teamStatus": null,
      "teamCurrentCount": null,
      "teamMaxParticipants": null,
      "preparationStatus": "PREPARING",
      "preparationStatusLabel": "🚚 배송 준비 중",
      "shipmentStatus": "PRODUCT_PREPARING",
      "shipmentStatusLabel": "상품 준비중",
      "trackingCarrier": null,
      "trackingNumber": null
    }
  ]
  ```

  > `preparationStatus`는 `REFUNDED`(환불/취소됨) · `RECRUITING`(공구 모집 중) · `FAILED`(공구 실패) · `PREPARING`(배송 준비 중, 공구 성공 또는 솔로 구매) 중 하나다.
  >
  > `shipmentStatus`/`shipmentStatusLabel`/`trackingCarrier`/`trackingNumber`(2026-08-21 `007` 추가):
  > 판매자가 직접 조작하는 배송 단계 — 아래 `PATCH .../shipment`로 바꾼다. `preparationStatus`가
  > `PREPARING`이 아닌 주문(환불됐거나 아직 공구 진행/실패 중)은 항상 기본값(`PRODUCT_PREPARING`,
  > 택배사·송장번호 `null`)만 갖는다 — 그 상태에서는 변경 자체가 거절되기 때문이다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

### PATCH /api/seller/mypage/orders/{paymentId}/shipment — 배송 단계 변경 (2026-08-21 `007` 추가)

판매자가 자기 상품 주문의 배송 단계를 직접 바꾼다. 4단계(`PRODUCT_PREPARING`/`SHIPPING_PREPARING`/`IN_TRANSIT`/`DELIVERED`) 중 아무거나로 자유롭게 전환 가능하다(순서 강제 없음). 성공하면 그 주문의 구매자에게 `SHIPMENT_UPDATED` 알림이 간다.

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | shipmentStatus | String (enum) | Y | `PRODUCT_PREPARING`/`SHIPPING_PREPARING`/`IN_TRANSIT`/`DELIVERED` |
  | trackingCarrier | String | N | 택배사명(자유 텍스트) |
  | trackingNumber | String | N | 송장번호. `shipmentStatus`가 `IN_TRANSIT`/`DELIVERED`면 필수 |

  ```json
  {
    "shipmentStatus": "IN_TRANSIT",
    "trackingCarrier": "CJ대한통운",
    "trackingNumber": "123456789012"
  }
  ```

- 응답: `200 OK` → 위 `GET .../orders`의 항목 하나와 동일한 형식(변경된 값 반영)

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `TRACKING_NUMBER_REQUIRED` | 400 | `IN_TRANSIT`/`DELIVERED`로 바꾸는데 `trackingNumber`가 비어있음 |
  | `PAYMENT_NOT_FOUND` | 404 | 존재하지 않는 결제 |
  | `SHIPMENT_STATUS_NOT_APPLICABLE` | 409 | 환불됐거나(`REFUNDED`/`REFUND_PENDING`) 아직 배송 대상이 아닌(공구 `RECRUITING`/`FAILED`) 주문 |
  | `FORBIDDEN` | 403 | 본인 상품의 주문이 아니거나 구매자 계정으로 시도 |
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
      "leaderName": "김팀장",
      "participantNames": ["김팀장", "박참여"],
      "deadline": "2026-07-31T23:59:59",
      "createdAt": "2026-07-24T10:00:00",
      "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg"
    }
  ]
  ```

  > `leaderName` / `participantNames`는 2026-08-20 추가. 이전에는 상품명과 인원 수만 내려가서
  > **판매자가 "누가 참여했는지" 알 수 없었다.** `participantNames`는 참여 순서(`joinedAt` 오름차순)이며
  > 리더도 참여자이므로 목록에 포함된다.

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## 알림

> **알림 종류(`type`)는 11종이다**(2026-08-21 `SHIPMENT_UPDATED` 추가) — `TEAM_REFUNDED`, `TEAM_SUCCESS`, `INQUIRY_CREATED`, `INQUIRY_ANSWERED`, `PAYMENT_RECEIVED`, `REVIEW_CREATED`, `REFUND_REQUESTED`, `REFUND_REQUEST_APPROVED`, `REFUND_REQUEST_REJECTED`, `SUPPORT_MESSAGE_RECEIVED`, `SHIPMENT_UPDATED`. 각 종류가 누구에게 가는지는 `docs/dev/notification/refund-alert/design.md` 참고.
>
> **`linkUrl`**(2026-08-20 추가)은 알림을 눌렀을 때 이동할 앱 내부 경로다. 이 필드가 생기기 전에 만들어진 알림은 `null`이므로 클라이언트는 `null`을 정상 처리해야 한다(이동하지 않음).
>
> **목록 응답이 배열이 아니라 객체다**(2026-08-20 페이지네이션 도입). `?page=0&size=20`(기본값)으로 최신순 페이지를 받는다.
>
> ```json
> {
>   "unreadCount": 5,
>   "totalCount": 25,
>   "hasNext": true,
>   "notifications": [ ... ]
> }
> ```
>
> `unreadCount`는 **현재 페이지가 아니라 그 회원 전체 기준**이다 — 목록이 잘려 오므로 클라이언트가 받아온 목록에서 세면 틀린다. `hasNext`가 참이면 다음 페이지가 남아있다.

### GET /api/buyer/mypage/notifications — 구매자 알림 목록

- 응답: `200 OK`
  ```json
  [
    {
      "notificationId": 1,
      "type": "TEAM_REFUNDED",
      "message": "참여하신 공구팀이 미성사되어 환불 처리되었습니다.",
      "relatedTeamId": 5,
      "linkUrl": "/buyer/mypage.html",
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
      "linkUrl": "/seller/mypage.html",
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

---

### POST /api/{buyer,seller}/mypage/notifications/{notificationId}/read — 알림 읽음 처리 (2026-08-19 추가)

헤더 알림 벨 UI(신규)에서 사용 — 이전까지는 목록 조회 API만 있고 읽음 처리 수단이 없었다.

- 응답: `200 OK`, 본문 없음(`data: null`)
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `NOTIFICATION_NOT_FOUND` | 404 | 존재하지 않는 알림 |
  | `FORBIDDEN` | 403 | 본인 알림이 아님(다른 회원 소유), 또는 반대 역할 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

### POST /api/{buyer,seller}/mypage/notifications/read-all — 알림 모두 읽음 처리 (2026-08-19 추가)

본인의 안 읽은 알림 전체를 한 번의 벌크 UPDATE로 읽음 처리한다.

- 응답: `200 OK`, 본문 없음(`data: null`)
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 반대 역할 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## 환불 요청

응답 항목 형식은 `docs/api/refund.md`의 `RefundRequestResponse`와 동일하다(승인/거절 액션 자체는
`docs/api/refund.md`의 `POST /api/refund-requests/{id}/approve`/`reject`가 담당 — 이 두 조회
엔드포인트는 목록 노출만 한다).

### GET /api/buyer/mypage/refund-requests — 구매자 본인 환불 요청 목록

본인이 직접 요청했거나(솔로 구매), 본인의 참여 취소로 자동 생성된 환불 요청 전체(대기/승인/거절
포함)를 반환한다.

- 응답: `200 OK`
  ```json
  [
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
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

### GET /api/seller/mypage/refund-requests — 판매자 본인 상품 환불 요청 목록

내가 등록한 상품에 대해 들어온 환불 요청 전체(대기/승인/거절 포함)를 반환한다.

- 응답: `200 OK`
  ```json
  [
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
  ]
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |
