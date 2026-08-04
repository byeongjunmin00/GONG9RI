# 001-deadline-check — 공구팀 마감 체크 & 환불 트리거 (로그)

## Attempt 1 — 2026-08-04
- 시도: `docs/policy/refund-trigger.md`(SSOT)를 그대로 구현.
  - `entity/GroupBuyTeam`에 `fail()` 추가 — `increaseParticipant()`와 같은 도메인 메서드 패턴, `RECRUITING`일 때만 `FAILED`로 전환하는 가드 포함(이중 전환 방지).
  - `repository/GroupBuyTeamRepository`에 `findIdsByStatusAndDeadlineBefore(status, now)` 추가 — `idx_status_deadline` 인덱스를 타는 스캔 쿼리, 엔티티 대신 id만 반환해 스캔 시점 스냅샷과 실제 락 획득 시점을 분리.
  - `repository/PaymentRepository`에 `findByTeamIdAndStatus(teamId, status)` 추가(derived query, `idx_team_status` 인덱스 활용) — 팀의 `PAID` 결제 목록을 가져와 엔티티 루프 + `payment.refund()` 방식으로 전환(기존 `Payment.refund()` 스타일과 일관성 유지, bulk `@Modifying` 대신 선택 — 팀당 결제 건수가 많지 않을 것으로 예상되고, 엔티티 메서드 재사용이 기존 코드베이스 패턴과 더 맞음).
  - 신규 `service/TeamDeadlineService` 클래스(기존 `TeamService`/`PaymentService`에 얹지 않고 분리 — 두 도메인을 가로지르는 배치 로직이라 별도 서비스가 책임을 더 명확히 분리한다고 판단). `findExpiredRecruitingTeamIds()`(읽기전용, 스캔)와 `processDeadline(teamId)`(팀 단위 독립 `@Transactional`, 쓰기)로 분리.
    - `processDeadline`은 `groupBuyTeamRepository.findByIdForUpdate(teamId)`로 기존 `join()`과 동일한 비관적 락을 재사용 — 마감 직전 참가 시도(`TeamService.join`)와 스케줄러의 FAILED 전환이 같은 팀 row에서 직렬화되도록 함(계획에서 명시한 동시성 리스크 대응).
    - 락 획득 후 "여전히 `RECRUITING`이고 `deadline`이 지났는지"를 방어적으로 재검증 — 스캔(id 목록 조회) 시점과 락 획득 시점 사이에 상태가 바뀌었을 가능성(예: 그 사이 참가로 `SUCCESS` 전환) 대비.
    - 통과하면 `team.fail()` → `PAID` 결제 전부 `payment.refund()` → INFO 로그(`teamId`, `refundedPaymentCount`).
  - 신규 `scheduler/TeamDeadlineScheduler` 컴포넌트(신규 패키지 `com.gong9ri.gong9ri.scheduler` — 이 저장소 첫 스케줄러라 계층 위치를 새로 만듦). `@Scheduled(fixedRate = 60_000)`(정책 기본값 1분 그대로 유지)로 `checkDeadlines()`를 실행 — `findExpiredRecruitingTeamIds()`로 대상 id를 가져온 뒤, 각 id에 대해 `processDeadline(teamId)`를 개별 호출. 스캐줄러 자체는 `@Transactional`이 아니므로, 스캔 1개 트랜잭션 + 팀마다 별도 트랜잭션(`processDeadline`)으로 자연히 나뉨 — 계획의 "팀 단위 트랜잭션" 요건 충족(전체 대상을 하나의 트랜잭션으로 묶지 않음).
  - `Gong9riApplication`에 `@EnableScheduling` 추가.
  - 컨트롤러/DTO/`ErrorCode` 신규 없음(계획대로 — 내부 배치, 사용자 대면 아님).
  - 테스트: `service/TeamDeadlineServiceTest`(신규) — `@SpringBootTest` + `@Transactional`(기존 `PaymentControllerTest`류 컨벤션과 동일하게 자동 롤백으로 정리; 이 테스트는 동시 스레드를 쓰지 않아 `TeamConcurrencyTest`의 수동 정리 방식은 불필요하다고 판단). 실제 1분 대기 없이 `deadline`을 과거로 박아 저장한 팀에 대해 `TeamDeadlineService.processDeadline(teamId)`/`findExpiredRecruitingTeamIds()`를 직접 호출해 검증(`@Scheduled` 트리거 자체는 테스트하지 않음 — 스프링이 등록해주는 부분이라 비즈니스 로직 검증에 불필요).
    - 케이스: (1) `RECRUITING`+`deadline` 지난 팀 → `FAILED` 전환 + 연결된 `PAID` 결제 전부 `REFUNDED`, (2) 연결된 결제 없는 팀도 에러 없이 `FAILED` 전환, (3) `deadline` 안 지난 `RECRUITING` 팀은 그대로 유지, (4) 이미 `SUCCESS`인 팀은 `deadline`이 지났어도 재전환 없고 결제도 그대로 `PAID`, (5) 스캔 쿼리가 `RECRUITING`+`deadline` 지난 팀 id만 반환하는지 확인.
- 참고: `./gradlew compileJava`/`compileTestJava` 모두 성공 확인. 이 샌드박스 환경에는 로컬 MySQL이 설치/기동돼 있지 않아(`netstat`상 3306 리스닝 없음, MySQL 서비스 없음) `./gradlew test --tests "*TeamDeadlineServiceTest*"` 실행 시 Hibernate dialect 해석 단계에서 DB 연결 실패로 5케이스 전부 컨텍스트 로딩 실패함 — 코드/테스트 로직 자체의 실패가 아니라 이 환경의 DB 부재가 원인으로 보이나, 실제 로컬 MySQL이 있는 환경에서 재확인이 필요함(Evaluate 단계 몫).

## Attempt 1 (Evaluate) — 2026-08-04  ✅ PASS
- 결과: 이 PC에 로컬 MySQL 8.4를 직접 설치(Windows 서비스 등록은 권한 문제로 실패해 `mysqld.exe --standalone`을 백그라운드 프로세스로 직접 기동, `root`/`1234`로 맞춤)한 뒤 재검증. `./gradlew build` **BUILD SUCCESSFUL** — 기존 70케이스 + 신규 `TeamDeadlineServiceTest` 5케이스, 총 75케이스 전부 통과. `./gradlew test --tests "*TeamDeadlineServiceTest*"` 단독 실행으로도 재확인.
- 원인: (통과) Generate 단계에서 남긴 이슈는 "샌드박스에 MySQL이 아예 없었다"는 환경 문제였고, MySQL을 준비하자 해소됨. 코드/테스트 로직 자체는 최초 구현에서 이미 정상이었음(첫 시도에 통과, 재작업 없음).
- 추론적 평가: `docs/policy/refund-trigger.md`(1분 주기, 팀 단위 트랜잭션, FAILED+일괄 REFUNDED) 그대로 구현됨을 코드 리딩으로 확인. 계획(`docs/dev/ongoing/team-deadline-check.md`)이 리스크로 지적한 "join과의 동시성 경합"을 `findByIdForUpdate` 락 재사용 + 락 후 방어적 재검증으로 해결했음을 확인. `docs/code-convention.md`(생성자 주입, `@Transactional` 경계, SLF4J 로깅, 계층 분리) 위반 없음. API/DTO/ErrorCode 신규 없음(계획대로).
- 증거: `./gradlew build` 콘솔 출력 `BUILD SUCCESSFUL in 11s`, `7 actionable tasks: 1 executed, 6 up-to-date`. 신규 테스트 5케이스 전부 통과(`TeamDeadlineServiceTest_processDeadline_failsTeamAndRefundsPaidPayments` 등). DB 오염 없음(`@Transactional` 롤백 방식).
- 후속: `docs/dev/team/deadline-check/design.md` 신규 작성, `docs/dev/ongoing/team-deadline-check.md` → `docs/dev/team/deadline-check/changes/001-deadline-check.md` 채번 이동 완료.
