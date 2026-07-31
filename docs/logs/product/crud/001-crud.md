# 001-crud — 상품 CRUD (로그)

## Attempt 1 — 2026-07-31  ✅ PASS
- 시도: `Product`/`PriceTier` 엔티티, DTO 6종, `ProductRepository`(fetch join+페이지네이션, count 쿼리 분리), `PriceTierRepository`(bestPrice 집계 쿼리, deleteByProductId), `ProductService`(역할→존재→소유권 순 검증), `ProductController`(5개 엔드포인트, `@AuthenticationPrincipal`로 로그인 사용자 주입), `SecurityConfig`(`GET /api/products/**` permitAll 추가), `ApiAuthenticationEntryPoint`(미인증 401 공통 응답 형식화), `ErrorCode`에 `PRODUCT_NOT_FOUND`/`FORBIDDEN`/`UNAUTHORIZED` 추가. 테스트는 `spring-security-test`의 `SecurityMockMvcRequestPostProcessors.authentication()`으로 로그인 상태를 시뮬레이션(실제 로그인 API 호출 없이 `MemberUserDetails`를 principal로 직접 주입).
- 결과: `./gradlew build` 첫 시도에 전체 통과. `ProductControllerTest` 12케이스 + 기존 `AuthControllerTest` 7케이스 + `Gong9riApplicationTests` 1케이스, 총 20케이스 전부 성공.
- 참고: `SecurityMockMvcRequestPostProcessors` 패키지는 이전에 겪었던 Jackson/MockMvc처럼 이동되지 않고 표준 위치(`org.springframework.security.test.web.servlet.request`)에 그대로 있었음 — 사용 전에 jar를 직접 까서 확인한 뒤 작성해서 이번엔 한 번에 통과함.
- 증거(API 샘플, MockMvc):
  - `GET /api/products`(비로그인) → `200 {"success":true,"data":{"content":[{"bestPrice":15000,"sellerName":"테스트유저",...}],"page":0,"size":20,"totalElements":1}}`
  - `GET /api/products/{id}`(존재) → `200`, `priceTiers` 2건 포함
  - `GET /api/products/999999` → `404 {"code":"PRODUCT_NOT_FOUND"}`
  - `POST /api/products`(SELLER 로그인) → `201`, `priceTiers` 반영 확인
  - `POST /api/products`(BUYER 로그인) → `403 {"code":"FORBIDDEN"}`
  - `POST /api/products`(비로그인) → `401 {"success":false,"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}` — Spring Security 기본 401이 아니라 우리 공통 형식으로 나오는 것 확인(이번 기능의 핵심 목표)
  - `PUT/DELETE /api/products/{id}`(타인 소유) → `403 FORBIDDEN`
- DB 증거: `product`/`price_tier` 테이블 스키마가 `docs/db/*.md`와 일치(`DESCRIBE`로 확인). 테스트가 `@Transactional`이라 실행 후 `member`/`product`/`price_tier` 전부 0건 확인(DB 오염 없음).
