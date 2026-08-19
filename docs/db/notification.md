# notification (알림)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| member_id | BIGINT | NOT NULL, FK | 알림 수신자(구매자 또는 판매자) |
| type | VARCHAR(30) | NOT NULL | 알림 종류 — `TEAM_REFUNDED` / `TEAM_SUCCESS` / `INQUIRY_CREATED` / `INQUIRY_ANSWERED` / `PAYMENT_RECEIVED` / `REVIEW_CREATED` / `REFUND_REQUESTED` / `REFUND_REQUEST_APPROVED` / `REFUND_REQUEST_REJECTED` (9종, 2026-08-20 확장) |
| message | VARCHAR(255) | NOT NULL | 알림 본문 |
| related_team_id | BIGINT | NULL, FK | 관련 공구팀 ID (팀 관련 알림이 아니면 NULL) |
| link_url | VARCHAR(255) | NULL | 알림 클릭 시 이동할 앱 내부 경로 (예: `/product.html?id=33`). 2026-08-20 추가 — 이전에 만들어진 알림은 NULL |
| is_read | BOOLEAN | NOT NULL, default false | 읽음 여부 |
| created_at | DATETIME | NOT NULL | 생성 시각 |

## 인덱스
- `idx_member` (member_id) — 마이페이지 "알림 목록" 조회용

## 관계
- member_id → member.id
- related_team_id → group_buy_team.id (nullable)

## 사용하는 기능
- notification/refund-alert, mypage/buyer-notifications, mypage/seller-notifications

## 삭제 정책
- 하드 삭제 없음(알림 이력 보존)
