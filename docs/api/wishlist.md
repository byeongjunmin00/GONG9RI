# wishlist API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

## POST /api/products/{productId}/wishlist — 찜 추가 (구매자 전용)

- 경로 변수: `productId` (Long)
- **멱등**: 이미 찜한 상품이면 새로 만들지 않고 그대로 성공 처리한다(에러 아님).
- 응답: `201 Created` (본문 `data: null`)
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## DELETE /api/products/{productId}/wishlist — 찜 해제 (구매자 전용)

- 경로 변수: `productId` (Long)
- **멱등**: 찜하지 않은 상품이어도 에러 없이 그대로 성공 처리한다.
- 응답: `204 No Content`
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## GET /api/buyer/mypage/wishlist — 내 찜 목록 (구매자 전용)

- 응답: `200 OK`
  ```json
  [
    {
      "productId": 1,
      "productName": "제주 감귤 5kg",
      "basePrice": 25000,
      "bestPrice": 15000,
      "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg",
      "sellerName": "제주농장",
      "wishlistedAt": "2026-08-18T18:00:00"
    }
  ]
  ```
- 찜한 순서(최신순, `createdAt desc`)로 반환.
- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `FORBIDDEN` | 403 | 판매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |
