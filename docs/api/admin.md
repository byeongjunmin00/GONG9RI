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


### GET /api/admin/products — 상품 현황(숨김 포함)

공개 목록(`GET /api/products`)은 숨김 상품을 제외하므로, 그걸 쓰면 **숨긴 상품을 되돌릴 방법이 없다.** 관리자 화면은 이 경로를 쓴다.

- 쿼리: `page`(기본 0), `size`(기본 20)
- 응답: `200 OK` — 공개 목록과 같은 `ProductPageResponse`. 각 항목에 `hidden`(Boolean)이 실린다.
- 신뢰배지·리뷰 평점은 채우지 않는다(관리자 화면이 쓰지 않아, 불필요한 집계 쿼리를 아낀다).

---

### PATCH /api/admin/products/{productId}/hidden — 상품 숨김/해제

- 쿼리: `hidden`(필수, boolean)
- 응답: `204 No Content`
- **삭제와 다르다**: 되돌릴 수 있고 데이터가 그대로 남는다. 결제·리뷰가 붙어 삭제할 수 없는 상품을 목록에서 치울 때 쓴다.
- 숨김 상품은 목록뿐 아니라 **상세(`GET /api/products/{id}`)에서도 404**다 — 목록에서만 빼면 주소를 아는 사람은 계속 볼 수 있다. 관리자에게도 동일하게 404인데, 상세 응답이 `productId`만으로 캐싱되기 때문이다(요청자 역할에 따라 결과가 달라지면 관리자가 조회한 값이 캐시에 남아 모두에게 나간다).

---

### DELETE /api/admin/products/{productId} — 상품 삭제

관리자가 상품을 삭제한다(판매자 본인이 아니어도 가능).

- 쿼리: `force`(기본 false)
- 응답: `204 No Content`

#### `force=true` — 강제 삭제

장난성 게시물처럼 **기록을 남길 가치가 없다고 관리자가 판단한 경우**에만 쓴다. 결제·리뷰·공구팀까지 전부 지우고 **되돌릴 수 없다.**

삭제 순서가 곧 정확성이다. FK(NO ACTION)로 묶여 있어 참조하는 쪽을 먼저 지우지 않으면 그 자리에서 실패한다.

```
refund_request → payment → product
notification, team_participation, payment → group_buy_team → product
```

> **매출 요약도 함께 바로잡는다.** `seller_revenue_summary`는 결제마다 누적만 하는 집계 테이블이라 결제를 지워도 저절로 줄지 않는다. 강제 삭제 후 남은 결제 기준으로 재계산해 덮어쓴다 — 안 하면 판매자 수익이 실제보다 부풀려진 채로 남는다.

- 삭제 정책 — **회원 삭제와 같은 결**이다. 돈·기록이 걸린 상품은 지우지 못한다.

  | 참조 데이터 | 처리 |
  |---|---|
  | 결제(payment) / 공구팀(group_buy_team) / 리뷰(review) | **삭제 거절**(409) |
  | 찜(wishlist) / 문의(inquiry) / 가격구간(price_tier) / 이미지(product_image) | 상품과 함께 삭제 |

  > 찜은 북마크일 뿐이고, 문의는 그 상품에 대한 질문이라 상품이 사라지면 의미가 없다. 둘 다 다른 테이블이 참조하지 않는 leaf 데이터다.
  > 볼륨에 저장된 실제 이미지 파일은 지우지 않는다(알려진 한계, `docs/dev/product/image/design.md`).

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_HAS_ACTIVITY` | 409 | 결제·공구팀·리뷰가 있는 상품 (`force=true`로 우회 가능) |
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `FORBIDDEN` | 403 | 관리자가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |

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
        "requesterId": 7,
        "requesterName": "이환불",
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
