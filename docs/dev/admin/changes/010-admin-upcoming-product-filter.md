# 관리자 상품 현황 오픈 예정 상품(UPCOMING) 필터 구현

대상: backend/frontend/admin
담당: 전용운

## 배경 / 요구사항

현재 관리자 상품 현황(`admin/products.html`)에는 `전체`, `공개`, `⚠️ 숨김/제재`, `🚀 추천/인기 푸시` 필터 탭만 존재하여 **오픈 예정 상품(`openAt > LocalDateTime.now()`)만 따로 모아서 모니터링할 수가 없었다.**

- **목표**: 관리자가 오픈 예정 상품들만 한눈에 확인하고 사전 점검 및 관리를 수행할 수 있도록, ⏱️ **오픈 예정 상품 (`UPCOMING`) 필터 탭**을 추가하고 백엔드 QueryDSL 동적 쿼리 및 프론트엔드 API 연동, 단위 테스트 검증을 완비했다.

## 설계 및 구현 내용

1. **백엔드 QueryDSL 동적 쿼리 조건 추가 (`ProductRepositoryImpl.java`)**:
   - `status == "UPCOMING"` 요청 시 QueryDSL 조건 적용:
     `product.hidden.isFalse().and(product.openAt.isNotNull()).and(product.openAt.gt(LocalDateTime.now()))`
   - 오픈 예정 상품만 정확하게 DB 쿼리 레벨에서 정교하게 필터링.

2. **프론트엔드 UI & 필터 탭 추가 (`admin/products.html` & `admin-products.js`)**:
   - `admin/products.html`: `<button type="button" class="btn btn-secondary btn-sm product-filter-btn" data-filter="UPCOMING">⏱️ 오픈 예정 상품</button>` 필터 버튼 추가.
   - `admin-products.js`: `activeFilter === 'UPCOMING'` 일 때 `GET /api/admin/products?status=UPCOMING` 파라미터 전송.
   - 오픈 예정 상품 카드에 ⏱️ **오픈예정** 배지 노출.

3. **단위 테스트 및 검증 (`AdminControllerTest.java`)**:
   - `products_withUpcomingStatusFilter_returnsOnlyUpcomingProducts()` 단위 테스트 케이스 추가.
   - 미래 시각의 `openAt`을 가진 오픈 예정 상품과 일반 공개 상품을 저장 후 `status=UPCOMING` 파라미터 조회 시 오픈 예정 상품만 1개 필터링되어 반환되는지 단언.

## 변경된 파일 목록

- `src/main/java/com/gong9ri/gong9ri/repository/ProductRepositoryImpl.java`: `status=UPCOMING` QueryDSL 조건 구현
- `src/main/resources/static/admin/products.html`: ⏱️ 오픈 예정 상품 필터 버튼 추가
- `src/main/resources/static/js/admin-products.js`: `UPCOMING` 필터 API 연동 및 ⏱️ 오픈예정 배지 표시
- `src/test/java/com/gong9ri/gong9ri/controller/AdminControllerTest.java`: `products_withUpcomingStatusFilter` 단위 테스트 구현
- `docs/api/admin.md`: `status=UPCOMING` API 명세 추가
- `docs/dev/admin/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/admin/010-admin-upcoming-product-filter.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava compileTestJava` 빌드 검증 성공.
- `status=UPCOMING` 파라미터 전달 시 `openAt`이 미래인 상품만 정확히 반환됨을 확인.
