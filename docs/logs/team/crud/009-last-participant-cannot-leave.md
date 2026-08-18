# 009-last-participant-cannot-leave — 마지막 참여자 탈퇴 금지 (로그)

대상: `docs/dev/team/crud/changes/004-last-participant-cannot-leave.md`

## Attempt 1 — 2026-08-18 ✅ PASS

- 시도: `TeamService.leave()`에 `currentCount <= 1` 가드 추가(`ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE`,
  409). 관련 테스트 교체(`leave_lastParticipant_conflict`), `docs/api/team.md`/`docs/dev/team/crud/design.md` 갱신.
- 결과: `./gradlew test --rerun-tasks` 272건 전체 통과(로컬 Docker MySQL/Redis 재기동 후 확인 — 초기 1회는
  재사용 볼륨의 잔여 데이터로 무관한 4건이 실패했으나, 볼륨 초기화 후 재실행해 전부 통과 확인).
- 증거(API 샘플): `POST /api/teams/{teamId}/leave`(마지막 참여자) → `409 {"code":"LAST_PARTICIPANT_CANNOT_LEAVE", ...}`.
- 상세 통합 로그: `docs/logs/refund/request/003-hardening.md`(같은 코드리뷰 후속 조치의 5건을 함께 기록).
