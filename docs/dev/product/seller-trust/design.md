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
- **캐시 안에 포함**: 목록/상세 캐시(`PRODUCT_LIST_CACHE`/`PRODUCT_DETAIL_CACHE`, TTL 30분) 응답에 그대로 실어 보낸다 — activeTeamCurrentCount처럼 캐시 밖으로 뺀 별도 조회를 만들지 않는다. 이 결정(배지를 캐시 안에 둔다)은 유지한다.
- **리뷰 작성·수정·삭제 시 캐시 무효화**(2026-08-20 추가). 원래는 "리뷰 평균은 실시간으로 지켜보는 값이 아니라 참고 지표"라는 이유로 최대 30분 staleness를 의도적으로 허용했었다. 그런데 실제로 배지 조건(리뷰 3개·평균 4.5)을 갓 채운 판매자 화면에서 배지가 안 뜨는 걸 확인하고, 이 트레이드오프를 바꿨다 — "조건을 만족시켰는데 아무 일도 안 일어난다"는 체감이 나쁘고, 리뷰 작성은 상품 조회에 비해 훨씬 드물어 캐시 적중률 손해가 거의 없다.
  - `ReviewService.create/update/delete`에 `@CacheEvict`. 상세 캐시는 `key = "#productId"`가 아니라 **`allEntries = true`** — 배지는 판매자의 *전체 상품* 리뷰를 합산해 판정하므로, 상품 A에 리뷰가 달리면 같은 판매자의 상품 B·C 배지까지 바뀐다. 리뷰가 달린 상품 하나만 날리면 나머지가 낡은 값으로 남는다.
  - 검증: `ReviewCachingTest` — 5점 리뷰 3개로 조건을 채운 뒤 `detail()`이 캐시된 `false`가 아니라 `true`를 돌려주는지 확인한다(무효화를 제거하면 실제로 실패하는 것까지 확인함).
  - **캐시 무효화가 트랜잭션 커밋 이후에만 실행되도록 순서 고정(2026-08-20, `changes/003`)**: `@Transactional`과 `@CacheEvict`를 같은 메서드에 함께 쓰면 AOP 어드바이저 순서를 명시하지 않는 한 무효화가 커밋보다 먼저 실행될 수 있어(그 틈에 동시 조회가 커밋 전 값으로 캐시를 다시 채우는 레이스), 리뷰로 배지 조건을 채워도 배지가 안 뜨는 버그가 좁은 확률로 재발할 수 있었다. `CacheConfig`의 `@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)`로 캐싱 어드바이저를 트랜잭션 어드바이저(기본값)보다 항상 바깥쪽에 둬서 "커밋 → 무효화" 순서를 구조적으로 보장한다(`ProductService`의 동일 패턴에도 함께 적용됨 — `docs/policy/caching.md` 참고). `CacheEvictionOrderingTest`가 이 순서를 고정 검증한다.

## 프론트

- 메인 페이지 카드(`main.js`): 판매자명 옆에 "✓ 신뢰 판매자" 필배지(보라색 pill, `.card-seller-trust`), `sellerTrustedBadge`가 true일 때만.
- 상품 상세(`product.html`/`product.js`): 판매자명 옆 동일 배지(`#product-seller-trust`).

## 관련 코드

`repository/SellerRatingProjection.java`/`SellerRatingProjectionImpl.java`, `repository/ReviewRepositoryCustom.java`/`ReviewRepositoryImpl.java`, `repository/ReviewRepository.java`, `service/ProductService.java`(`trustedSellerMap`/`isTrustedSeller`), `dto/ProductResponse.java`/`ProductSummaryResponse.java`(`sellerTrustedBadge`), `static/js/main.js`(`createProductCard`), `static/product.html`+`js/product.js`, `static/css/components.css`(`.card-seller-row`/`.card-seller-trust`), `config/CacheConfig.java`(`@EnableCaching(order = ...)`, 캐시 무효화 순서 고정, 2026-08-20).

테스트: `ReviewCachingTest`, `config/CacheEvictionOrderingTest.java`(신규, 2026-08-20 — 캐싱 어드바이저가 트랜잭션 어드바이저보다 항상 바깥쪽인지 검증).
