# 마지막 남은 참여자도 참여 취소 가능하게 (팀 자동 해체)

대상: team/crud (기존 `leave()` 확장, 새 폴더 아님)
담당: 전용운

## 배경 / 요구

사용자가 UI에서 발견: 공구팀 참여 목록에서 참여자가 1명(마지막)만 남으면 "마지막 남은 참여자는
참여를 취소할 수 없습니다"라는 안내와 함께 취소 버튼이 막혀있다. 사용자 지적 — 마지막 1명만
남았다는 건 그 사람이 나가면 공구가 무산된다는 뜻일 뿐인데, 이를 막을 이유가 없고 오히려 그
사람이 취소하면 공구방 자체가 없어져야(=팀이 무산 처리돼야) 한다.

이 동작은 버그가 아니라 **의도된 이전 결정의 결과**다 —
`docs/dev/team/crud/changes/004-last-participant-cannot-leave.md`(2026-08-18, 전용운). 원래
설계(`docs/dev/refund/request/changes/001-team-leave-and-refund-request.md`)는 지금 요구와
동일하게 "마지막 참여자가 취소하면 팀이 `FAILED`로 전환"이었다. 그런데 코드리뷰에서 "리더가
팀의 마지막 참여자로 탈퇴하면 `GroupBuyTeam.leader`가 더 이상 참여자가 아닌 사람을 계속
가리키는" finding이 나왔고, 처리 방향을 사용자와 상의하는 과정에서 "마지막 1명은 참여 취소·
환불 모두 금지"로 요구가 바뀌어 지금처럼 아예 막아버렸다.

즉 이번 작업은 **004의 가드를 되돌려 001의 원래 동작을 복원**하는 것이다. 004가 우려했던
"리더 필드가 죽은 참여자를 계속 가리키는" 문제는 사용자 확인 결과 **그대로 둔다**로 정리됐다
— `leader_id`는 `TeamParticipation`이 아니라 `Member`를 직접 참조(FK 자체는 무결) 하고,
팀이 `FAILED`(죽은 상태)가 된 뒤에는 leader 필드가 어떤 활성 기능에도 쓰이지 않으므로
"마지막에 누가 리더였는지"를 기록으로 남기는 정도로 취급한다. 별도 null 처리·스키마 변경
없음.

참고: 팀 목록 조회(`GET /api/products/{productId}/teams`)는 `RECRUITING` 상태만 반환하므로,
팀이 `FAILED`로 전환되면 별도 프론트 작업 없이도 자동으로 목록에서 사라진다 — "공구방을
없앤다"는 요구는 백엔드의 상태 전환만으로 충족된다.

## 설계

- `TeamService.leave()`에서 `team.getCurrentCount() <= 1` 가드
  (`ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE` throw)를 제거한다 — 004 이전 상태로 되돌림.
  Javadoc도 이 가드가 없어진 사실에 맞게 갱신한다.
- 가드 제거 후 마지막 참여자가 취소하면: 참여 기록 삭제 → `GroupBuyTeam.decreaseParticipant()`가
  `currentCount`가 0이 되는 것을 감지해 팀을 `FAILED`로 전환(기존 로직 그대로 재사용, 코드에
  이미 있음 — 새로 구현할 필요 없음) → 리더 승계 로직(`wasLeader && status != FAILED`)은 이미
  FAILED 전환 시 스킵하도록 되어 있어 그대로 둔다(남을 사람이 없으므로 승계 자체가 불가능한
  상황이 맞다).
- 마지막 참여자의 `PAID` 결제가 있으면 기존 환불 요청 자동 생성 경로
  (`refundRequestService.createFromTeamLeave`)를 예외 없이 그대로 탄다 — 001 설계상 "참여 취소
  시 자동 환불 요청 생성"에 마지막 참여자 예외는 없었다.
- `ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE`는 참조가 사라져 미사용 코드가 되므로 제거한다.
- 영향 계층: service(`TeamService`), 테스트(`TeamControllerTest`), 문서(`docs/api/team.md`,
  `docs/dev/team/crud/design.md`).

## 태스크

- [x] `TeamService.leave()`에서 `LAST_PARTICIPANT_CANNOT_LEAVE` 가드 제거 + Javadoc 갱신
- [x] 마지막 참여자 취소 → 팀 `FAILED` 전환 + (해당 시) 환불 요청 자동 생성 동작 확인
- [x] `ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE` 제거(참조 없음 확인)
- [x] `TeamControllerTest`의 `leave_lastParticipant_conflict`(409 검증) 테스트를
      `leave_lastParticipant_teamBecomesFailed`(200 + FAILED 전환 검증)로 교체
- [x] `docs/api/team.md` — "마지막 참여자는 취소 불가" 서술을 "마지막 참여자 취소 시 팀 FAILED
      전환" 서술로 교체 (에러 표에서 `LAST_PARTICIPANT_CANNOT_LEAVE` 행 제거)
- [x] `docs/dev/team/crud/design.md` — 참여 취소 절 갱신(가드 제거, FAILED 전환 경로 부활)
- [x] `buyer-mypage.js`의 `teamStatusToLabel('FAILED')`가 무조건 "환불 처리됨"이라고 표시하던
      것 수정(리뷰에서 발견 — 마지막 참여자 취소 경로는 자동환불 설정이 꺼져 있으면 환불이
      승인 대기 상태로 남을 수 있어 "처리됨"을 단정할 수 없음) → "미성사"로 단순화하고, 실제
      환불 상태는 환불 요청 내역 섹션에서 확인하도록 주석 갱신

## 평가(통과) 기준 / 결과

- 참여자 1명(마지막)만 남은 `RECRUITING` 팀에서 `leave()` 호출 시 `200` 성공, 팀 상태가
  `FAILED`로 전환되고 `currentCount`가 0이 되는지 확인 — ✅ `leave_lastParticipant_teamBecomesFailed`
- `LAST_PARTICIPANT_CANNOT_LEAVE` 409 응답이 더 이상 발생하지 않는지 확인 — ✅
- 참여자 2명 이상인 기존 leave 시나리오(자리 반환, 리더 승계, 환불 요청 생성)는 회귀 없이
  통과 — ✅ `TeamControllerTest` 전체
- `FAILED` 전환 후 `GET /api/products/{productId}/teams`(RECRUITING만 반환) 목록에서 해당
  팀이 자동으로 빠지는지 — ✅ 기존 필터 로직 그대로(신규 구현 없음), 프론트 `product.js`도
  `loadTeams()` 재조회 방식이라 별도 수정 불필요함을 코드 확인
- 동시성/실시간 브로드캐스트 관련 회귀 없음 — ✅ `TeamConcurrencyTest`, `TeamConcurrencyAtomicTest`,
  `TeamDeadlineServiceTest`, `TeamCapacityBroadcastTest` 재실행 확인
- `./gradlew test --tests "com.gong9ri.gong9ri.controller.TeamControllerTest"` 등 스코프 테스트
  전부 `BUILD SUCCESSFUL`(상세: `docs/logs/team/crud/010-last-participant-leave-team-failed.md`)

## 리스크/전제

- `FAILED`로 전환된 팀의 `leader_id`는 더 이상 그 팀 참여자가 아닌 멤버를 계속 가리킨다(004의
  코드리뷰 finding과 동일한 상황 재발) — 사용자 확인: FK 무결성은 안 깨지고 FAILED(죽은) 팀의
  leader는 실사용 기능에 영향이 없으므로 **의도적으로 그대로 둔다**. 별도 null 처리·스키마
  변경 없음.
- 동시성: `leave()`는 기존 `findByIdForUpdate` 락 경로를 그대로 재사용하므로 신규 동시성 이슈는
  없다(기존 join()/leave() 동시성 제어와 동일한 전제, 재검증 완료).

완료: 2026-08-22. 상세 실행 증거는 `docs/logs/team/crud/010-last-participant-leave-team-failed.md` 참고.
