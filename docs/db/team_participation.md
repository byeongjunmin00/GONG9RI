# team_participation (팀 참여 내역)

## 컬럼
| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, auto | 식별자 |
| team_id | BIGINT | NOT NULL, FK | 어떤 팀에 참여했는지 |
| member_id | BIGINT | NOT NULL, FK | 참여한 회원 |
| joined_at | DATETIME | NOT NULL | 참여 시각 |

## 인덱스
- `idx_member` (member_id) — 마이페이지 "공구 참여 목록" 조회용
- UNIQUE `(team_id, member_id)` — 같은 팀 중복 참가 방지 (`ALREADY_JOINED` 에러의 DB 레벨 보장)

## 관계
- team_id → group_buy_team.id
- member_id → member.id

## 사용하는 기능
- team/join, team/leave, mypage/buyer-teams

## 삭제 정책
- **팀/참가(`team/join`) 시점 기준으로는 여전히 하드 삭제 없음.** 다만 참여 취소(`team/leave`,
  2026-08-14 추가)는 예외다 — 취소한 사람의 행을 즉시 실제로 `DELETE`한다
  (`TeamParticipationRepository.deleteByTeamIdAndMemberId`). 참여 취소의 핵심이 "자리 즉시 반환"(다른
  사람이 그 자리에 바로 참가 가능)이라 이 테이블에서 지우는 게 맞고, 돈이 오간 이력은 이 테이블이 아니라
  `payment`/`refund_request`가 별도로 보존한다 — "참여 이력"과 "결제/환불 이력"을 서로 다른 테이블의
  책임으로 분리했다고 보면 된다.
