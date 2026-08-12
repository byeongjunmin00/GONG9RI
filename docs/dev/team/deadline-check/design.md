# 공구팀 마감 체크 & 환불 트리거 (team/deadline-check) — Design

## 개요

`RECRUITING` 상태이면서 `deadline`이 지난 `group_buy_team`을 1분마다 스캔해, 팀별로 독립된 트랜잭션에서 `FAILED`로 전환하고 그 팀에 연결된 `PAID` 결제의 **환불(포트원 결제취소) 필요를 트리거**하는 내부 배치 기능이다. 사용자 대면 API는 없다(스케줄러 전용).

**감지·처리·실제 환불의 3단계가 스프링 애플리케이션 이벤트로 분리돼 있다** — 스케줄러는 스캔+이벤트 발행만 하고, 팀 `FAILED` 전환은 별도 비동기 리스너가 수행하며(신규 인프라 없음, 인프로세스 발행-구독), **실제 결제 환불(포트원 결제취소 API 호출·확인)은 이 기능이 아니라 `payment/portone`이 담당**한다(`docs/dev/payment/portone/design.md`) — 이 기능은 "환불이 필요한 결제 id 목록"까지만 넘긴다. 환불이 실제로 확정되면 그 확정 트랜잭션이 커밋된 이후에만 알림 도메인(`notification/refund-alert`, `docs/dev/notification/refund-alert/design.md`)에 "환불 완료" 이벤트가 전달된다.

## API / 인터페이스

없음 — 내부 스케줄러(`@Scheduled`)만 존재, 외부에 노출되는 엔드포인트 없음.

## 데이터 모델

- `group_buy_team`, `payment` 기존 테이블 재사용 — 상세: `docs/db/group_buy_team.md`, `docs/db/payment.md`
- 신규 테이블/컬럼 없음. 기존 인덱스(`idx_status_deadline`, `idx_team_status`)를 그대로 활용.
- **성능 병목 개선(발제 필수9) — EXPLAIN 기반 실측**: 스캔 쿼리(`status='RECRUITING' AND deadline<NOW()`)가 `idx_status_deadline`를 실제로 타는지, 인덱스가 없었다면 어땠을지를 합성 데이터(20만 건)로 재현해서 EXPLAIN·실행시간을 비교함 — 인덱스 없으면 전체 테이블 스캔(20만 건, 평균 52ms), 있으면 커버링 인덱스 레인지 스캔(매칭 33,336건만, 평균 13ms, 약 4배). 상세: `docs/logs/team/deadline-check/003-explain-analysis.md`.

## 이벤트 흐름

1. `TeamDeadlineScheduler.checkDeadlines()`(`@Scheduled(fixedRate = 60_000)`)가 대상 팀 id를 스캔해, 팀 id별로 `TeamDeadlineDetectedEvent`만 발행한다 — `TeamDeadlineService.processDeadline()`을 직접 호출하지 않는다.
2. `TeamDeadlineEventListener`(`@EventListener` + `@Async`)가 이 이벤트를 구독해 `TeamDeadlineService.processDeadline(teamId)`를 그대로 호출한다. `@Async`라 스케줄러의 스캔 루프(for)는 팀별 처리(락 대기+DB 쓰기)를 기다리지 않는다. 전용 스레드풀은 `config/AsyncConfig`(`@EnableAsync`, `ThreadPoolTaskExecutor` core=2/max=10/queue=100, 예외는 `AsyncUncaughtExceptionHandler`로 SLF4J ERROR 로깅).
3. **PortOne 연동 이후(`docs/dev/payment/portone/design.md`)**: `processDeadline(teamId)`는 더 이상 결제를 직접 `REFUNDED`로 전환하지 않는다 — 실제 결제 환불은 PortOne 결제취소 API 호출을 거쳐야 하는데, 그 외부 HTTP 호출을 이 메서드가 잡고 있는 비관적 락(`findByIdForUpdate`) 트랜잭션 안에서 하면 락을 오래 잡게 되기 때문이다. 대신 `processDeadline`은 팀을 `FAILED`로 전환한 뒤, 그 팀의 `PAID` 결제 id 목록만 담아 `TeamPaymentsRefundRequestedEvent(teamId, paymentIds)`를 발행한다. 결제 상태 전환·판매자 수익 요약 반영은 이 시점에 하지 않는다.
4. `TeamPaymentsRefundRequestedEventListener`(`@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)`)가 `processDeadline`의 트랜잭션이 **실제로 커밋된 이후에만** 이 이벤트를 소비한다 — 이 시점부터는 비관적 락이 이미 풀린 뒤이므로, 결제 건마다 실제 PortOne 결제취소 API를 호출해도 락 문제가 없다. 호출 결과를 `PaymentRefundService.applyCancelResult`가 반영하고(성공 시 `REFUNDED` 전환 + 판매자 수익 요약 감소), 그 안에서 결제 건별로 `TeamRefundedEvent(teamId, sellerId, buyerMemberIds)`를 발행한다(상세: `docs/dev/payment/portone/design.md`).
5. `TeamRefundedEventListener`가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 `TeamRefundedEvent`를 구독한다 — `NotificationService.createTeamRefundedNotifications(event)`를 호출해 그 결제의 구매자 + 상품 판매자에게 알림을 남긴다(상세: `docs/dev/notification/refund-alert/design.md`). **알려진 동작 변화**: 팀 단위로 한 번에 배치 발행하던 이전과 달리, 이제는 결제 건이 실제로 확정될 때마다 개별 발행되므로(비동기 처리 시점이 결제 건마다 다를 수 있음), 같은 팀 판매자가 결제 건수만큼 여러 번 알림을 받을 수 있다.

## 규칙 / 검증

- 규칙 원천: `docs/policy/refund-trigger.md` — 1분 주기 스캔, `status=RECRUITING && deadline<now()` 대상, 팀 단위 트랜잭션으로 `FAILED` 전환. **PortOne 연동 이후에는 "해당 팀 PAID 결제 전부 REFUNDED 일괄 전환"이 `processDeadline` 안에서 즉시 일어나지 않는다** — `FAILED` 전환은 여전히 이 메서드가 팀 단위 트랜잭션으로 보장하지만, 실제 `REFUNDED` 전환은 `payment/portone`이 포트원 취소 API 응답을 확인한 뒤(비동기 가능) 별도로 보장한다.
- 스캔(`findExpiredRecruitingTeamIds`)은 id만 조회하는 읽기전용 쿼리, 실제 처리(`processDeadline`)는 팀별로 별도 트랜잭션 — 전체 대상을 하나의 트랜잭션으로 묶지 않는다.
- **동시성**: `processDeadline`은 `team/join`(`TeamService.join`)과 동일한 `findByIdForUpdate` 비관적 락을 재사용해, 마감 직전 참가 시도와 마감 처리가 같은 팀 row에서 직렬화되게 한다. 락 획득 후 "여전히 `RECRUITING`이고 `deadline`이 지났는지" 방어적으로 재검증한다(스캔 스냅샷과 락 획득 시점 사이 상태가 바뀔 수 있어서 — 예: 그 사이 참가로 `SUCCESS` 전환).
- `GroupBuyTeam.fail()`은 `RECRUITING` 상태일 때만 `FAILED`로 전환하는 가드를 가진다(이미 `SUCCESS`/`FAILED`인 팀은 보호됨).
- 환불 대상 조회: `PaymentRepository.findByTeamIdAndStatus(teamId, PAID)`로 이 시점 기준 `PAID` 결제 id 목록만 뽑아 이벤트로 넘긴다 — 실제 `Payment.refund()` 호출과 판매자 수익 요약 반영은 이 서비스가 아니라 `PaymentRefundService`(`docs/dev/payment/portone/design.md`)가 PortOne 취소 응답을 확인한 뒤 담당한다.
- 컨트롤러/DTO/`ErrorCode` 신규 없음(사용자 대면 기능이 아님).
- 인프로세스 이벤트라 서버 재시작 중 처리되지 못한 이벤트는 사라질 수 있다 — 영향은 "알림 발송 누락"뿐이고, 환불 자체는 스캔이 다음 주기(1분)에 같은 팀을 다시 찾아 트랜잭션으로 보장한다(데이터 정합성 문제는 아님).

## 관련 코드 위치

- `entity/GroupBuyTeam.java` — `fail()`
- `repository/GroupBuyTeamRepository.java` — `findIdsByStatusAndDeadlineBefore(status, now)`
- `repository/PaymentRepository.java` — `findByTeamIdAndStatus(teamId, status)`
- `service/TeamDeadlineService.java` — `findExpiredRecruitingTeamIds()`(스캔) / `processDeadline(teamId)`(팀 단위 트랜잭션 처리, `FAILED` 전환 + 환불 대상 있으면 `TeamPaymentsRefundRequestedEvent` 발행)
- `scheduler/TeamDeadlineScheduler.java` — `@Scheduled(fixedRate = 60_000)` `checkDeadlines()` — 스캔 후 팀 id별로 `TeamDeadlineDetectedEvent`만 발행(처리 직접 호출 없음)
- `event/TeamDeadlineDetectedEvent.java` — 마감 감지 이벤트(record, teamId)
- `event/TeamDeadlineEventListener.java` — `@Async` 구독자, `processDeadline(teamId)` 호출
- `event/TeamPaymentsRefundRequestedEvent.java` / `event/TeamPaymentsRefundRequestedEventListener.java` — 환불취소 요청 이벤트(record, teamId/paymentIds) + `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 구독자(PortOne 취소 API 호출·결과 반영은 `docs/dev/payment/portone/design.md` 참고)
- `event/TeamRefundedEvent.java` — 환불 **확정** 완료 이벤트(record, teamId/sellerId/buyerMemberIds) — 발행 주체가 `TeamDeadlineService`에서 `PaymentRefundService`(payment/portone)로 이동했다.
- `event/TeamRefundedEventListener.java` — `@TransactionalEventListener(AFTER_COMMIT)` 구독자, `NotificationService` 호출(변경 없음)
- `config/AsyncConfig.java` — `@EnableAsync` + 전용 `ThreadPoolTaskExecutor` + 비동기 예외 로깅
- `Gong9riApplication.java` — `@EnableScheduling`
- 테스트:
  - `service/TeamDeadlineServiceTest.java` — `processDeadline` 자체 회귀(마감 전환 + `TeamPaymentsRefundRequestedEvent` 발행 검증, 결제 없는 팀, 마감 미도달, 이미 SUCCESS인 팀 보호, 스캔쿼리 필터링) + 신규(트랜잭션 롤백 시 이벤트가 소비되지 않아 알림도 생성되지 않음 검증)
  - `scheduler/TeamDeadlineSchedulerTest.java` — 스캔된 팀마다 이벤트만 발행하고 `processDeadline`을 직접 호출하지 않는지(순수 Mockito, DB 없음)
  - `event/TeamDeadlineEventFlowTest.java` — 실제 이벤트 발행 → 비동기 리스너 경유 상태전환+환불취소요청, PortOne 취소(목) 확인 후 실제 REFUNDED 전환 및 구매자 전원+판매자 알림 생성, PortOne이 REQUESTED(비동기)로 응답하면 `REFUND_PENDING`로 대기하는 케이스까지(`@SpringBootTest`, 실제 커밋 필요해 클래스 레벨 `@Transactional` 미사용, `PortOneClient`는 `@MockitoBean`)
  - `event/TeamPaymentsRefundRequestedEventListenerTest.java` — 리스너의 순수 라우팅 로직(취소 대상마다 PortOne 호출·결과 반영, 대상 아님/호출 실패 시 안전 처리) 단위 검증
