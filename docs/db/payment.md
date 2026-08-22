# payment (결제 내역)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| order_no | VARCHAR(20) | NULL (백필 후 NOT NULL, UNIQUE 예정) | 주문번호(admin-identifier-codes, 2026-08-22 추가). `"O" + paidAt(yyyyMMdd) + "-" + PK 6자리 zero-pad`(`O20260822-000001`, `docs/policy/identifier-code.md`) — 회원번호/상품코드/공구팀 번호와 달리 날짜 접두어가 있다(정산 대사·일자별 CS 조회 편의). 결제 요청 접수 직후 자동 채번. **지금은 nullable이다**(`member.member_code`와 동일한 마이그레이션 사정). **이번 라운드는 admin 어디에도 노출하지 않는다** — admin 전용 주문 목록 화면이 아직 없어서다(`docs/dev/ongoing/admin-identifier-codes.md` "확정 4", 다음 작업으로 이연) |
| member_id | BIGINT | NOT NULL, FK | 결제한 회원 |
| product_id | BIGINT | NOT NULL, FK | 결제한 상품 |
| team_id | BIGINT | NULL, FK | 공동구매 결제면 팀 ID, 혼자구매면 NULL |
| amount | INT | NOT NULL | 결제 금액(서버가 계산한 기대 금액 — PortOne 재조회 결과와 대조하는 기준값) |
| status | VARCHAR(20) | NOT NULL, default 'PENDING' | `PENDING`/`PAID`/`FAILED`/`REFUND_PENDING`/`REFUNDED` — 상세 전이는 `docs/dev/payment/portone/design.md` |
| pg_payment_id | VARCHAR(64) | NULL, UNIQUE | PortOne에 보낸 가맹점 채번 결제 식별자(merchant paymentId). 웹훅이 가리키는 결제 건 역조회, 취소 API 호출 대상 특정에 쓴다. 4-arg 생성자(레거시/테스트에서 "이미 확정된 결제"를 직접 만들 때)로 만든 행은 NULL일 수 있다(MySQL UNIQUE 인덱스는 NULL을 서로 다른 값으로 취급하므로 여러 행이 NULL이어도 제약 위반 아님) |
| paid_at | DATETIME | NOT NULL | 레코드 생성(결제 요청 접수) 시각. PortOne 연동 이후 `PENDING`으로 시작하므로 항상 "실제 승인 시각"과 같지는 않다 — 확정 시각을 별도 컬럼으로 관리하지는 않는다 |
| shipment_status | VARCHAR(20) | NOT NULL, default 'PRODUCT_PREPARING' | 판매자가 직접 조작하는 배송 단계(007) — `PRODUCT_PREPARING`/`SHIPPING_PREPARING`/`IN_TRANSIT`/`DELIVERED`. `status`(결제 상태)와 별개이며, 순서 강제 없이 자유롭게 전환 가능. `PAID`가 아니거나(REFUNDED 등) 공구팀이 RECRUITING/FAILED인 주문은 변경이 거절된다(`SellerOrderResponse.isShipmentManageable`) |
| tracking_carrier | VARCHAR(50) | NULL | 택배사명(자유 텍스트, 판매자 입력) |
| tracking_number | VARCHAR(50) | NULL | 송장번호. `shipment_status`가 `IN_TRANSIT`/`DELIVERED`면 필수(서비스 레이어에서 검증, 컬럼 자체는 NULL 허용 — 그 이전 단계에서는 비어있는 게 정상) |

## 인덱스
- `idx_member` (member_id) — 구매자 마이페이지 "구매 완료 목록"용
- `idx_team_status` (team_id, status) — 실패 판정 스케줄러가 팀별 `PAID` 결제 일괄 조회할 때 사용
- `idx_product` (product_id) — 판매자 마이페이지 "수익 현황" 집계용
- `idx_pg_payment_id` (pg_payment_id, UNIQUE) — PortOne 웹훅이 가리키는 결제 건 역조회(`findByPgPaymentId`)

## 관계
- member_id → member.id
- product_id → product.id
- team_id → group_buy_team.id (nullable)

## 사용하는 기능
- payment/crud(요청 접수), payment/portone(서버 확정·웹훅·환불), mypage/buyer-purchases, mypage/seller-revenue, team/deadline-check(스케줄러, 환불취소 요청 트리거)

## 삭제 정책
- 하드 삭제 없음 (`REFUNDED`로 상태만 전환, row는 유지 — 결제 이력 보존 필요)
