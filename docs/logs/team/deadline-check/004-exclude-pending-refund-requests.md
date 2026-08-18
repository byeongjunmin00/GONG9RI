# 004-exclude-pending-refund-requests — 마감 스윕 PENDING 제외 (로그)

대상: `docs/dev/team/deadline-check/changes/003-exclude-pending-refund-requests.md`

## Attempt 1 — 2026-08-18 ✅ PASS

- 시도: `TeamDeadlineService.processDeadline()`이 마감 스윕 대상에서 이미 `PENDING` `RefundRequest`가
  걸린 결제를 제외하도록 수정(`RefundRequestRepository.findByPayment_IdInAndStatus` 신규).
  `TeamDeadlineServiceTest`에 혼합 케이스 신규 추가.
- 결과: `./gradlew test --rerun-tasks` 272건 전체 통과(로컬 Docker MySQL/Redis, 볼륨 초기화 후 확인).
- 증거: 신규 테스트 `processDeadline_paymentWithPendingRefundRequest_isExcludedFromSweep` —
  대기 중 요청이 걸린 결제는 발행된 `TeamPaymentsRefundRequestedEvent.paymentIds`에서 제외되고,
  일반 결제는 포함됨을 확인.
- 상세 통합 로그: `docs/logs/refund/request/003-hardening.md`(같은 코드리뷰 후속 조치의 5건을 함께 기록).
