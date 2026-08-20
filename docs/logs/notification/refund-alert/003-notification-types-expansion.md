# 003-notification-types-expansion — 알림 종류 8종 확장 (로그)

## Attempt 1 — 2026-08-20  ❌ FAIL (동기 리스너, 커넥션 풀 고갈)
- 시도: 기존 환불 알림과 동일하게 동기 `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`로 8종 알림 발행 구현.
- 결과: 전체 테스트에서 `SellerRevenueSummaryConcurrencyTest`(동시 결제 20건)가 타임아웃으로 실패.
- 원인: `HikariPool-6 - Connection is not available, request timed out after 30002ms (total=10, active=10, idle=0, waiting=16)`. AFTER_COMMIT 콜백이 원본 트랜잭션의 JDBC 커넥션이 아직 반납되기 전에 실행되는데, 거기서 동기로 REQUIRES_NEW 서비스를 부르면 스레드 하나가 커넥션을 동시에 2개 필요로 해서 동시 요청이 풀 크기만큼 몰리면 교착된다.
- 다음: `@Async`로 알림 리스너를 분리(같은 방식을 이미 쓰는 `RefundRequestApprovedEventListener` 참고).

## Attempt 2 — 2026-08-20  ✅ PASS
- 시도: `NotificationRequestedEvent` 하나로 8종을 통일(종류마다 이벤트+리스너 쌍을 만들지 않음), 리스너에 `@Async` 적용. 문구/링크 규칙은 `NotificationPublisher`에 집중.
- 결과: `./gradlew test` 전체 **379케이스 통과**(신규 `NotificationTypesFlowTest` 10케이스 포함).
- 증거: `NotificationTypesFlowTest`는 "알림이 생겼다"가 아니라 "누구에게 생겼는지"를 단언 — 발행 코드를 일부러 제거해 실제로 실패하는 것까지 역검증함. 결제 확정 경로 2개(클라이언트 confirm/웹훅)가 중복 알림 없이 1건만 발행되는지도 전용 테스트로 확인.
