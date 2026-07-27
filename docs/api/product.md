# product API

> 에러 응답 형식: `{ "code": "...", "message": "..." }` — 공통 규칙: [api/README.md](README.md)

## GET /api/products — 상품 목록 조회

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
        "productId": 1,
        "name": "제주 감귤 5kg",
        "basePrice": 25000,
        "bestPrice": 15000,
        "maxParticipants": 10,
        "sellerName": "제주농장",
        "createdAt": "2026-07-24T10:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42
  }
  ```

---

## GET /api/products/{productId} — 상품 상세 조회

- 경로 변수: `productId` (Long)

- 응답: `200 OK`
  ```json
  {
    "productId": 1,
    "sellerId": 5,
    "sellerName": "제주농장",
    "name": "제주 감귤 5kg",
    "description": "직접 재배한 새콤달콤 감귤",
    "basePrice": 25000,
    "maxParticipants": 10,
    "priceTiers": [
      { "minCount": 2, "price": 22000 },
      { "minCount": 5, "price": 18000 },
      { "minCount": 10, "price": 15000 }
    ],
    "createdAt": "2026-07-24T10:00:00"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |

---

## POST /api/products — 상품 등록 (판매자 전용)

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | name | String | Y | 상품명 |
  | description | String | N | 상품 설명 |
  | basePrice | int | Y | 정가 (1인 구매 시 가격) |
  | maxParticipants | int | Y | 팀 최대 인원 |
  | priceTiers | List | Y | 가격 구간표 (최소 1개) |
  | priceTiers[].minCount | int | Y | 해당 가격 적용 최소 인원 |
  | priceTiers[].price | int | Y | 1인당 가격 |

- 응답: `201 Created`
  ```json
  {
    "productId": 1,
    "name": "제주 감귤 5kg",
    "basePrice": 25000,
    "maxParticipants": 10,
    "priceTiers": [
      { "minCount": 2, "price": 22000 },
      { "minCount": 5, "price": 18000 },
      { "minCount": 10, "price": 15000 }
    ],
    "createdAt": "2026-07-24T10:00:00"
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 유효성 실패 |
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |

---

## PUT /api/products/{productId} — 상품 수정 (판매자 본인만)

- 경로 변수: `productId` (Long)

- 요청 body: `POST /api/products`와 동일 (전체 교체)

- 응답: `200 OK` → 수정된 상품 응답 (상세 조회 형식과 동일)

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 유효성 실패 |
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `FORBIDDEN` | 403 | 본인 상품이 아니거나 구매자 계정 |

---

## DELETE /api/products/{productId} — 상품 삭제 (판매자 본인만)

- 경로 변수: `productId` (Long)

- 응답: `204 No Content`

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `FORBIDDEN` | 403 | 본인 상품이 아니거나 구매자 계정 |
