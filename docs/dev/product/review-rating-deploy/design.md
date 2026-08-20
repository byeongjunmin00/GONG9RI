# 리뷰 별점 (5성 채운별/빈별) 및 리뷰 수 연동 & 배포 — Design

## 개요
메인 페이지(`/`) 상품 카드 및 상품 상세 페이지(`/product.html`)에 5성 별점(채운 별 `★` / 빈 별 `☆`)과 평점 및 리뷰 수를 실데이터 DB(MySQL)와 연동하고, Railway 프로덕션 환경에 자동 배포(GitHub `main` push)한다.

## 백엔드 가공 규칙
- `ProductService`에서 `reviewStatMap` 조회 시 `averageRating`을 `Math.round(val * 10.0) / 10.0`으로 소수점 1자리(예: `5.0`, `4.7`)로 가공한다.
- `ReviewService.create/update/delete` 시 `@CacheEvict(cacheNames = {CacheConfig.PRODUCT_DETAIL_CACHE, CacheConfig.PRODUCT_LIST_CACHE}, allEntries = true)`를 통해 캐시 무효화를 보장한다.

## 프론트엔드 UI 사양
1. **메인 페이지 카드 (Option B)**:
   - 상품 이름(`card-title`) 바로 아래 독립 라인(`card-rating-row`)으로 배치.
   - 별점: 5성별 (채운 별 `★` `#FFA24C` + 빈 별 `☆` `#CBD5E1`)
   - 평점 & 리뷰수: `5.0 (리뷰 3개)` (리뷰 0개 시 `0.0 (리뷰 0개)`)
2. **상품 상세 페이지 (Option 1)**:
   - 상품 제목 하단 `#product-header-rating` 노출.
   - 클릭 시 하단 `#product-tabs` 리뷰 탭으로 스크롤 이동.
