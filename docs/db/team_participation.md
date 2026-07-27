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
- team/join, mypage/buyer-teams

## 삭제 정책
- 하드 삭제 없음 (참여 이력은 영구 보존)
