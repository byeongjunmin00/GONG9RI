# payment (결제 내역)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_id | BIGINT | NOT NULL, FK | 결제한 회원 |
| product_id | BIGINT | NOT NULL, FK | 결제한 상품 |
| team_id | BIGINT | NULL, FK | 공동구매 결제면 팀 ID, 혼자구매면 NULL |
| amount | INT | NOT NULL | 결제 금액 |
| status | VARCHAR(20) | NOT NULL, default 'PAID' | `PAID` / `REFUNDED` |
| paid_at | DATETIME | NOT NULL | 결제 시각 |

## 인덱스
- `idx_member` (member_id) — 구매자 마이페이지 "구매 완료 목록"용
- `idx_team_status` (team_id, status) — 실패 판정 스케줄러가 팀별 `PAID` 결제 일괄 조회할 때 사용
- `idx_product` (product_id) — 판매자 마이페이지 "수익 현황" 집계용

## 관계
- member_id → member.id
- product_id → product.id
- team_id → group_buy_team.id (nullable)

## 사용하는 기능
- payment/create, payment/detail, mypage/buyer-purchases, mypage/seller-revenue, team/deadline-check(스케줄러, 환불 처리)

## 삭제 정책
- 하드 삭제 없음 (`REFUNDED`로 상태만 전환, row는 유지 — 결제 이력 보존 필요)
