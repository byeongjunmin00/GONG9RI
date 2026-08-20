# 결제 확정 동시 요청 락 보강

대상: payment/portone
담당: 전용운

## 배경 / 요구

코드리뷰(2026-08-20, 병합된 15개 커밋 리뷰)에서 발견: `PaymentService.confirm()`(클라이언트 호출)과
`confirmByPgPaymentId()`(웹훅)가 각자 `Payment`를 락 없이 조회한 뒤 `status == PENDING`만 확인하고
넘어간다. 두 경로가 거의 동시에 같은 결제를 확정하려 들면(정상적으로 발생 가능한 겹침 —
`confirmByPgPaymentId`의 존재 이유 자체가 이 겹침의 안전망이다) 둘 다 PENDING을 읽고 둘 다
`applyVerificationResult`까지 진입해, 판매자 수익이 두 번 증가하고 `paymentReceived` 알림도 두 번
발행될 수 있다. `Payment`에는 `@Version`도 없고 조회에 락도 없어서 코드 주석의 "같은 결제로 알림이
두 번 생기지 않는다"는 보장이 실제로는 지켜지지 않는다.

## 설계

이 프로젝트에 이미 있는 비관적 락 패턴(`GroupBuyTeamRepositoryImpl.findByIdForUpdate`,
`RefundRequestRepositoryImpl`의 동일 패턴)을 `PaymentRepository`에도 적용한다 —
`PaymentRepositoryCustom`/`PaymentRepositoryImpl`에 QueryDSL `setLockMode(PESSIMISTIC_WRITE)`로
잠그는 조회 메서드를 추가하고, `confirm()`/`confirmByPgPaymentId()`가 기존 락 없는 조회 대신 이걸
쓰게 한다. 두 트랜잭션 중 하나가 먼저 행을 잠그면 다른 하나는 첫 트랜잭션 커밋(상태가 PENDING이
아니게 됨)까지 대기했다가, 그 이후에는 PENDING 게이트에서 정상적으로 걸러진다.

## 태스크

- [x] `PaymentRepositoryCustom`/`PaymentRepositoryImpl`에 `findByIdForUpdate`/
      `findByPgPaymentIdForUpdate` 추가(비관적 락)
- [x] `PaymentService.confirm()`/`confirmByPgPaymentId()`가 락 조회를 쓰도록 변경, 관련 주석 갱신
- [x] 동시 확정 시나리오 동시성 테스트 추가(`RefundRequestConcurrencyTest`와 같은 패턴)

## 평가(통과) 기준

- 신규 동시성 테스트 통과(같은 결제를 동시에 여러 번 확정 시도해도 정확히 한 번만 확정 + 알림 1건)
- 기존 관련 테스트 전체 통과: `PaymentControllerTest`, `SellerRevenueSummaryTest`,
  `SellerRevenueSummaryConcurrencyTest`, `TeamDeadlineEventFlowTest`

## 실행 결과

계획대로 `PaymentRepositoryCustom`/`Impl`에 `findByIdForUpdate`/`findByPgPaymentIdForUpdate`(QueryDSL
`setLockMode(PESSIMISTIC_WRITE)`, `GroupBuyTeamRepositoryImpl`과 동일 패턴)를 추가하고,
`PaymentService.confirm()`/`confirmByPgPaymentId()`가 이 락 조회를 쓰도록 변경했다.

신규 `PaymentConfirmConcurrencyTest`(클라이언트 `confirm()` 5회 + 웹훅 `confirmByPgPaymentId()` 5회를
같은 결제에 동시 실행) 포함 `./gradlew test` **전체 385케이스 통과**(신규 1케이스 포함, 실패/에러 0).
최종 상태 확인: `seller_revenue_summary.totalRevenue`/`paidCount`가 정확히 1건분만 반영, "결제 발생"
알림(`notificationPublisher.paymentReceived`)도 정확히 1회만 발행됨을 Mockito verify로 확인했다.

## 리스크 / 전제

- 로컬 검증에 MySQL 가동 필요(비관적 락은 실제 RDB 락 동작을 타므로 H2 등 대체 불가).
- 비관적 락으로 트랜잭션이 짧게 직렬화되지만, `confirm`/`confirmByPgPaymentId`는 이미 PortOne API
  재조회(외부 HTTP 호출)를 트랜잭션 안에서 하고 있어 락 보유 시간이 이미 그 지연에 좌우된다 — 이번
  변경이 그 특성을 새로 만든 것은 아니다(스코프 밖, 별도 개선 대상이면 추후 논의).
