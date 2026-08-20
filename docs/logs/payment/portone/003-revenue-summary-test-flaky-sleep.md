# 003-revenue-summary-test-flaky-sleep — 동시성 테스트 고정 sleep 제거 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `SellerRevenueSummaryConcurrencyTest.waitForAsyncNotifications()`를 고정
  `Thread.sleep(1_000L)`에서 "판매자에게 결제(스레드) 건수만큼 알림이 실제로 도착"을 50ms 간격으로
  최대 5초까지 폴링하는 방식으로 교체(`NotificationTypesFlowTest.waitUntil`과 동일 패턴).
- 결과: 이 테스트만 격리 실행해 3회 연속 통과 확인. 전체 스위트 실행에서는 로컬 환경에 다른 세션이
  같은 MySQL/Redis를 동시에 쓰고 있어(`product-open-soon-tab`/`header-logo-inline-9` 작업 추정)
  이 변경과 무관한 `LoginRateLimitFilterTest`(공유 Redis 키 충돌) 등에서 일시적 실패가 있었음 —
  이 테스트가 건드린 파일만 격리했을 때는 안정적이라 회귀가 아니라고 판단.
- 증거(테스트 결과 요약):
  - `./gradlew test --tests "*SellerRevenueSummaryConcurrencyTest*"` 3회 연속 실행 — 매번
    `BUILD SUCCESSFUL`.
