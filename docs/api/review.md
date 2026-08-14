# review API

> 응답 형식(성공/실패 공통): 공통 규칙 — [api/README.md](README.md). 아래 응답 예시는 성공 시 `data` 안에 들어갈 내용만 표시.
> 실패: `{ "success": false, "code": "...", "message": "..." }`

---

## GET /api/products/{productId}/reviews — 상품 리뷰 목록 조회

> 비로그인도 조회 가능(상품 상세 페이지의 공개 정보).

- 경로 변수: `productId` (Long)

- 응답: `200 OK`
  ```json
  {
    "averageRating": 4.5,
    "count": 2,
    "reviews": [
      { "reviewId": 3, "memberId": 7, "memberName": "홍길동", "rating": 5, "content": "좋아요", "createdAt": "...", "updatedAt": "..." }
    ]
  }
  ```
  > 리뷰가 하나도 없으면 `averageRating`은 `null`, `count`는 `0`.

---

## POST /api/products/{productId}/reviews — 리뷰 작성

- 경로 변수: `productId` (Long)
- 요청 body:
  | 필드 | 타입 | 필수 | 설명 |
  |------|------|------|------|
  | rating | int | Y | 1~5 |
  | content | string | N | 최대 1000자 |

- 응답: `201 Created` — 위 리뷰 객체 형식

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `PRODUCT_NOT_FOUND` | 404 | 존재하지 않는 상품 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `REVIEW_NOT_ELIGIBLE` | 403 | 이 상품을 결제 완료(PAID)한 이력이 없음 |
  | `DUPLICATE_REVIEW` | 409 | 이미 이 상품에 리뷰를 작성함(회원당 상품별 1개) |
  | `VALIDATION_FAILED` | 400 | rating 누락/범위 밖 |

---

## PUT /api/reviews/{reviewId} — 리뷰 수정

- 경로 변수: `reviewId` (Long)
- 요청 body: `POST`와 동일(rating, content)
- 응답: `200 OK` — 수정된 리뷰 객체

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `REVIEW_NOT_FOUND` | 404 | 존재하지 않는 리뷰 |
  | `FORBIDDEN` | 403 | 본인이 작성한 리뷰가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |
  | `VALIDATION_FAILED` | 400 | rating 누락/범위 밖 |

---

## DELETE /api/reviews/{reviewId} — 리뷰 삭제

- 경로 변수: `reviewId` (Long)
- 응답: `204 No Content`

- 에러:
  | 코드 | HTTP | 설명 |
  |------|------|------|
  | `REVIEW_NOT_FOUND` | 404 | 존재하지 않는 리뷰 |
  | `FORBIDDEN` | 403 | 본인이 작성한 리뷰가 아님 |
  | `UNAUTHORIZED` | 401 | 미인증 |
