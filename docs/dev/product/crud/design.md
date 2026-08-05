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
- **캐싱** (`docs/policy/caching.md`): `ProductService.list(page, size)`와 `ProductService.detail(productId)`를 캐싱한다.
  - **캐시 대상/키**: 목록 — `CacheConfig.PRODUCT_LIST_CACHE`(이름 `"productList"`), 키는 `page`+`size` 조합. 상세 — `CacheConfig.PRODUCT_DETAIL_CACHE`(이름 `"productDetail"`), 키는 `productId`.
  - **무효화**: `register()` 완료 시 목록 캐시 **전체** 무효화(`@CacheEvict(allEntries = true)`) — `findAllWithSeller`에 `ORDER BY`가 없어 신규 상품이 어느 페이지에 들어갈지 특정 불가하기 때문(정책 문서엔 없었지만 이번 작업에서 사용자와 협의해 스코프에 포함). `update()`/`delete()` 완료 시 해당 `productId`의 상세 캐시 + 목록 캐시 전체를 `@Caching`으로 함께 무효화 — 이름/가격 변경 시 그 상품이 포함되는 페이지가 달라질 수 있어 특정 페이지만 지울 수 없다.
  - **직렬화**: `mypage/seller-revenue`에서 확정한 방식과 동일하게, 캐시별로 타입을 고정한 `JacksonJsonRedisSerializer<>(대상타입.class)`를 값 직렬화기로 명시(`CacheConfig`의 `productListCacheCustomizer()`/`productDetailCacheCustomizer()`) — 기본 `JdkSerializationRedisSerializer`가 non-serializable record를 거부하는 문제와, 범용 JSON 직렬화기가 타입 정보를 잃어 캐시 히트 시 `LinkedHashMap`으로 역직렬화되는 문제를 둘 다 피한다.
  - **TTL**: 목록·상세 각각 30분(무효화가 "전체 무효화" 방식이라 세밀한 `sellerRevenue`(10분)보다 적중 기간의 가치를 더 크게 봐서 길게 설정) — 무효화 누락에 대비한 안전장치.
  - 캐싱 로직은 Service 계층(`ProductService`)에만 있다(Controller·Repository 미개입).

## 관련 코드 위치

- `entity/{Product,PriceTier}.java`
- `dto/{PriceTierRequest,PriceTierResponse,ProductRegisterRequest,ProductResponse,ProductSummaryResponse,ProductPageResponse}.java`
- `repository/{ProductRepository,PriceTierRepository,BestPriceProjection}.java`
- `service/ProductService.java`
- `controller/ProductController.java`
- `config/SecurityConfig.java` — `GET /api/products/**` permitAll 추가
- `common/security/ApiAuthenticationEntryPoint.java` — 공통 401 응답 형식
- `common/exception/ErrorCode.java` — `PRODUCT_NOT_FOUND`/`FORBIDDEN`/`UNAUTHORIZED` 추가
- `config/CacheConfig.java` — 상품 목록/상세 캐시(`productList`/`productDetail`) TTL·값 직렬화기 설정
- 테스트: `src/test/.../controller/ProductControllerTest.java` (12케이스, `SecurityMockMvcRequestPostProcessors.authentication()`로 로그인 시뮬레이션)
- 테스트: `service/ProductCachingTest.java`(5케이스, `@SpringBootTest`) — 캐시 히트(목록/상세)·무효화(register/update/delete) 시나리오. `config/CacheConfigTest.java`(순수 단위 테스트) — 값 직렬화기가 non-serializable record(`ProductPageResponse`/`ProductResponse`, 중첩 `ProductSummaryResponse`/`PriceTierResponse` 포함)를 실제로 write/read 왕복시킬 수 있는지 검증
