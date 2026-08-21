# 005-seller-order-shipment-management — 판매자 마이페이지 주문/결제 내역 및 배송 준비 상태 관리 구현 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도: 판매자 마이페이지(`seller/mypage.html`)에 📦 **주문·배송 관리** 탭 신설 및 구매자 정보, 결제 금액, 상품 정보, 배송 준비 상태 조회 백엔드 API & 프론트엔드 연동.
  - `SellerOrderResponse.java`:
    - `paymentId`, `buyerName`, `buyerEmail`, `productId`, `productName`, `amount`, `status`, `paidAt`, `teamId`, `teamStatus`, `preparationStatus`, `preparationStatusLabel` (🚚 배송 준비 중 / ⏳ 공구 모집 중 / 🔄 환불됨).
  - `PaymentRepository.java`:
    - `findAllBySellerIdWithProductAndMemberAndTeam(sellerId)` N+1 방지 패치 조인 JPQL 구현.
  - `SellerMypageService.java` & `SellerMypageController.java`:
    - `GET /api/seller/mypage/orders` 엔드포인트 구현.
  - `seller/mypage.html` & `js/seller-mypage.js`:
    - 📦 **주문·배송 관리** 탭 버튼 및 `loadOrders()` 카드 DOM 렌더링 구현.
  - `SellerMypageControllerTest.java`:
    - `orders_asSeller_returnsSellerOrdersWithBuyerInfo()` 단위/통합 테스트 구현.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.SellerMypageControllerTest` → `BUILD SUCCESSFUL in 14s`.
- 추론적 평가:
  - 판매자가 자신의 상품을 결제한 구매자의 이름/이메일 정보와 결제 일시, 금액 및 배송 준비 진행 단계(공구 성공 시 배송 준비 중 🚚, 공구 진행 중 ⏳, 환불됨 🔄)를 한눈에 파악할 수 있는 완전한 주문·배송 관리 UX 환경 구축 완료.
- 증거:
  - `./gradlew test --tests SellerMypageControllerTest` → `BUILD SUCCESSFUL`.

## Attempt 2 — 2026-08-21  ✅ PASS (리뷰 보완 — 테스트 커버리지)

- 시도: 코드 리뷰 중 `orders` 엔드포인트가 이 테스트 파일의 기존 관례(성공/스코핑/403/401 4종 세트, `products`·`refund-requests` 등 모든 다른 엔드포인트가 이 패턴을 따름)와 달리 성공 케이스 1개만 있는 것을 확인 — 나머지 3개 추가.
  - `SellerMypageControllerTest.java`: `orders_scoping_onlyOwnSalesPayments`(다른 판매자 결제 건 비노출), `orders_forbidden_buyer`(구매자 계정 403), `orders_unauthorized`(비로그인 401) 추가.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.SellerMypageControllerTest` → `BUILD SUCCESSFUL in 15s` (7개 테스트 전체 통과).

## Attempt 3 — 2026-08-21  ✅ PASS (리뷰 발견 버그 수정 + 회귀 테스트)

- 시도: 리뷰에서 발견된 버그 수정.
  - `PaymentRepository.findAllBySellerIdWithProductAndMemberAndTeam`에 `AND p.status <> 'PENDING' AND p.status <> 'FAILED'` 추가 — 결제 미확정/실패 건이 `preparationStatus` 파생 로직에서 전부 `PREPARING`(배송 준비 중)으로 잘못 표시되던 문제 수정(안티그래비티가 수정, 이 세션이 검증).
  - `SellerMypageControllerTest.java`: `orders_excludesPendingAndFailedPayments`(PENDING·FAILED 결제 각 1건 + PAID 1건을 seed하고 PAID만 반환되는지 검증) 추가.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.SellerMypageControllerTest` → `BUILD SUCCESSFUL in 16s` (8개 테스트 전체 통과).
