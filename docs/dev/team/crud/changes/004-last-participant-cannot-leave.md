# 마지막 남은 참여자는 참여 취소 불가

대상: team/crud (기존 `leave()` 확장, 새 폴더 아님)
담당: 전용운

## 배경 / 요구

코드리뷰(`docs/logs/refund/request/002-code-review.md`)에서 "리더가 팀의 마지막 남은 참여자로
탈퇴하면 `GroupBuyTeam.leader`가 삭제된 참여자를 계속 가리키는" finding이 나왔다. 처리 방향을
사용자에게 확인하는 과정에서, 더 근본적인 요구사항으로 대체됐다: **"마지막 1명은 환불도 안되고
탈퇴도 안돼"** — 팀의 마지막 남은 참여자는 애초에 참여 취소 자체를 할 수 없어야 한다.

## 설계

- `TeamService.leave()`에 `team.getCurrentCount() <= 1`이면 거절하는 가드 추가(신규
  `ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE`, 409). 기존 `TEAM_NOT_RECRUITING` 가드 다음, 리더
  판정 이전에 위치.
- 부수 효과: `GroupBuyTeam.decreaseParticipant()`가 `currentCount`를 0으로 만드는 경로가
  `leave()`를 통해서는 더 이상 없다 — 팀이 `FAILED`로 전환되는 경로는 이제 `team/deadline-check`
  마감 스케줄러뿐이다. 리더 승계 로직상 "마지막 남은 1명은 항상 그 시점의 리더"라는 점(리더가
  나갈 때마다 남은 사람에게 승계되는 걸 반복하면 귀납적으로 수렴)이 성립하므로, 이 가드 하나로
  원래 finding(leader 필드 stale)까지 함께 해소된다 — 별도 null 처리·스키마 변경 불필요.
  `GroupBuyTeam.decreaseParticipant()`/`changeLeader()` 자체는 방어적 코드로 그대로 둔다.

## 태스크

- [x] `ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE` 추가
- [x] `TeamService.leave()` 가드 추가 + Javadoc 갱신
- [x] `TeamControllerTest.leave_lastParticipant_teamBecomesFailed` → `leave_lastParticipant_conflict`(409 검증)로 교체
- [x] `docs/api/team.md` leave 에러 표 갱신
- [x] `docs/dev/team/crud/design.md` 갱신(참여 취소 절, 관련 코드 위치, 테스트 목록)

## 평가(통과) 기준

- 마지막 남은 참여자가 `leave()` 호출 시 `409 LAST_PARTICIPANT_CANNOT_LEAVE`, 팀 `currentCount`/`status` 불변.
- 참여자가 2명 이상인 상태에서의 기존 leave 시나리오(자리 반환, 리더 승계, 환불 요청 생성 등)는 회귀 없이 통과.
- `./gradlew test` 전체 통과(272/272, `docs/logs/refund/request/003-hardening.md` 참고).

완료: 2026-08-18. 상세 실행 증거는 `docs/logs/refund/request/003-hardening.md`(리뷰 후속 조치 통합 로그) 참고.
