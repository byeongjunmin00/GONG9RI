# 002-confirm-concurrency-lock — 결제 확정 동시 요청 락 보강 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `PaymentRepositoryCustom`/`PaymentRepositoryImpl`에 `findByIdForUpdate`/
  `findByPgPaymentIdForUpdate`(QueryDSL `setLockMode(PESSIMISTIC_WRITE)`,
  `GroupBuyTeamRepositoryImpl`과 동일 패턴) 추가. `PaymentService.confirm()`/
  `confirmByPgPaymentId()`가 기존 락 없는 조회 대신 이 메서드를 쓰도록 변경. 동시 확정 시나리오
  검증용 `PaymentConfirmConcurrencyTest`(클라이언트 `confirm()` 5회 + 웹훅
  `confirmByPgPaymentId()` 5회를 같은 결제에 동시 실행) 신규 작성.
- 결과: `./gradlew test` 전체 통과(385케이스, 실패/에러 0).
- 증거(테스트 결과 요약):
  - `PaymentConfirmConcurrencyTest`: 1케이스 통과 — 최종 상태 `PAID`, `seller_revenue_summary`
    `totalRevenue=10000`/`paidCount=1`(중복 확정 없음), `notificationPublisher.paymentReceived`
    정확히 1회 호출(Mockito verify).
  - 회귀 확인: `PaymentControllerTest`(20), `SellerRevenueSummaryTest`(6),
    `SellerRevenueSummaryConcurrencyTest`(1), `TeamDeadlineEventFlowTest`(3) 전부 통과.
- 참고: 로컬 검증 시점에 Docker Desktop이 꺼져있어 MySQL/Redis 컨테이너가 내려가 있었다 —
  Docker Desktop 기동 + `docker compose up -d mysql redis` 후 재실행해 통과 확인.
