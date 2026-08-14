# refund_request (환불 요청)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| payment_id | BIGINT | NOT NULL, FK | 환불 대상 결제 |
| requester_id | BIGINT | NOT NULL, FK | 요청자(참여 취소면 취소한 구매자, 직접 요청이면 결제 본인) |
| status | VARCHAR(20) | NOT NULL | `PENDING` / `APPROVED` / `REJECTED` |
| reason | VARCHAR(500) | NULL | 구매자가 직접 요청(솔로 구매)할 때만 값이 있다. 참여 취소로 자동 생성된 요청은 "참여 취소"가 곧 사유라 NULL |
| rejection_reason | VARCHAR(30) | NULL | 거절 시에만 값이 있다 — 자유 텍스트가 아니라 템플릿(`RefundRejectionReason` enum) 중 하나 |
| requested_at | DATETIME | NOT NULL | 요청 생성 시각 |
| decided_at | DATETIME | NULL | 승인/거절 시각. `PENDING`인 동안은 NULL |

## 인덱스
- `idx_payment` (payment_id) — 같은 결제에 대한 중복 요청 여부 확인용 (`existsByPayment_IdAndStatus`)
- `idx_requester` (requester_id) — 구매자 마이페이지 "내 환불 요청 목록" 조회용
- `idx_status` (status) — 판매자 마이페이지에서 대기/처리 상태별 필터링 여지

## 관계
- payment_id → payment.id
- requester_id → member.id

## 생성 경로 (서로 겹치지 않음)
1. **참여 취소 자동 생성**(`team/leave` → `TeamService.leave`) — 팀 결제(`payment.team != null`) 전용,
   `reason = NULL`. 상품별 `product.auto_refund_on_cancel`이 켜져 있으면 저장 즉시 `APPROVED`로
   생성되고, 꺼져 있으면 `PENDING`으로 남아 판매자 승인을 기다린다.
2. **구매자 직접 요청**(`POST /api/payments/{paymentId}/refund-requests`) — 솔로 구매(`payment.team ==
   null`) 건에만 허용, `reason` 필수. 팀이 딸린 결제로 시도하면 `409 TEAM_PAYMENT_REFUND_NOT_ALLOWED`.

**매우 중요한 제약**: 팀이 딸린 결제(`payment.team != null`)의 환불은 오직 경로 1(참여 취소)로만
일어난다. 팀이 정원을 채워 `SUCCESS`로 전환된 뒤에는 그 팀 참여자가 더는 참여 취소를 할 수 없으므로(
`TeamService.leave`가 `RECRUITING`에서만 성공), 그 시점 이후로는 팀 결제 건에 대한 `refund_request`가
어떤 경로로도 새로 생성될 수 없다 — 2인 목표 팀에 계정 2개로 결제 후 하나만 환불해 실질적 1인 결제를
2인 구간 할인가로 사는 악용을 막기 위한 설계(사용자 확인).

## 사용하는 기능
- refund/request(신규 개념), team/leave(자동 생성 트리거), payment/portone(승인 시 실제 취소 실행
  경로 재사용 — `PaymentRefundService`/`PaymentCancellationExecutor`), mypage/buyer-refund-requests,
  mypage/seller-refund-requests

## 삭제 정책
- 하드 삭제 없음(요청 이력 보존 — 거절 사유까지 구매자가 나중에 확인할 수 있어야 한다).
