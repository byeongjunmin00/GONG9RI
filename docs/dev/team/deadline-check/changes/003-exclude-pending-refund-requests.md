# 마감 스윕에서 대기 중 환불 요청 걸린 결제 제외

대상: team/deadline-check (기존 `processDeadline()` 확장, 새 폴더 아님)
담당: 전용운

## 배경 / 요구

코드리뷰(`docs/logs/refund/request/002-code-review.md`)에서 발견: `TeamDeadlineService.
processDeadline()`의 마감 스윕(`docs/policy/refund-trigger.md` — 팀의 `PAID` 결제를 전부
`REFUNDED`로 일괄 전환)이, 참여 취소(`refund/request`)로 이미 생성된 `PENDING` `RefundRequest`가
걸린 결제까지 그대로 쓸어가는 문제. 판매자의 승인/거절 결정을 기다리는 요청을 마감 스윕이 먼저
가로채 취소해버리면, 그 `RefundRequest`가 영구히 고아 상태(대상 결제가 이미 없음)로 남는다.

## 설계

- `RefundRequestRepository.findByPayment_IdInAndStatus(paymentIds, status)` 신규 — 후보 결제 id
  목록 중 특정 상태(`PENDING`)의 요청이 걸린 것만 조회.
- `TeamDeadlineService.processDeadline()`이 `paymentRepository.findByTeamIdAndStatus(teamId, PAID)`
  결과에서 위 조회로 찾은 `PENDING` 대상을 제외한 뒤 `TeamPaymentsRefundRequestedEvent`를 발행.
  `REJECTED`/`APPROVED`로 이미 결정 난 요청이 있던 결제는 제외 대상이 아니다.

## 태스크

- [x] `RefundRequestRepository.findByPayment_IdInAndStatus` 추가
- [x] `TeamDeadlineService.processDeadline()` 제외 로직 추가
- [x] `TeamDeadlineServiceTest`에 혼합 케이스(대기중 있는 결제/없는 결제) 신규 추가
- [x] `docs/dev/team/deadline-check/design.md`, `docs/dev/refund/request/design.md` 갱신

## 평가(통과) 기준

- 대기 중(`PENDING`) 환불 요청이 걸린 결제는 `TeamPaymentsRefundRequestedEvent.paymentIds`에서 제외된다.
- 대기 중 요청이 없는 결제는 기존대로 포함된다(회귀 없음).
- `./gradlew test` 전체 통과(272/272, `docs/logs/refund/request/003-hardening.md` 참고).

완료: 2026-08-18. 상세 실행 증거는 `docs/logs/refund/request/003-hardening.md`(리뷰 후속 조치 통합 로그) 참고.
