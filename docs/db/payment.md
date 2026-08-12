# payment (결제 내역)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_id | BIGINT | NOT NULL, FK | 결제한 회원 |
| product_id | BIGINT | NOT NULL, FK | 결제한 상품 |
| team_id | BIGINT | NULL, FK | 공동구매 결제면 팀 ID, 혼자구매면 NULL |
| amount | INT | NOT NULL | 결제 금액(서버가 계산한 기대 금액 — PortOne 재조회 결과와 대조하는 기준값) |
| status | VARCHAR(20) | NOT NULL, default 'PENDING' | `PENDING`/`PAID`/`FAILED`/`REFUND_PENDING`/`REFUNDED` — 상세 전이는 `docs/dev/payment/portone/design.md` |
| pg_payment_id | VARCHAR(64) | NULL, UNIQUE | PortOne에 보낸 가맹점 채번 결제 식별자(merchant paymentId). 웹훅이 가리키는 결제 건 역조회, 취소 API 호출 대상 특정에 쓴다. 4-arg 생성자(레거시/테스트에서 "이미 확정된 결제"를 직접 만들 때)로 만든 행은 NULL일 수 있다(MySQL UNIQUE 인덱스는 NULL을 서로 다른 값으로 취급하므로 여러 행이 NULL이어도 제약 위반 아님) |
| paid_at | DATETIME | NOT NULL | 레코드 생성(결제 요청 접수) 시각. PortOne 연동 이후 `PENDING`으로 시작하므로 항상 "실제 승인 시각"과 같지는 않다 — 확정 시각을 별도 컬럼으로 관리하지는 않는다 |

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
