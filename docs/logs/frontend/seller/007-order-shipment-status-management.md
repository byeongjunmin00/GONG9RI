# 007-order-shipment-status-management — 판매자 주문·배송 상태 실제 조작 가능하게 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도: 005의 `preparationStatus`(파생값, 조작 불가)와 별개로 `payment` 테이블에 저장되는 진짜 배송 상태(`shipment_status`/`tracking_carrier`/`tracking_number`)를 추가하고, 판매자가 4단계(상품 준비중/배송 준비중/배송중/배송완료)를 자유 토글할 수 있게 구현.
  - `ShipmentStatus.java`, `Payment.java`(필드+`updateShipment()`), `NotificationType.SHIPMENT_UPDATED`, `ErrorCode`(2종) 추가.
  - `SellerOrderResponse.derivePreparationStatus()`/`isShipmentManageable()` 정적 메서드 분리 — 005의 파생 로직을 재사용해 "이 주문이 배송 관리 대상인가"를 독립적으로 판정(PAID 결제 + preparationStatus == PREPARING만 허용).
  - `SellerMypageService.updateShipment()`: 본인 상품 스코핑 → 관리 대상 검증(`SHIPMENT_STATUS_NOT_APPLICABLE`) → 배송중/완료는 송장번호 필수(`TRACKING_NUMBER_REQUIRED`) → 반영 → 구매자 알림 발행.
  - `PATCH /api/seller/mypage/orders/{paymentId}/shipment` 신규.
  - `PurchaseResponse`(구매자용)에 같은 배송 필드를 읽기 전용으로 추가.
  - 프론트: `seller-mypage.js`에 배송 단계 select + 택배사/송장번호 입력 + 저장 버튼(항목별 즉시 갱신, 전체 재조회 없음), `buyer-mypage.js`에 읽기 전용 표시.
  - 테스트: `SellerMypageControllerTest`(`updateShipment_*` 7개), `BuyerMypageControllerTest`(purchases 배송 필드 기본값 검증), `NotificationTypesFlowTest`(`shipmentUpdatedNotifiesBuyerOnly`).
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.SellerMypageControllerTest --tests com.gong9ri.gong9ri.controller.BuyerMypageControllerTest --tests com.gong9ri.gong9ri.event.NotificationTypesFlowTest` → `BUILD SUCCESSFUL`.
- 추론적 평가:
  - 판매자가 실제로 배송 단계를 조작하고, 구매자가 그 결과(상태+택배사+송장번호)를 조회할 수 있는 "관리" 기능이 이름값대로 동작하게 됨. 확정 사항 3가지(알림 추가/외부 링크 제외/배송중·완료 송장번호 필수) 전부 반영.

## Attempt 1 부수 발견 — 로컬 실기동 중 잡은 버그 2건

- **스키마 기본값 버그**: `shipment_status` 컬럼을 `columnDefinition` 없이 자바 필드 초기값만으로 추가했더니, `ddl-auto=update`의 `ALTER TABLE ADD COLUMN`이 기존 행 전체를 자바 기본값(`PRODUCT_PREPARING`)이 아니라 **MySQL이 ENUM 컬럼에 붙이는 암묵적 기본값(정의 순서상 첫 값 — Hibernate가 enum을 알파벳순으로 나열해 실제로는 `DELIVERED`)**으로 채워버렸다. `DESCRIBE payment`와 실제 `SELECT`로 로컬 DB에서 직접 확인(기존 결제 13건 전부 `DELIVERED`로 잘못 채워져 있었음). `@Column(columnDefinition = "VARCHAR(20) DEFAULT 'PRODUCT_PREPARING'")`로 컬럼 정의를 명시해 수정하고, 로컬 DB는 `ALTER TABLE ... MODIFY COLUMN` + `UPDATE`로 직접 정정했다.
- **프론트 라이브 갱신 누락**: 저장 성공 시 배송 단계 배지는 갱신되는데 "🚚 택배사 송장번호" 텍스트 줄은 새로고침 전까지 안 바뀌는 걸 브라우저로 직접 눌러보다 발견 — `trackingEl` 참조를 `createShipmentPanel`에 넘겨 저장 성공 콜백에서 함께 갱신하도록 수정.
- 증거:
  - `docker exec mysql`로 `DESCRIBE payment` / `SELECT shipment_status, COUNT(*) FROM payment GROUP BY shipment_status` 실행해 수정 전/후 비교.
  - 로컬 브라우저(`localhost:8080`)에서 판매자 계정으로 실제 select 조작 → 저장 → 배지·트래킹 텍스트 즉시 갱신 확인, 구매자 계정에서 동일 정보 읽기 전용 확인.
