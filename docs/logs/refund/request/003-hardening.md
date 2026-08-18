# 003-hardening — a6373d9 코드리뷰 후속 조치 (로그)

대상: `docs/dev/ongoing/refund-request-hardening.md` (완료 시 team/crud, team/deadline-check,
refund/request 각 changes/로 분리 이동 예정)

## Attempt 1 — 2026-08-18

- 시도: 리뷰(`docs/logs/refund/request/002-code-review.md`)에서 남은 5건 구현.
  1. `TeamService.leave()` — `currentCount == 1`(마지막 남은 참여자)이면 참여 취소 거절
     (`ErrorCode.LAST_PARTICIPANT_CANNOT_LEAVE`, 409 신규). 마지막 참여자가 항상 그 시점의
     리더라는 점(리더 승계 로직상 귀납적으로 성립)을 이용해, 이 가드 하나로 "리더가 마지막으로
     나가면서 `leader` 필드가 stale해지는" 옛 finding까지 같이 해소(사용자 확인 후 결정 —
     원래 finding은 "leader 필드 처리"였는데, 계획 단계에서 "마지막 1명은 환불도 탈퇴도 안 된다"는
     더 근본적인 요구사항으로 대체됨). `TeamControllerTest.leave_lastParticipant_teamBecomesFailed`를
     `leave_lastParticipant_conflict`(409 검증)로 교체, `docs/api/team.md` 에러 표 갱신.
  2. `TeamDeadlineService.processDeadline()` — 마감 스윕에서 이미 `PENDING` `RefundRequest`가 걸린
     결제는 제외(`RefundRequestRepository.findByPayment_IdInAndStatus` 신규). `TeamDeadlineServiceTest`에
     혼합 케이스(PENDING 있는 결제/없는 결제) 신규 추가.
  3. `RefundRequestService.createFromTeamLeave()` — `createDirect()`와 동일한 중복 PENDING 방지
     체크 추가. 단 예외 대신 로그+스킵(부수효과 호출이라 예외 시 `leave()` 전체가 롤백돼버림).
     `TeamControllerTest.leave_rejoinThenLeaveAgain_doesNotDuplicatePendingRefundRequest` 신규.
  4. `Payment.isOwnedBy(Long memberId)` 신규 — `RefundRequestService`/`PaymentService`의 중복
     `requireOwner` 로직을 엔티티 메서드로 통합.
  5. (문서 전용, 코드 없음) `docs/dev/refund/request/design.md`의 정책 참조·FAILED 케이스 분석·
     스테일 메서드명 수정은 Evaluate 통과 후 design.md 갱신 단계에서 함께 처리 예정.
- 결과: ✅ PASS — Docker Desktop 기동 + `docker compose up -d mysql redis` 후 `./gradlew test --rerun-tasks`
  최초 실행 시 4건 실패(`ProductControllerTest`/`ProductCachingTest`, "expected 15 but was 16" 류) —
  8일 전에 만들어진 MySQL 볼륨을 재사용해 생긴 잔여 데이터 오염으로 판단(이번 변경과 무관, team/
  refund/payment 쪽 테스트는 전부 통과했었음). `docker compose down -v` + `up -d`로 볼륨 초기화 후
  재실행하니 **272건 전체 통과**(`BUILD SUCCESSFUL`).
- 완료 후: `docs/dev/{team/crud/changes/004-last-participant-cannot-leave.md, team/deadline-check/
  changes/003-exclude-pending-refund-requests.md, refund/request/changes/002-hardening.md}`로 분리
  기록 + 대응 design.md 3곳(`team/crud`, `team/deadline-check`, `refund/request`) 갱신 +
  `docs/dev/ongoing/refund-request-hardening.md` 제거(채번 이동 완료).
