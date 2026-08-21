# 001-reservation-expiry — 미결제 참여 자동 만료 스케줄러 신설 (로그)

대응 계획: `docs/dev/ongoing/team-payment-enforcement.md`

## Attempt 1 — 2026-08-22

- 시도: `team/deadline-check`(`TeamDeadlineService`/`TeamDeadlineScheduler`) 패턴을 재사용해, 참가/신설
  시점(`TeamParticipation.joinedAt`) 기준 10분이 지나도록 그 팀+멤버 조합에 `PAID` 결제가 없으면 자동으로
  참여를 취소하는 신규 기능을 추가.
  1. `repository/TeamParticipationRepositoryCustom.java`/`Impl.java` —
     `findTeamIdsWithParticipationBefore(TeamStatus status, LocalDateTime cutoff)` 신규(QueryDSL, 기존
     querydsl-migration 컨벤션 준수). `team/deadline-check`의 스캔과 동일하게 "후보 팀 id"만 가볍게
     반환 — 결제 여부는 이 스캔에서 거르지 않는다(최종 판정은 락 획득 후 처리 단계에서).
  2. `service/TeamReservationExpiryService.java`(신규) — 스캔(`findTeamIdsWithExpiredUnpaidParticipations`,
     읽기전용)과 처리(`processExpiredParticipations(teamId)`, 팀별 독립 `@Transactional`)를 분리.
     처리 단계는 `groupBuyTeamRepository.findByIdForUpdate`로 팀 락을 잡고, 락 획득 후 팀 상태가
     여전히 `RECRUITING`인지 방어적으로 재검증한 뒤, 그 팀의 참여자 중 `joinedAt`이 cutoff보다 이전인
     후보를 순회하며 각각 "여전히 참여자인지"·"그 사이 PAID 결제가 생기지 않았는지"를 다시 확인하고
     `TeamService.cancelParticipation(team, member)`(이번 작업에서 team/crud 쪽에 새로 뽑아낸 공용 메서드,
     011-payment-enforcement 로그 참고)를 그대로 재사용해 취소한다. `RefundRequestService`는 별도
     분기 없이도 자연히 스킵된다 — `cancelParticipation` 내부에서 PAID 결제가 있을 때만 환불 요청을
     만드는데, 이 경로의 대상은 정의상 PAID 결제가 없는 참여이기 때문.
  3. `scheduler/TeamReservationExpiryScheduler.java`(신규) — `@Scheduled(fixedRate = 60_000)`로 1분마다
     스캔 후 팀 id별로 `processExpiredParticipations`를 직접 동기 호출. `team/deadline-check`와 달리
     이 기능은 외부 HTTP 호출(PortOne 등)이 없어 락을 오래 잡을 위험이 없으므로, 이벤트 발행-구독(비동기
     분리)을 도입하지 않았다 — 계획 문서에서도 "정확한 구현 방식은 Generate 재량"으로 남겨둔 부분.
  4. 테스트 신규: `service/TeamReservationExpiryServiceTest`(7케이스 — 미결제 만료 취소, PAID 결제 보호,
     cutoff 이내 보호, 마지막 참여자 FAILED 전환, 리더 승계, SUCCESS 팀 방어적 스킵, 스캔 쿼리 필터링),
     `scheduler/TeamReservationExpirySchedulerTest`(2케이스 — 스캔 결과별 처리 호출 여부, 순수 Mockito).
     - 테스트 작성 중 발견: `TeamParticipation.joinedAt`이 `@CreatedDate` + `updatable=false`라
       `ReflectionTestUtils.setField`만으로는 DB에 반영되지 않는다(Hibernate가 UPDATE에 그 컬럼을 아예
       포함 안 함). 스캔 쿼리가 스칼라 프로젝션이라 영속성 컨텍스트를 거치지 않고 DB 값을 직접 읽어서
       처음엔 테스트가 실패했다 — 프로덕션 버그가 아니라 테스트 셋업 문제였음을 확인 후, 테스트 헬퍼가
       네이티브 UPDATE로 DB 컬럼 값도 함께 맞추도록 수정해 해결(테스트 파일 자체 주석에 근거 남김).
- 범위 밖(계획대로 손대지 않음): `join()`/`leave()`의 기존 동시성 제어, `RefundRequestService` 자체 로직,
  PortOne 연동, `team/deadline-check`의 기존 파일들.
- 셀프 체크(Generator 단계 확인 — Evaluate의 정식 계산적 평가는 별도):
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`.
  - `./gradlew test --tests "com.gong9ri.gong9ri.service.TeamReservationExpiryServiceTest" --tests "com.gong9ri.gong9ri.scheduler.TeamReservationExpirySchedulerTest"` → `BUILD SUCCESSFUL`(7+2건 전부 통과).
  - `./gradlew test --tests "*Team*"` → `BUILD SUCCESSFUL`(기존 team/crud·deadline-check 테스트 포함
    전부 회귀 없이 통과).

## Evaluate — 2026-08-22  ✅ PASS

- 결과: ✅ **PASS**.
- 계산적 평가: `./gradlew test --tests "*Team*" --rerun-tasks` → `BUILD SUCCESSFUL`,
  `TeamReservationExpiryServiceTest` 7건·`TeamReservationExpirySchedulerTest` 2건 모두
  `failures="0" errors="0"`. 이후 로컬 MySQL 컨테이너의 `root`@`%` DML 권한 누락(이번 기능과 무관한
  환경 문제, 상세 원인·조치는 `docs/logs/team/crud/011-payment-enforcement.md`의 Evaluate 기록 참고)을
  고친 뒤 `./gradlew test --rerun-tasks`(전체) → `BUILD SUCCESSFUL`, 65개 테스트 클래스 전부 실패 0건.
- 추론적 평가:
  - "10분 자동 만료" 통과 기준을 코드로 직접 확인: `EXPIRY_MINUTES = 10`, `cutoff = now().minusMinutes(10)`,
    스캔(`findTeamIdsWithParticipationBefore`)은 QueryDSL로 `team.status = RECRUITING AND joinedAt < cutoff`인
    참여가 있는 팀 id만 중복 제거해 반환 — `team/deadline-check`와 동일하게 "스캔은 가볍게, 최종 판정은
    락 획득 후"라는 패턴을 그대로 따름.
  - `processExpiredParticipations`가 `findByIdForUpdate`로 팀 락을 잡고 `RECRUITING` 재검증 → 후보
    참여자 각각에 대해 "여전히 참여자인지" + "그 사이 PAID 결제가 생기지 않았는지"를 다시 확인한 뒤에만
    `cancelParticipation` 호출 — 스캔 스냅샷과 락 획득 시점 사이의 경쟁(그 사이 결제 완료·참여 취소 등)을
    안전하게 처리함을 코드로 확인. 테스트가 PAID 보호·cutoff 이내 보호·SUCCESS 팀 스킵 케이스로 이 방어
    로직을 실제로 검증함.
  - "외부 HTTP 호출이 없어 락을 오래 잡아도 된다"는 스케줄러/서비스 Javadoc의 주장을 코드로 재확인
    (011 로그에 상세 근거 기록) — `cancelParticipation` 경로에 PortOne 같은 외부 API 호출이 없음을
    확인했고, `team/deadline-check`가 이벤트 발행-구독으로 비동기 분리한 이유(PortOne 취소 호출을 락
    밖으로)가 이 기능에는 애초에 해당하지 않아 동기 처리 선택이 타당함.
  - `RefundRequestService`는 이번 작업에서 전혀 수정되지 않았음을 `git log -- .../RefundRequestService.java`로
    확인(가장 최근 변경 커밋이 이번 작업과 무관한 `729d3af`) — "PAID 없으면 환불 요청 생성 안 됨"이
    `cancelParticipation`의 기존 분기(PAID 결제 있을 때만 `createFromTeamLeave` 호출)로 자연히 보장됨.
  - `join()`/`leave()`의 기존 동시성 전략(`team.join-strategy`, 비관적 락)은 이 신규 스케줄러가
    같은 `findByIdForUpdate` 락을 재사용하는 것 외에는 전혀 변경되지 않았음을 diff로 확인.
- 증거: `TeamReservationExpiryServiceTest`(7케이스, 실제 `@SpringBootTest` + DB로 검증) —
  `processExpiredParticipations`가 미결제·10분 경과 참여를 제거하고 `currentCount`를 되돌림
  (`processExpiredParticipations_unpaidPastCutoff_cancelsParticipation`), PAID 결제가 있으면 그대로
  둠(`processExpiredParticipations_paidParticipation_isNotCanceled`), 마지막 참여자면 팀을 `FAILED`로
  전환(`processExpiredParticipations_lastParticipant_teamBecomesFailed`), 리더면 다음 참가자에게
  승계(`processExpiredParticipations_leaderExpires_leaderSucceeds`).
- 후속 조치: `docs/dev/team/reservation-expiry/design.md` 신규 작성, `docs/dev/team/crud/design.md` 갱신,
  `docs/dev/ongoing/team-payment-enforcement.md`를 이 기능의 `changes/001-payment-enforcement.md`로
  채번 이동(핵심 신규 산출물이 이 개념이라 판단, 근거는 design.md/커밋 참고).
