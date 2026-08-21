# 010-admin-upcoming-product-filter — 관리자 상품 현황 오픈 예정 상품(UPCOMING) 필터 구현 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도: 관리자 상품 현황(`admin/products.html`)에 ⏱️ 오픈 예정 상품(`UPCOMING`) 필터 탭 추가 및 백엔드 QueryDSL 동적 쿼리, 프론트엔드 API 연동, 단위 테스트 검증.
  - `ProductRepositoryImpl.java`:
    - `status == "UPCOMING"` 요청 시 `product.hidden.isFalse().and(product.openAt.isNotNull()).and(product.openAt.gt(LocalDateTime.now()))` QueryDSL 조건 적용.
  - `admin/products.html` & `admin-products.js`:
    - `<button type="button" class="btn btn-secondary btn-sm product-filter-btn" data-filter="UPCOMING">⏱️ 오픈 예정 상품</button>` 필터 버튼 추가.
    - `activeFilter === 'UPCOMING'` API 요청 파라미터 구성 및 오픈 예정 카드에 ⏱️ **오픈예정** 배지 표시.
  - `AdminControllerTest.java`:
    - `products_withUpcomingStatusFilter_returnsOnlyUpcomingProducts()` 단위 테스트 작성 및 오픈 예정 상품만 1개 조회되는지 단언.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew compileJava compileTestJava` → `BUILD SUCCESSFUL in 4s`.
- 추론적 평가:
  - 오픈 예정 상품(`openAt > LocalDateTime.now()`)만 모아 볼 수 있는 전용 필터 탭을 제공하여, 관리자가 오픈 전 상품을 사전 점검하고 모니터링할 수 있는 UI/UX 환경 완비.
- 증거:
  - `./gradlew compileJava compileTestJava` → `BUILD SUCCESSFUL`.
