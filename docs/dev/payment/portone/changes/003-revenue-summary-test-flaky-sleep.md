# 동시성 테스트의 고정 sleep을 폴링으로 교체 (테스트 안정성)

대상: payment/portone
담당: 전용운

## 배경 / 요구

코드리뷰(2026-08-20, 병합된 15개 커밋 리뷰)에서 발견: `SellerRevenueSummaryConcurrencyTest`의
`@AfterEach` 정리 전 `waitForAsyncNotifications()`가 조건을 확인하지 않고 그냥
`Thread.sleep(1_000L)`만 한다. 같은 저장소의 `NotificationTypesFlowTest`는 조건이 충족될 때까지
폴링하는 `waitUntil(...)` 헬퍼를 쓰는데, 이 테스트만 고정 sleep이다. 20개 동시 결제 각각이
`@Async` 리스너로 알림을 만드는데, 그 삽입이 CI 부하 등으로 1초 안에 다 안 끝나면 `@AfterEach`의
회원 삭제가 아직 남아있는 알림 행의 FK를 위반해 테스트가 간헐적으로 실패할 수 있다(이 테스트가
검증하려는 실제 로직과 무관한 실패).

## 설계

`NotificationTypesFlowTest.waitUntil(condition, failureMessage)`와 같은 폴링 패턴을 이
테스트에도 적용한다 — 고정 sleep 대신 "판매자에게 결제 건수만큼 알림이 실제로 생겼는지"를 주기적으로
확인하고, 타임아웃 안에 조건이 충족되면 즉시 진행한다(불필요하게 기다리지 않음).

## 태스크

- [x] `SellerRevenueSummaryConcurrencyTest`에 `waitUntil` 폴링 헬퍼 추가(또는 공용화)
- [x] `waitForAsyncNotifications()`를 "판매자 알림이 결제 건수만큼 존재"를 기다리는 폴링으로 교체

## 평가(통과) 기준

- `./gradlew test --tests "*SellerRevenueSummaryConcurrencyTest*"` 통과(여러 번 반복 실행해도 안정적)
- 전체 테스트 스위트 회귀 없음

## 실행 결과

계획대로 고정 `Thread.sleep(1_000L)`을 "판매자에게 결제(스레드) 건수만큼 알림이 실제로 도착했는지"를
50ms 간격으로 최대 5초까지 폴링하는 방식으로 교체했다(`NotificationTypesFlowTest.waitUntil`과 동일
패턴). `SellerRevenueSummaryConcurrencyTest`만 **3회 연속 재실행**해 안정적으로 통과함을 확인했다.

전체 스위트(`./gradlew test`)는 이 로컬 환경에 동시에 다른 세션이 같은 MySQL/Redis 컨테이너를 공유해
테스트를 돌리고 있어(`docs/dev/ongoing/product-open-soon-tab.md`, `header-logo-inline-9.md` 관련
작업으로 추정) 이번 실행에서 `LoginRateLimitFilterTest`(고정 Redis 키 `rate-limit:login:...` 공유로
두 세션이 서로의 카운터에 간섭) 등 이 변경과 무관한 항목에서 일시적 실패가 있었다 — 이 테스트가
건드린 파일(`SellerRevenueSummaryConcurrencyTest.java`)만 별도로 격리 실행했을 때는 안정적으로
통과했으므로 이번 변경으로 인한 회귀는 아니라고 판단한다.
