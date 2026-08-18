# 판매자 신뢰 배지 (product/seller-trust) — Design

## 개요

판매자마다 새 평판 시스템(등급/점수 테이블)을 따로 만들지 않고, 이미 있는 리뷰 데이터(`Review`)만으로 "신뢰 판매자" 배지를 계산한다. 리뷰는 실제로 결제(PAID) 완료한 구매자만 남길 수 있어(`ReviewService` 검증) 조작 여지가 적은 신호다.

## 기준

- 판매자의 **전체 상품에 달린 리뷰**를 합산해 평균 평점 ≥ 4.5, 리뷰 개수 ≥ 3일 때 배지 노출.
- 상품 단위가 아니라 판매자 단위 집계다 — 리뷰가 하나도 없는 신상품이라도 그 판매자가 다른 상품에서 이미 신뢰를 쌓았다면 배지가 뜬다.
- 두 조건 다 필요: 평점만 보면 리뷰 1~2개짜리도 만점만 받으면 배지를 달 수 있어, 최소 리뷰 개수를 별도로 요구한다.
- **실측 근거 없는 초기값**(`ProductService.TRUSTED_SELLER_MIN_RATING`/`TRUSTED_SELLER_MIN_REVIEW_COUNT`) — 운영 데이터가 쌓이면 조정 예정.

## API / 인터페이스

- `GET /api/products`, `GET /api/products/{id}`: 응답에 `sellerTrustedBadge`(boolean) 포함.

## 데이터 모델

새 테이블/컬럼 없음 — 기존 `review` 테이블(`product_id` → `product.seller_id` 조인)만 집계한다.

## 구현

- `SellerRatingProjection`/`SellerRatingProjectionImpl` — `BestPriceProjection`과 동일한 QueryDSL 생성자 프로젝션 패턴(인터페이스는 QueryDSL이 직접 지원 안 해서 구체 클래스 필요).
- `ReviewRepositoryCustom.findSellerRatingSummaries(List<Long> sellerIds)` — `review.product.seller.id`로 `groupBy`, `avg(rating)`/`count()` 한 번의 쿼리로 여러 판매자를 집계(N+1 회피, product/list-progress의 bestPrices와 동일한 이유).
- `ProductService.trustedSellerMap(sellerIds)` → `isTrustedSeller(rating)` — `list()`/`detail()`/`register()`/`update()` 네 곳 모두 이 헬퍼로 계산한다.
- **캐시 안에 포함**: 목록/상세 캐시(`PRODUCT_LIST_CACHE`/`PRODUCT_DETAIL_CACHE`, TTL 30분) 응답에 그대로 실어 보낸다 — activeTeamCurrentCount처럼 캐시 밖으로 뺀 별도 조회를 만들지 않는다. 리뷰 평균은 사용자가 실시간으로 지켜보는 값이 아니라 참고 지표라, 최대 30분 낡아도 되는 신선도로 판단(sort=POPULAR와 같은 이유).

## 프론트

- 메인 페이지 카드(`main.js`): 판매자명 옆에 "✓ 신뢰 판매자" 필배지(보라색 pill, `.card-seller-trust`), `sellerTrustedBadge`가 true일 때만.
- 상품 상세(`product.html`/`product.js`): 판매자명 옆 동일 배지(`#product-seller-trust`).

## 관련 코드

`repository/SellerRatingProjection.java`/`SellerRatingProjectionImpl.java`, `repository/ReviewRepositoryCustom.java`/`ReviewRepositoryImpl.java`, `repository/ReviewRepository.java`, `service/ProductService.java`(`trustedSellerMap`/`isTrustedSeller`), `dto/ProductResponse.java`/`ProductSummaryResponse.java`(`sellerTrustedBadge`), `static/js/main.js`(`createProductCard`), `static/product.html`+`js/product.js`, `static/css/components.css`(`.card-seller-row`/`.card-seller-trust`).
