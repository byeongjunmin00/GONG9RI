# 상품 CRUD (product/crud)

대상: product/crud
담당: 민병준

## 배경 / 요구

상품 등록/목록/상세/수정/삭제 5개 엔드포인트. `docs/api/product.md` 계약대로 구현하며, 목록/상세는 비로그인 공개, 등록/수정/삭제는 판매자 본인만 가능하도록 한다. 이번에 처음으로 `SecurityConfig`의 "인증 필요" 규칙이 실제로 발동하므로, 미인증 401 응답도 공통 형식으로 맞춘다.

## 설계

- `Product`(→`Member` ManyToOne), `PriceTier`(→`Product` ManyToOne) 엔티티
- 목록 조회: `Product`+`seller` fetch join(페이지네이션과 함께 안전) + `price_tier`는 별도 집계 쿼리(MIN(price))로 `bestPrice` 계산 — 컬렉션 fetch join과 페이지네이션을 같이 쓰면 생기는 함정을 피하기 위함
- 상세 조회: `Product`+`seller` fetch join, `price_tier`는 별도 조회
- 등록/수정: 역할 검사(SELLER만) → (수정/삭제는) 존재 확인 → 소유권 확인 순서
- `SecurityConfig`: `GET /api/products/**` permitAll 추가, 나머지는 인증 필요 유지. 커스텀 `AuthenticationEntryPoint`로 미인증 401을 공통 응답 형식으로 통일
- 참고 계약: `docs/api/product.md`, `docs/db/product.md`, `docs/db/price_tier.md`, `docs/code-convention.md`(N+1)

## 태스크

- [ ] `Product`, `PriceTier` 엔티티
- [ ] 등록/수정 요청 DTO, 목록/상세 응답 DTO
- [ ] `ProductRepository`(fetch join+페이지네이션), `PriceTierRepository`(bestPrice 집계, 삭제)
- [ ] `ProductService` (역할/소유권 검사 포함)
- [ ] `ProductController` (5개 엔드포인트)
- [ ] `SecurityConfig` 수정 + `ApiAuthenticationEntryPoint`
- [ ] `ErrorCode`에 `PRODUCT_NOT_FOUND`/`FORBIDDEN`/`UNAUTHORIZED` 추가
- [ ] 테스트(목록/상세/등록성공·권한없음·미인증·검증실패/수정/삭제)

## 평가(통과) 기준

- 목록: 비로그인 200, 페이지네이션, `bestPrice` 포함
- 상세: 비로그인 200(존재)/404(미존재), `priceTiers` 전체 포함
- 등록: SELLER 201 / BUYER 403 / 비로그인 401 / 필드누락 400
- 수정·삭제: 본인 성공 / 타인·BUYER 403 / 없는 상품 404 / 비로그인 401
- `./gradlew test` 통과
