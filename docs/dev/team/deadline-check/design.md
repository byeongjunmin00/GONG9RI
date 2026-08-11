# 공구팀 마감 체크 & 환불 트리거 (team/deadline-check) — Design

## 개요

`RECRUITING` 상태이면서 `deadline`이 지난 `group_buy_team`을 1분마다 스캔해, 팀별로 독립된 트랜잭션에서 `FAILED`로 전환하고 그 팀에 연결된 `PAID` 결제를 전부 `REFUNDED`로 일괄 전환하는 내부 배치 기능이다. 사용자 대면 API는 없다(스케줄러 전용).

**감지와 처리는 스프링 애플리케이션 이벤트로 분리돼 있다** — 스케줄러는 스캔+이벤트 발행만 하고, 실제 상태전환+환불은 별도 비동기 리스너가 수행한다(신규 인프라 없음, 인프로세스 발행-구독). 환불이 발생하면 그 트랜잭션이 커밋된 이후에만 알림 도메인(`notification/refund-alert`, `docs/dev/notification/refund-alert/design.md`)에 "환불 완료" 이벤트를 넘긴다.

## API / 인터페이스

없음 — 내부 스케줄러(`@Scheduled`)만 존재, 외부에 노출되는 엔드포인트 없음.

## 데이터 모델

- `group_buy_team`, `payment` 기존 테이블 재사용 — 상세: `docs/db/group_buy_team.md`, `docs/db/payment.md`
- 신규 테이블/컬럼 없음. 기존 인덱스(`idx_status_deadline`, `idx_team_status`)를 그대로 활용.
- **성능 병목 개선(발제 필수9) — EXPLAIN 기반 실측**: 스캔 쿼리(`status='RECRUITING' AND deadline<NOW()`)가 `idx_status_deadline`를 실제로 타는지, 인덱스가 없었다면 어땠을지를 합성 데이터(20만 건)로 재현해서 EXPLAIN·실행시간을 비교함 — 인덱스 없으면 전체 테이블 스캔(20만 건, 평균 52ms), 있으면 커버링 인덱스 레인지 스캔(매칭 33,336건만, 평균 13ms, 약 4배). 상세: `docs/logs/team/deadline-check/003-explain-analysis.md`.

## 이벤트 흐름

1. `TeamDeadlineScheduler.checkDeadlines()`(`@Scheduled(fixedRate = 60_000)`)가 대상 팀 id를 스캔해, 팀 id별로 `TeamDeadlineDetectedEvent`만 발행한다 — `TeamDeadlineService.processDeadline()`을 직접 호출하지 않는다.
2. `TeamDeadlineEventListener`(`@EventListener` + `@Async`)가 이 이벤트를 구독해 `TeamDeadlineService.processDeadline(teamId)`를 그대로 호출한다. `@Async`라 스케줄러의 스캔 루프(for)는 팀별 처리(락 대기+DB 쓰기)를 기다리지 않는다. 전용 스레드풀은 `config/AsyncConfig`(`@EnableAsync`, `ThreadPoolTaskExecutor` core=2/max=10/queue=100, 예외는 `AsyncUncaughtExceptionHandler`로 SLF4J ERROR 로깅).
3. `processDeadline(teamId)` 내부 로직(락·재검증·전환·환불)은 바뀌지 않았다 — 아래 "규칙/검증" 그대로. 실제로 환불이 발생한 경우(`!paidPayments.isEmpty()`)에만, 이 메서드가 이미 열어둔 트랜잭션 안에서 `TeamRefundedEvent(teamId, sellerId, buyerMemberIds)`를 발행한다.
4. `TeamRefundedEventListener`가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 이 이벤트를 구독한다 — `processDeadline`의 트랜잭션이 **실제로 커밋된 이후에만** 반응하고, 커밋 전/롤백 시에는 절대 실행되지 않는다(알림 정합성 보장). 리스너는 `NotificationService.createTeamRefundedNotifications(event)`를 호출해 그 팀에서 환불된 결제의 구매자 전원 + 상품 판매자에게 알림을 남긴다(상세: `docs/dev/notification/refund-alert/design.md`).

## 규칙 / 검증

- 규칙 원천: `docs/policy/refund-trigger.md` — 1분 주기 스캔, `status=RECRUITING && deadline<now()` 대상, 팀 단위 트랜잭션으로 `FAILED` 전환 + 해당 팀 `PAID` 결제 전부 `REFUNDED` 일괄 전환. (이벤트로 진입 경로가 바뀌었어도, "팀 단위 트랜잭션 안에서 전환+환불"이라는 정책 자체는 `processDeadline` 메서드 하나가 여전히 그대로 지킨다 — 감지 이벤트/처리는 분리됐지만 전환+환불 자체는 분리되지 않았다.)
- 스캔(`findExpiredRecruitingTeamIds`)은 id만 조회하는 읽기전용 쿼리, 실제 처리(`processDeadline`)는 팀별로 별도 트랜잭션 — 전체 대상을 하나의 트랜잭션으로 묶지 않는다.
- **동시성**: `processDeadline`은 `team/join`(`TeamService.join`)과 동일한 `findByIdForUpdate` 비관적 락을 재사용해, 마감 직전 참가 시도와 마감 처리가 같은 팀 row에서 직렬화되게 한다. 락 획득 후 "여전히 `RECRUITING`이고 `deadline`이 지났는지" 방어적으로 재검증한다(스캔 스냅샷과 락 획득 시점 사이 상태가 바뀔 수 있어서 — 예: 그 사이 참가로 `SUCCESS` 전환).
- `GroupBuyTeam.fail()`은 `RECRUITING` 상태일 때만 `FAILED`로 전환하는 가드를 가진다(이미 `SUCCESS`/`FAILED`인 팀은 보호됨).
- 결제 환불: `PaymentRepository.findByTeamIdAndStatus(teamId, PAID)`로 대상 조회 후 엔티티 루프 + `Payment.refund()` 호출(기존 도메인 메서드 재사용, bulk 쿼리 대신 — 팀당 결제 건수가 크지 않을 것으로 예상되고 기존 코드베이스 패턴과 일관성 유지).
- 컨트롤러/DTO/`ErrorCode` 신규 없음(사용자 대면 기능이 아님).
- 인프로세스 이벤트라 서버 재시작 중 처리되지 못한 이벤트는 사라질 수 있다 — 영향은 "알림 발송 누락"뿐이고, 환불 자체는 스캔이 다음 주기(1분)에 같은 팀을 다시 찾아 트랜잭션으로 보장한다(데이터 정합성 문제는 아님).

## 관련 코드 위치

- `entity/GroupBuyTeam.java` — `fail()`
- `repository/GroupBuyTeamRepository.java` — `findIdsByStatusAndDeadlineBefore(status, now)`
- `repository/PaymentRepository.java` — `findByTeamIdAndStatus(teamId, status)`
- `service/TeamDeadlineService.java` — `findExpiredRecruitingTeamIds()`(스캔) / `processDeadline(teamId)`(팀 단위 트랜잭션 처리, 환불 발생 시 `TeamRefundedEvent` 발행)
- `scheduler/TeamDeadlineScheduler.java` — `@Scheduled(fixedRate = 60_000)` `checkDeadlines()` — 스캔 후 팀 id별로 `TeamDeadlineDetectedEvent`만 발행(처리 직접 호출 없음)
- `event/TeamDeadlineDetectedEvent.java` — 마감 감지 이벤트(record, teamId)
- `event/TeamDeadlineEventListener.java` — `@Async` 구독자, `processDeadline(teamId)` 호출
- `event/TeamRefundedEvent.java` — 환불 완료 이벤트(record, teamId/sellerId/buyerMemberIds)
- `event/TeamRefundedEventListener.java` — `@TransactionalEventListener(AFTER_COMMIT)` 구독자, `NotificationService` 호출
- `config/AsyncConfig.java` — `@EnableAsync` + 전용 `ThreadPoolTaskExecutor` + 비동기 예외 로깅
- `Gong9riApplication.java` — `@EnableScheduling`
- 테스트:
  - `service/TeamDeadlineServiceTest.java` — `processDeadline` 자체 회귀(5케이스: 정상 전환+환불, 결제 없는 팀, 마감 미도달, 이미 SUCCESS인 팀 보호, 스캔쿼리 필터링) + 신규(트랜잭션 롤백 시 환불 완료 알림 미생성 검증)
  - `scheduler/TeamDeadlineSchedulerTest.java` — 스캔된 팀마다 이벤트만 발행하고 `processDeadline`을 직접 호출하지 않는지(순수 Mockito, DB 없음)
  - `event/TeamDeadlineEventFlowTest.java` — 실제 이벤트 발행 → 비동기 리스너 경유 상태전환+환불, `processDeadline` 커밋 성공 시 구매자 전원+판매자 알림 생성(`@SpringBootTest`, 실제 커밋 필요해 클래스 레벨 `@Transactional` 미사용)
