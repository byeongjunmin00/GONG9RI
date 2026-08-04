# 공구팀 마감 체크 & 환불 트리거 (team/deadline-check) — Design

## 개요

`RECRUITING` 상태이면서 `deadline`이 지난 `group_buy_team`을 1분마다 스캔해, 팀별로 독립된 트랜잭션에서 `FAILED`로 전환하고 그 팀에 연결된 `PAID` 결제를 전부 `REFUNDED`로 일괄 전환하는 내부 배치 기능이다. 사용자 대면 API는 없다(스케줄러 전용).

## API / 인터페이스

없음 — 내부 스케줄러(`@Scheduled`)만 존재, 외부에 노출되는 엔드포인트 없음.

## 데이터 모델

- `group_buy_team`, `payment` 기존 테이블 재사용 — 상세: `docs/db/group_buy_team.md`, `docs/db/payment.md`
- 신규 테이블/컬럼 없음. 기존 인덱스(`idx_status_deadline`, `idx_team_status`)를 그대로 활용.

## 규칙 / 검증

- 규칙 원천: `docs/policy/refund-trigger.md` — 1분 주기 스캔, `status=RECRUITING && deadline<now()` 대상, 팀 단위 트랜잭션으로 `FAILED` 전환 + 해당 팀 `PAID` 결제 전부 `REFUNDED` 일괄 전환.
- 스캔(`findExpiredRecruitingTeamIds`)은 id만 조회하는 읽기전용 쿼리, 실제 처리(`processDeadline`)는 팀별로 별도 트랜잭션 — 전체 대상을 하나의 트랜잭션으로 묶지 않는다.
- **동시성**: `processDeadline`은 `team/join`(`TeamService.join`)과 동일한 `findByIdForUpdate` 비관적 락을 재사용해, 마감 직전 참가 시도와 마감 처리가 같은 팀 row에서 직렬화되게 한다. 락 획득 후 "여전히 `RECRUITING`이고 `deadline`이 지났는지" 방어적으로 재검증한다(스캔 스냅샷과 락 획득 시점 사이 상태가 바뀔 수 있어서 — 예: 그 사이 참가로 `SUCCESS` 전환).
- `GroupBuyTeam.fail()`은 `RECRUITING` 상태일 때만 `FAILED`로 전환하는 가드를 가진다(이미 `SUCCESS`/`FAILED`인 팀은 보호됨).
- 결제 환불: `PaymentRepository.findByTeamIdAndStatus(teamId, PAID)`로 대상 조회 후 엔티티 루프 + `Payment.refund()` 호출(기존 도메인 메서드 재사용, bulk 쿼리 대신 — 팀당 결제 건수가 크지 않을 것으로 예상되고 기존 코드베이스 패턴과 일관성 유지).
- 컨트롤러/DTO/`ErrorCode` 신규 없음(사용자 대면 기능이 아님).

## 관련 코드 위치

- `entity/GroupBuyTeam.java` — `fail()` 추가
- `repository/GroupBuyTeamRepository.java` — `findIdsByStatusAndDeadlineBefore(status, now)`
- `repository/PaymentRepository.java` — `findByTeamIdAndStatus(teamId, status)`
- `service/TeamDeadlineService.java` — `findExpiredRecruitingTeamIds()`(스캔) / `processDeadline(teamId)`(팀 단위 트랜잭션 처리) (신규)
- `scheduler/TeamDeadlineScheduler.java` — `@Scheduled(fixedRate = 60_000)` `checkDeadlines()` (신규, 이 저장소 첫 스케줄러 컴포넌트/패키지)
- `Gong9riApplication.java` — `@EnableScheduling` 추가
- 테스트: `service/TeamDeadlineServiceTest.java` (5케이스: 정상 전환+환불, 결제 없는 팀, 마감 미도달, 이미 SUCCESS인 팀 보호, 스캔쿼리 필터링)
