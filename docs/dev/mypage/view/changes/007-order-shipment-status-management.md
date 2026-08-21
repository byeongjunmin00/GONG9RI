# 판매자 주문·배송 상태 실제 조작 가능하게

대상: mypage/view
담당: 전용운

## 배경 및 요구사항

005(주문·배송 관리 탭)에서 판매자에게 보여주는 `preparationStatus`는 DB에 저장되는 값이 아니라 결제 상태·공구팀 상태로부터 매 조회 때 계산하는 파생값이었다. 그래서 이름은 "주문·배송 **관리**" 탭인데 실제로 판매자가 할 수 있는 조작이 하나도 없었다 — 사용자가 로컬에서 직접 눌러보고 지적함: "탭에서 내가 할 수 있는 게 아무것도 없다".

- **목표**: 판매자가 주문 각각의 배송 단계(상품 준비중 → 배송 준비중 → 배송중 → 배송완료)를 자유롭게 토글하고, 택배사·송장번호를 입력해 구매자가 실제로 배송 정보를 볼 수 있는 수준까지 만든다.

## 확정 사항 (사용자 승인, 2026-08-21)

1. 배송 상태가 바뀌면 구매자에게 알림(`SHIPMENT_UPDATED`)을 보낸다.
2. 외부 배송조회 딥링크(스마트택배 등)는 이번 스코프에서 뺀다 — 택배사명+송장번호 텍스트 표시까지만.
3. `배송중`/`배송완료`로 바꿀 때만 송장번호를 필수로 한다. 그 외 단계는 자유 입력.

## 상세 설계 및 구현 내용

1. **DB/엔티티**:
   - `payment` 테이블에 `shipment_status`(기본값 `PRODUCT_PREPARING`)/`tracking_carrier`/`tracking_number` 컬럼 추가.
   - `entity/ShipmentStatus.java` 신규(4단계 enum + 한글 라벨), `Payment.updateShipment()` 도메인 메서드 추가(검증 없이 값만 반영 — 검증은 서비스 레이어).
2. **판정 로직 재사용**: `SellerOrderResponse.derivePreparationStatus()`(005 리뷰에서 발견한 버그 수정과 동일 로직)를 정적 메서드로 분리하고, `isShipmentManageable(Payment)`를 새로 추가해 "이 주문이 실제로 배송 관리 대상인가"(`PAID` 결제 + `preparationStatus == PREPARING`)를 독립적으로 판정한다.
3. **서비스**: `SellerMypageService.updateShipment()` — 본인 상품 스코핑 → `isShipmentManageable` 검증 → `IN_TRANSIT`/`DELIVERED`는 송장번호 필수 검증 → 반영 → `NotificationPublisher.shipmentUpdated()`로 구매자에게 알림.
4. **API**: `PATCH /api/seller/mypage/orders/{paymentId}/shipment` 신규. `GET .../orders`(판매자)와 `GET /api/buyer/mypage/purchases`(구매자, 읽기 전용) 응답에 배송 필드 추가.
5. **프론트**: `seller/mypage.html`/`js/seller-mypage.js` — 주문 카드에 배송 단계 select(4개) + 택배사/송장번호 입력 + 저장 버튼(환불됐거나 배송 대상이 아닌 주문엔 노출 안 함). 저장 성공 시 전체 목록 재조회 없이 배지·트래킹 표시를 그 항목만 즉시 갱신. `js/buyer-mypage.js` — 구매 내역에 배송 상태·택배사·송장번호 읽기 전용 표시.
6. **알림**: `NotificationType.SHIPMENT_UPDATED` 신규, `NotificationPublisher.shipmentUpdated()`(자기 상품을 자기가 산 경우는 발송 안 함, 기존 패턴과 동일).

## 리뷰/실측 중 발견해 함께 고친 것

- **스키마 기본값 버그(로컬 실측으로 발견)**: `shipment_status`를 자바 필드 초기값(`PRODUCT_PREPARING`)만 믿고 `columnDefinition` 없이 추가했더니, 로컬에서 `ddl-auto=update`의 `ALTER TABLE ADD COLUMN`이 기존 행 전체를 **자바 기본값이 아니라 MySQL의 암묵적 ENUM 기본값(알파벳순 첫 값 = `DELIVERED`)**으로 채워버렸다 — `DESCRIBE payment`/`SELECT`로 실제 확인. 이미 배송된 적 없는 옛 결제가 전부 "배송완료"로 잘못 표시되는 상태였다. `@Column(columnDefinition = "VARCHAR(20) DEFAULT 'PRODUCT_PREPARING'")`로 컬럼 정의를 명시하고, 로컬 DB는 `ALTER TABLE ... MODIFY COLUMN` + `UPDATE`로 직접 정정했다.
- **프론트 라이브 갱신 누락**: 저장 성공 직후 배송 단계 배지는 갱신되는데 "🚚 택배사 송장번호" 텍스트 줄은 페이지를 새로고침해야만 반영되는 걸 로컬 브라우저에서 직접 눌러보다 발견 — `trackingEl`을 `createShipmentPanel`에 넘겨 저장 성공 콜백에서 함께 갱신하도록 수정.

## 변경된 파일 목록

- `src/main/java/com/gong9ri/gong9ri/entity/ShipmentStatus.java`: 신규
- `src/main/java/com/gong9ri/gong9ri/entity/Payment.java`: 필드 3개 + `updateShipment()` 추가
- `src/main/java/com/gong9ri/gong9ri/entity/NotificationType.java`: `SHIPMENT_UPDATED` 추가
- `src/main/java/com/gong9ri/gong9ri/common/exception/ErrorCode.java`: `SHIPMENT_STATUS_NOT_APPLICABLE`, `TRACKING_NUMBER_REQUIRED` 추가
- `src/main/java/com/gong9ri/gong9ri/dto/SellerOrderResponse.java`: 배송 필드 추가, `derivePreparationStatus()`/`isShipmentManageable()` 분리
- `src/main/java/com/gong9ri/gong9ri/dto/PurchaseResponse.java`: 배송 필드 읽기 전용 추가
- `src/main/java/com/gong9ri/gong9ri/dto/ShipmentUpdateRequest.java`: 신규
- `src/main/java/com/gong9ri/gong9ri/service/SellerMypageService.java`: `updateShipment()` 추가
- `src/main/java/com/gong9ri/gong9ri/service/NotificationPublisher.java`: `shipmentUpdated()` 추가
- `src/main/java/com/gong9ri/gong9ri/controller/SellerMypageController.java`: `PATCH .../orders/{paymentId}/shipment` 추가
- `src/main/resources/static/js/seller-mypage.js`: 배송 관리 패널(select+입력+저장)
- `src/main/resources/static/js/buyer-mypage.js`: 배송 정보 읽기 전용 표시
- `src/test/java/com/gong9ri/gong9ri/controller/SellerMypageControllerTest.java`: `updateShipment_*` 7개 테스트
- `src/test/java/com/gong9ri/gong9ri/controller/BuyerMypageControllerTest.java`: `purchases_success`에 배송 필드 기본값 검증 추가
- `src/test/java/com/gong9ri/gong9ri/event/NotificationTypesFlowTest.java`: `shipmentUpdatedNotifiesBuyerOnly` 추가
- `docs/db/payment.md`, `docs/api/mypage.md`, `docs/dev/mypage/view/design.md`: 갱신
- `docs/logs/frontend/seller/007-order-shipment-status-management.md`: 실행 로그

## 평가 결과

- `./gradlew test --tests SellerMypageControllerTest --tests BuyerMypageControllerTest --tests NotificationTypesFlowTest` → `BUILD SUCCESSFUL` (신규 테스트 전체 통과).
- 로컬(`./gradlew bootRun`)에 실제로 띄워서 판매자 계정으로 배송 단계를 4번 바꿔보고(송장번호 없이 배송중 시도 → 거절 메시지 확인 → 송장번호 입력 후 재시도 → 성공 → 배송완료로 재변경), 구매자 계정에서 같은 정보가 읽기 전용으로 보이는 것까지 직접 확인.
- 스키마 기본값 버그를 실측(`DESCRIBE`/`SELECT`)으로 잡아 `columnDefinition` 수정 + 로컬 DB 정정.
