# 미결제 참여 자동 만료 (team/reservation-expiry) — Design

## 개요

공구팀 참가/신설(`team/crud`의 `TeamService.join()`/`create()`)은 결제 완료 여부와 무관하게 자리를
즉시 반영하는 **"예약 후 유예" 모델**이다. 참가/신설 성공 시 프론트가 곧바로 결제 페이지
(`checkout.html`)로 이동시키긴 하지만, 사용자가 결제를 끝내지 않고 이탈(창 닫기, 뒤로 가기 등)하면
그 자리가 영구히 묶여버릴 수 있다. 이 기능은 참가 시점(`TeamParticipation.joinedAt`) 기준 **10분**
안에 그 참여에 연결된 결제가 `PAID`로 확정되지 않으면, 서버가 그 참여를 자동으로 취소해 자리를
반환하는 내부 배치 기능이다. 사용자 대면 API는 없다(스케줄러 전용).

`team/deadline-check`("팀이 정원을 못 채운 채 마감 시각이 지난 경우"를 다룸)와는 **별개 개념**이다 —
이 기능은 "개별 참여자가 결제를 안 끝낸 경우"만 다룬다. 두 기능 모두 `team/crud`의 락 경로
(`findByIdForUpdate`)를 재사용하는 동일 패턴을 쓴다는 점만 같다.

## API / 인터페이스

없음 — 내부 스케줄러(`@Scheduled`)만 존재, 외부에 노출되는 엔드포인트 없음. 사용자에게 보이는 효과는
`team/crud`가 이미 제공하는 `POST /api/teams/{teamId}/leave`와 동일하다(같은 코드 경로를 재사용하므로
API 응답 형태가 별도로 늘지 않는다) — 상세: `docs/api/team.md`의 "미결제 참여 자동 만료" 절.

## 데이터 모델

- `group_buy_team`, `team_participation`, `payment` 기존 테이블만 재사용 — 상세: `docs/db/group_buy_team.md`,
  `docs/db/team_participation.md`, `docs/db/payment.md`
- 신규 테이블/컬럼 없음. `TeamParticipation.joinedAt`(`@CreatedDate`, `updatable=false`)을 만료 기준
  시각으로 그대로 쓴다.

## 처리 흐름

1. `TeamReservationExpiryScheduler.checkExpiredParticipations()`(`@Scheduled(fixedRate = 60_000)`,
   1분 주기)가 `TeamReservationExpiryService.findTeamIdsWithExpiredUnpaidParticipations()`(읽기전용)로
   대상 팀 id를 스캔한다.
2. 스캔 쿼리(`TeamParticipationRepositoryImpl.findTeamIdsWithParticipationBefore`, QueryDSL)는
   `team.status = RECRUITING AND joinedAt < now-10min`인 참여가 하나라도 있는 팀 id만 중복 없이
   반환한다. 결제(`PAID`) 여부는 이 스캔에서 거르지 않는다 — "후보"만 가볍게 골라내고, 최종 판정은
   팀별 락을 잡은 뒤 처리 단계에서 한다(스캔 스냅샷과 락 획득 시점 사이 상태가 바뀔 수 있어서).
3. 스케줄러는 팀 id별로 `TeamReservationExpiryService.processExpiredParticipations(teamId)`를
   **동기로 직접 호출**한다(이벤트 발행-구독으로 비동기 분리하지 않음 — 아래 "동기 처리 이유" 참고).
4. `processExpiredParticipations`(팀별 독립 `@Transactional`)는 `GroupBuyTeamRepository.findByIdForUpdate`로
   그 팀 row에 비관적 락을 잡고, 팀 상태가 여전히 `RECRUITING`인지 방어적으로 재검증한다(스캔 이후
   그사이 정원이 차서 `SUCCESS`로 전환됐거나 이미 `FAILED`일 수 있음 — 아니면 스킵).
5. 그 팀의 참여자 중 `joinedAt`이 cutoff보다 이전인 후보를 `joinedAt` 오름차순으로 순회하며, 후보마다
   다시 "여전히 이 팀의 참여자인지"(방금 처리로 이미 빠졌을 수 있음, 안전망) + "그사이 `PAID` 결제가
   생기지 않았는지"를 재확인한 뒤에만 `TeamService.cancelParticipation(team, member)`
   (`team/crud`가 이번 작업에서 뽑아낸 공용 메서드, package-private, 같은 패키지)를 그대로 호출해
   취소한다. 처리 도중 마지막 참여자 취소로 팀이 `FAILED`로 전환되면 그 팀의 남은 후보 순회를 멈춘다.

## 동기 처리 이유 (vs team/deadline-check의 이벤트 발행-구독)

`team/deadline-check`는 스캔 → 이벤트 발행 → `@Async` 리스너가 실제 처리를 수행하는 3단계 비동기
분리를 쓴다 — 팀 `FAILED` 전환 이후의 실제 PortOne 결제취소 API 호출(외부 HTTP)을 팀 row 락 **밖**에서
하기 위해서다(락을 쥔 채로 외부 API를 호출하면 락을 오래 잡게 되는 문제).

이 기능(`team/reservation-expiry`)의 `cancelParticipation` 경로는 DB 접근(팀/참여/결제 조회·저장)과
인프로세스 이벤트 발행(`TeamCapacityChangedEvent`, 기존 `join()`/`leave()`도 이미 같은 트랜잭션 안에서
발행하던 것과 동일 패턴, 구독자는 `@TransactionalEventListener(AFTER_COMMIT)`)뿐이고, PortOne 같은
외부 HTTP 호출이 전혀 없다 — 결제가 애초에 `PAID`가 아닌 참여만 대상이므로 환불(`RefundRequestService`)
경로 자체가 열리지 않는다. 락을 오래 잡을 위험이 없으므로 `team/deadline-check`처럼 비동기 분리할
필요가 없어, 스케줄러가 팀별 처리 메서드를 그대로 동기 호출한다.

## 규칙 / 검증

- **규칙**: 참가/신설 시점(`TeamParticipation.joinedAt`) 기준 10분 안에 그 참여에 연결된 `Payment`가
  `PAID`로 확정되지 않으면, `POST /api/teams/{teamId}/leave`와 동일한 효과로 자동 취소한다(정원 감소,
  필요 시 리더 승계, 마지막 참여자면 팀 `FAILED` 전환). 결제가 애초에 `PAID`가 아니므로 환불 요청은
  생성되지 않는다 — `cancelParticipation`이 `PAID` 결제가 있을 때만 `RefundRequestService.createFromTeamLeave`를
  호출하는 기존 분기를 그대로 타기 때문에 이 기능에서 별도 분기가 필요 없다.
- **동시성**: `team/crud`(`TeamService.join`/`leave`)와 동일한 `findByIdForUpdate` 비관적 락을
  재사용해, 만료 처리와 참가/취소 요청이 같은 팀 row에서 직렬화되게 한다. 락 획득 후 "여전히
  `RECRUITING`인지"를 방어적으로 재검증한다(스캔 스냅샷과 락 획득 시점 사이 상태가 바뀔 수 있어서).
- `cancelParticipation`(`team/crud`가 노출한 공용 메서드)은 `@Transactional`이 없다 — 항상 호출자가
  이미 열어 둔 트랜잭션(과 그 트랜잭션이 쥔 팀 row 락) 안에서 실행되는 게 전제다. 이 기능의
  `processExpiredParticipations`는 자신의 `@Transactional` + `findByIdForUpdate` 락을 이미 잡은
  상태에서 `cancelParticipation`을 호출하므로 그 전제가 지켜진다.
- 스캔(`findTeamIdsWithExpiredUnpaidParticipations`)은 id만 조회하는 읽기전용 쿼리, 실제 처리
  (`processExpiredParticipations`)는 팀별로 별도 트랜잭션 — 전체 대상을 하나의 트랜잭션으로 묶지 않는다
  (`team/deadline-check`와 동일 원칙).
- 컨트롤러/DTO/`ErrorCode` 신규 없음(사용자 대면 기능이 아님).
- **리스크(계획 단계에서 이미 인지·수용됨)**: 10분 유예 동안 미결제자가 자리를 계속 점유한다 — 그사이
  다른 사람은 여전히 `TEAM_FULL`을 만날 수 있다.

## 관련 코드 위치

- `repository/TeamParticipationRepositoryCustom.java` / `Impl.java` —
  `findTeamIdsWithParticipationBefore(TeamStatus status, LocalDateTime cutoff)`(QueryDSL 스캔 쿼리)
- `service/TeamReservationExpiryService.java` — `findTeamIdsWithExpiredUnpaidParticipations()`(스캔,
  읽기전용) / `processExpiredParticipations(teamId)`(팀 단위 트랜잭션 처리, 락 획득 → 재검증 → 후보별
  최종 판정 → `TeamService.cancelParticipation` 호출)
- `scheduler/TeamReservationExpiryScheduler.java` — `@Scheduled(fixedRate = 60_000)`
  `checkExpiredParticipations()` — 스캔 후 팀 id별로 `processExpiredParticipations`를 동기 직접 호출
- `service/TeamService.java` — `cancelParticipation(GroupBuyTeam team, Member member)`(package-private,
  `team/crud`가 `leave()`에서 추출해 공개한 공용 메서드, 상세: `docs/dev/team/crud/design.md`)
- `Gong9riApplication.java` — `@EnableScheduling`(기존, `team/deadline-check`가 이미 활성화해 둠)
- `src/main/resources/static/js/product.js` — 참가/신설 성공 시 결제 페이지로 강제 이동(이 기능이 다루는
  "미결제 유예" 상황을 만드는 진입점), 팀 목록의 "참여 취소" 버튼
- `src/main/resources/static/js/checkout.js` — "취소하기" 클릭 시 `leave` 선호출(이 기능이 다루는
  "10분 대기 없이 즉시 반환"에 해당하는 명시적 경로)
- 테스트:
  - `service/TeamReservationExpiryServiceTest.java` — `processExpiredParticipations` 자체 회귀(미결제·
    cutoff 경과 참여 취소, `PAID` 결제 있는 참여는 보호, cutoff 이내는 보호, 마지막 참여자 취소 시
    `FAILED` 전환, 리더 만료 시 승계, 이미 `RECRUITING`이 아닌 팀은 스킵) + 스캔 쿼리 필터링 검증(7케이스)
  - `scheduler/TeamReservationExpirySchedulerTest.java` — 스캔 결과가 비어 있으면 처리 호출 안 함 /
    결과가 있으면 팀 id별로 처리 호출(순수 Mockito, DB 없음, 2케이스)
