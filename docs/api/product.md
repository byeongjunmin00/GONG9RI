# product API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

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
        "createdAt": "2026-07-24T10:00:00",
        "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg"
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
    "createdAt": "2026-07-24T10:00:00",
    "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg",
    "autoRefundOnCancel": false,
    "kakaoJsKey": "abcd1234..."
  }
  ```

  > `autoRefundOnCancel`: 참여 취소(`docs/api/team.md`의 `POST /api/teams/{teamId}/leave`)로 자동
  > 생성되는 환불 요청을 판매자 승인 없이 즉시 처리할지 여부(`docs/api/refund.md`). 솔로 구매 직접
  > 환불 요청에는 영향 없음(항상 승인 필요).

  > `kakaoJsKey`: 카카오톡 공유하기(`docs/dev/share/kakao-share/design.md`)용 카카오 JS SDK 초기화 키.
  > 서버 설정(`KAKAO_JS_KEY` 환경변수)이 비어있으면 빈 문자열(`""`)이 내려오고, 프론트는 이 경우
  > 공유 버튼을 숨긴다. 도메인 화이트리스트로 보호되는 공개 가능한 값이라 인증 없이 내려준다.

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
  | imageUrl | String | N | 상품 이미지 URL (없으면 프론트에서 그라디언트 placeholder 표시) |
  | autoRefundOnCancel | boolean | N | 참여 취소로 생긴 환불 요청을 승인 절차 없이 즉시 처리할지 여부. 생략하면 `false`(`docs/api/refund.md`) |

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
    "createdAt": "2026-07-24T10:00:00",
    "imageUrl": "https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg"
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

---

## POST /api/seller/products/ai-suggest — AI 상품등록 도우미 (판매자 전용)

> 상세 설계: `docs/dev/ai/product-suggestion/design.md`. LLM(OpenAI `gpt-4o-mini`)이 입력을 바탕으로 상품 정보를 **제안만** 한다 — 이 응답을 그대로 저장하지 않는다. 판매자가 검토·수정한 뒤 `POST /api/products`로 직접 등록해야 한다.

- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | category | String | Y | `FOOD`(신선식품) 또는 `GENERAL`(그 외) |
  | inputText | String | Y | 판매자가 대충 적은 상품 설명 |

- 응답: `200 OK`
  ```json
  {
    "suggestedName": "제주 감귤 5kg",
    "suggestedDescription": "신선한 제주 감귤 5kg, 유통기한은 2주입니다. 냉장 보관을 권장합니다.",
    "suggestedBasePrice": 10000,
    "suggestedMaxParticipants": 10
  }
  ```

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `VALIDATION_FAILED` | 400 | 필드 유효성 실패 |
  | `FORBIDDEN` | 403 | 구매자 계정으로 시도 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `AI_SUGGESTION_FAILED` | 503 | LLM 호출 실패(타임아웃 등) — `ai_suggestion_log`에도 실패로 기록됨 |
