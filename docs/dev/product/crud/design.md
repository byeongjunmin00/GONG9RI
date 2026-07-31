# 상품 CRUD (product/crud) — Design

## 개요

판매자가 상품을 등록/수정/삭제하고(본인 것만), 누구나(비로그인 포함) 상품 목록·상세를 조회할 수 있다. 상품 하나는 여러 개의 가격 구간(`price_tier`)을 갖고, 목록에서는 그중 최저가(`bestPrice`)를 계산해서 보여준다.

이 기능으로 `SecurityConfig`의 인가 규칙이 엔드포인트 단위로 세분화됐고(`GET /api/products/**`는 공개, 나머지는 인증 필요), 미인증 401 응답도 공통 형식으로 통일하는 `ApiAuthenticationEntryPoint`가 추가됐다 — 이 두 가지는 이후 `team`/`payment`/`mypage`도 그대로 재사용/확장한다.

## API / 인터페이스

- `GET /api/products`, `GET /api/products/{id}`, `POST /api/products`, `PUT /api/products/{id}`, `DELETE /api/products/{id}` — 상세: `docs/api/product.md`
- 공통 응답 형식: `docs/api/README.md`, `common/response/ApiResponse.java`

## 데이터 모델

- `product`, `price_tier` — 상세: `docs/db/product.md`, `docs/db/price_tier.md`
- `Product.seller`는 `Member`에 대한 `@ManyToOne`(FK: `seller_id`), `PriceTier.product`는 `Product`에 대한 `@ManyToOne`(FK: `product_id`)

## 규칙 / 검증

- 등록/수정/삭제는 `Role.SELLER`만 가능, 수정/삭제는 본인 소유 상품만(순서: 역할 확인 → 존재 확인 → 소유권 확인) — 위반 시 `403 FORBIDDEN`
- 존재하지 않는 상품 조회/수정/삭제 시 `404 PRODUCT_NOT_FOUND`
- 목록/상세 조회는 인증 불필요(`SecurityConfig`에서 `GET /api/products/**` permitAll)
- 비로그인으로 등록/수정/삭제 시도 시 `401 UNAUTHORIZED`(공통 응답 형식, `ApiAuthenticationEntryPoint`가 처리)
- 목록의 `bestPrice`는 해당 상품 `price_tier` 중 최저가(`MIN(price)`) — product/seller를 fetch join하는 페이지네이션 쿼리와, price_tier는 페이지에 속한 상품 id들로 별도 집계 쿼리를 날려서 계산(컬렉션 fetch join + 페이지네이션을 같이 쓰면 생기는 함정을 피함)
- 상품 수정 시 기존 `price_tier`를 전부 삭제하고 요청받은 구간표로 재삽입(`docs/db/price_tier.md` 정책)
- 캐싱은 `docs/policy/caching.md`에 따라 이번 MVP 단계에서 구현하지 않음(정책만 확인, 코드 없음)

## 관련 코드 위치

- `entity/{Product,PriceTier}.java`
- `dto/{PriceTierRequest,PriceTierResponse,ProductRegisterRequest,ProductResponse,ProductSummaryResponse,ProductPageResponse}.java`
- `repository/{ProductRepository,PriceTierRepository,BestPriceProjection}.java`
- `service/ProductService.java`
- `controller/ProductController.java`
- `config/SecurityConfig.java` — `GET /api/products/**` permitAll 추가
- `common/security/ApiAuthenticationEntryPoint.java` — 공통 401 응답 형식
- `common/exception/ErrorCode.java` — `PRODUCT_NOT_FOUND`/`FORBIDDEN`/`UNAUTHORIZED` 추가
- 테스트: `src/test/.../controller/ProductControllerTest.java` (12케이스, `SecurityMockMvcRequestPostProcessors.authentication()`로 로그인 시뮬레이션)
