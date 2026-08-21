# 판매자 마이페이지 주문/결제 내역 및 배송 준비 상태 관리 구현

대상: backend/frontend/seller
담당: 전용운

## 배경 및 요구사항

기존 판매자 마이페이지(`seller/mypage.html`)에는 등록 상품, 공구 현황, 환불 관리, 계정 설정 탭만 존재하여 **"누가 내 제품을 결제했는지 (구매자 정보)"** 및 **"그 결제 건의 주문/배송 준비 상태가 어디까지 진행되었는지"**를 파악할 수 없었다.

- **목표**:
  - 판매자가 자신의 상품을 결제한 구매자의 정보(이름, 이메일, 결제 금액, 결제 일시)와 해당 결제 상품의 배송/주문 준비 상태(🚚 배송 준비 중, ⏳ 공구 모집 중, 🔄 환불됨)를 조회할 수 있는 **"주문·배송 관리"** 탭 및 백엔드 API, 프론트엔드 UI를 구축했다.

## 상세 설계 및 백엔드/프론트엔드 연동

1. **DTO 신설 (`SellerOrderResponse.java`)**:
   - `paymentId`, `buyerName`, `buyerEmail`, `productId`, `productName`, `amount`, `status`, `paidAt`, `teamId`, `teamStatus`, `preparationStatus`, `preparationStatusLabel` (🚚 배송 준비 중 / ⏳ 공구 모집 중 / 🔄 환불/취소됨).

2. **Repository JPQL 신설 (`PaymentRepository.java`)**:
   - `findAllBySellerIdWithProductAndMemberAndTeam(sellerId)` 패치 조인 JPQL 구현으로 N+1 쿼리 없이 판매 상품 결제 내역과 구매자, 상품, 팀 정보 한 번에 받아옴.

3. **Service & Controller 추가 (`SellerMypageService.java`, `SellerMypageController.java`)**:
   - `GET /api/seller/mypage/orders` 엔드포인트 신설.
   - 판매자 본인 검증(`requireSeller`) 후 `List<SellerOrderResponse>` 변환 리턴.

4. **프론트엔드 UI & 탭 연동 (`seller/mypage.html`, `js/seller-mypage.js`)**:
   - `seller/mypage.html`: 📦 **주문·배송 관리** 탭 버튼 및 탭 패널 추가.
   - `js/seller-mypage.js`: `loadOrders()` 구현하여 `/api/seller/mypage/orders` 호출 및 👤 구매자 정보(이름, 이메일), 📦 상품명/금액, 🚚 배송 준비 중 / ⏳ 공구 모집 중 / 🔄 환불됨 상태 배지 렌더링.

5. **단위/통합 테스트 작성 (`SellerMypageControllerTest.java`)**:
   - `orders_asSeller_returnsSellerOrdersWithBuyerInfo()` 테스트 작성 및 성공 검증.

## 변경된 파일 목록

- `src/main/java/com/gong9ri/gong9ri/dto/SellerOrderResponse.java`: 신규 DTO
- `src/main/java/com/gong9ri/gong9ri/repository/PaymentRepository.java`: N+1 방지 패치 조인 JPQL 구현
- `src/main/java/com/gong9ri/gong9ri/service/SellerMypageService.java`: `orders()` 서비스 메서드 구현
- `src/main/java/com/gong9ri/gong9ri/controller/SellerMypageController.java`: `GET /api/seller/mypage/orders` 컨트롤러 구현
- `src/main/resources/static/seller/mypage.html`: 📦 주문·배송 관리 탭 및 패널 추가
- `src/main/resources/static/js/seller-mypage.js`: `loadOrders()` 및 주문/결제 카드 렌더링 작성
- `src/test/java/com/gong9ri/gong9ri/controller/SellerMypageControllerTest.java`: 통합 테스트 케이스 작성
- `docs/dev/mypage/view/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/seller/005-seller-order-shipment-management.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew test --tests com.gong9ri.gong9ri.controller.SellerMypageControllerTest` 실행 결과 `BUILD SUCCESSFUL in 14s` 통과.
- 판매자가 주문·배송 내역을 조회할 때 구매자 이름, 이메일, 결제 금액, 상품 정보 및 배송 준비 상태가 정확히 반환됨을 확인.
