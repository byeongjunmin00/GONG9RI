# group_buy_team (공동구매팀)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 팀 ID |
| product_id | BIGINT | NOT NULL, FK | 어떤 상품에 대한 팀인지 |
| leader_id | BIGINT | NOT NULL, FK | 팀을 신설한 사람 |
| current_count | INT | NOT NULL, default 1 | 현재 참여 인원 (캐싱 컬럼 — 동시성 제어 핵심) |
| max_participants | INT | NOT NULL | 정원 (신설 시 `product.max_participants` 복사) |
| status | VARCHAR(20) | NOT NULL, default 'RECRUITING' | `RECRUITING` / `SUCCESS` / `FAILED` |
| deadline | DATETIME | NOT NULL | 팀 유지 마감 시각 |
| created_at | DATETIME | NOT NULL | 팀 생성일 |
| updated_at | DATETIME | NOT NULL | 마지막 상태 변경일 |

## 인덱스
- `idx_product_status` (product_id, status) — "상품에 대한 모집 중 팀 목록 조회"용
- `idx_status_deadline` (status, deadline) — 실패 판정 스케줄러가 `status='RECRUITING' AND deadline < now()` 스캔할 때 사용

## 관계
- product_id → product.id
- leader_id → member.id

## 동시성 제어 (2026-07-27 확정)
- `current_count` 갱신은 반드시 **비관적 락**(`SELECT ... FOR UPDATE`, JPA `@Lock(PESSIMISTIC_WRITE)`)으로 해당 row를 잠근 뒤 처리한다.
- `join` 트랜잭션 안에서: 락 획득 → `current_count < max_participants` 확인 → 증가 → 도달 시 `status='SUCCESS'`까지 한 번에 커밋.
- 상세 배경: `docs/policy/team-success-criteria.md`, 옵시디언 개념 정리 참고.

## 사용하는 기능
- team/create, team/join, team/list, team/deadline-check(스케줄러), mypage/seller-teams

## 삭제 정책
- 하드 삭제 없음 (상태(`FAILED`/`SUCCESS`)로만 종료 표시, row는 유지 — 마이페이지 이력 조회에 필요)
