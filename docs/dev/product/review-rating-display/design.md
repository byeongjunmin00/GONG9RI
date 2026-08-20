# 메인 카드 및 상세 페이지 리뷰 별점 (5성 채운별/빈별) & 리뷰 수 표기 — Design

## 개요
메인 페이지(`/`) 상품 카드와 상품 상세 페이지(`/product.html`) 상단 헤더 영역에 5성 별점(채운 별 `★` / 빈 별 `☆`)과 평점 숫자 및 총 리뷰 수를 표기하여 상품 신뢰도와 사용자 경험을 강화한다.

## API / DTO 연동
- `ProductSummaryResponse`: `Double ratingAverage`, `Integer reviewCount`
- `ProductResponse`: `Double ratingAverage`, `Integer reviewCount`
- Jackson Redis 캐시 역직렬화 호환을 위해 Boxed `Double`/`Integer` 타입을 적용한다.

## 관련 코드 위치
- **백엔드**:
  - `src/main/java/com/gong9ri/gong9ri/dto/ProductSummaryResponse.java`
  - `src/main/java/com/gong9ri/gong9ri/dto/ProductResponse.java`
  - `src/main/java/com/gong9ri/gong9ri/repository/ProductReviewStatProjection.java`
  - `src/main/java/com/gong9ri/gong9ri/repository/ReviewRepositoryCustom.java`
  - `src/main/java/com/gong9ri/gong9ri/repository/ReviewRepositoryImpl.java`
  - `src/main/java/com/gong9ri/gong9ri/service/ProductService.java`
- **프론트엔드**:
  - `src/main/resources/static/css/components.css`: `.card-rating-row`, `.card-rating-stars`, `.product-header-rating`
  - `src/main/resources/static/js/main.js`: `createRatingRowElement()`, `createProductCard()`
  - `src/main/resources/static/product.html`: `#product-header-rating`
  - `src/main/resources/static/js/product.js`: 상세 헤더 렌더링 및 리뷰 탭 이동 스크롤 이벤트

## UI 사양 (Option B & Option 1)
1. **메인 상품 카드 (Option B)**:
   - 상품 이름(`card-title`) 바로 아래 독립 라인(`card-rating-row`)으로 배치.
   - 별점: 5성별 (채운 별 `★` `#FFA24C` + 빈 별 `☆` `#CBD5E1`)
   - 평점 & 리뷰수: `4.5 (리뷰 12개)` (리뷰 0개 시 `0.0 (리뷰 0개)`)
2. **상품 상세 페이지 (Option 1)**:
   - 상품 제목 하단에 `#product-header-rating` 노출.
   - 클릭 시 하단 `#product-tabs` 리뷰 탭으로 스크롤 이동.
