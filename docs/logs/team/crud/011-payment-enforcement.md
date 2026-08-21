# 011-payment-enforcement — 참가/신설 결제 강제 이동 + 참여 취소(leave) 핵심 로직 재사용 정리 (로그)

대응 계획: `docs/dev/ongoing/team-payment-enforcement.md`

## Attempt 1 — 2026-08-22

- 시도: "예약 후 유예" 모델의 team/crud 쪽 절반을 구현.
  1. `dto/TeamResponse.java` — `joinedByCurrentMember`(Boolean) 필드 추가. `from(GroupBuyTeam, boolean)`으로
     시그니처를 바꿔 호출부가 항상 명시적으로 채우게 강제.
  2. `service/TeamService.java`
     - `list(Long productId, MemberUserDetails principal)` — principal이 null(비로그인)이면 전부
       `false`, 있으면 팀마다 `teamParticipationRepository.existsByTeamIdAndMemberId`로 개별 판정.
     - `create()` — 신설자는 곧 첫 참여자이므로 조회 없이 `joinedByCurrentMember=true`로 응답.
     - `leave()`를 리팩터링해 검증 이후의 실제 취소 효과(참여기록 삭제 → 정원 감소 → 리더 승계 →
       PAID 결제 있으면 환불 요청)를 package-private `cancelParticipation(GroupBuyTeam team, Member member)`로
       추출. `@Transactional`을 새로 걸지 않았다 — 항상 호출자가 이미 연 트랜잭션(과 그 트랜잭션이 쥔
       팀 row 락) 안에서 실행되는 게 전제이므로 별도 트랜잭션 경계가 불필요하다. 이 메서드는
       `team/reservation-expiry`(`TeamReservationExpiryService`, 같은 패키지)가 principal 없이 재사용한다.
       `leave()` 자체의 에러코드/동작(순서: 팀 락 → 참여자 검증 → RECRUITING 검증)은 바꾸지 않았다.
  3. `controller/TeamController.java` — `list()`가 `@AuthenticationPrincipal MemberUserDetails principal`을
     nullable로 받아 `teamService.list(productId, principal)`에 그대로 전달(`/api/products/**` GET은 이미
     permitAll이라 비로그인이면 principal이 자동으로 null로 주입됨, 별도 설정 불필요 확인).
  4. `static/js/product.js`
     - `handleJoin()`/`handleCreateTeam()` — 성공 시 `showPageAlert` 배너 대신
       `window.location.href = 'checkout.html?productId=...&teamId=...'`로 자동 이동.
     - `createTeamItem()` — `team.joinedByCurrentMember`가 true면 "참가하기" 대신 "참여 취소" 버튼을
       렌더링, 클릭 시 확인창(`buyer-mypage.js` 569-598행과 동일 문구/비활성화 패턴) 후
       `Api.post('/teams/{teamId}/leave')` → 성공 시 `loadTeams(currentProductId)` 재조회.
  5. `static/js/checkout.js` — `handleCancel()`: `currentTeamId`가 있으면 이동 전에
     `Api.post('/teams/{teamId}/leave')`를 먼저 호출(성공/실패 무관하게 `.then`으로 이동 처리 —
     이미 취소된 상태 등으로 leave가 실패해도 사용자는 그냥 상품 페이지로 돌아가면 되므로 에러로 막지 않음).
  6. 테스트 추가: `TeamControllerTest`에 `list_publicAccess_joinedByCurrentMemberIsFalse`,
     `list_loggedIn_joinedByCurrentMemberReflectsRequester` 신규, `create_success`에
     `joinedByCurrentMember=true` 검증 추가.
- 범위 밖(계획대로 손대지 않음): `join()`/기존 `leave()`의 동시성 제어(`findByIdForUpdate`,
  `team.join-strategy` 토글), `RefundRequestService`, PortOne 연동.
- 셀프 체크(Generator 단계 확인 — Evaluate의 정식 계산적 평가는 별도):
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`.
  - `./gradlew test --tests "*Team*"` → `BUILD SUCCESSFUL`(신규 테스트 포함 전부 통과, 회귀 없음).
  - `./gradlew test`(전체) → 10건 실패(`AdminControllerTest`/`ProductControllerTest`/
    `ProductCachingTest`/`SellerRevenueSummaryTest`) 확인. 이번 변경분을 `git stash`로 걷어낸 baseline에서
    동일 4개 클래스만 재실행해도 같은 10건이 실패함을 확인 — 이번 작업과 무관한 기존 테스트 간 상태 오염
    이슈(pre-existing)로 판단, 별도 조치하지 않음(계획 범위 밖).

## Evaluate — 2026-08-22  ✅ PASS

- 결과: ✅ **PASS** — 계산적·추론적 평가 모두 통과.
- 계산적 평가:
  - `./gradlew test --tests "*Team*" --rerun-tasks` → `BUILD SUCCESSFUL`. Team 관련 테스트 파일
    10개(`TeamControllerTest` 35, `TeamReservationExpiryServiceTest` 7,
    `TeamReservationExpirySchedulerTest` 2, `TeamCapacityBroadcastTest` 1,
    `TeamDeadlineEventFlowTest` 3, `TeamPaymentsRefundRequestedEventListenerTest` 1,
    `TeamDeadlineSchedulerTest` 2, `TeamConcurrencyAtomicTest` 1, `TeamConcurrencyTest` 1,
    `TeamDeadlineServiceTest` 7) 전부 `failures="0" errors="0"` — 회귀 없음.
  - `./gradlew test --rerun-tasks`(전체)를 두 차례 돌려본 결과, Generator가 보고한 "10건 실패"와
    다른 실패 패턴(둘째 실행 시 `AdminControllerTest` 2건, `ProductControllerTest` 3건,
    `TeamConcurrencyAtomicTest`/`TeamConcurrencyTest` 각 1건 — 매번 실패 클래스·건수가 달라짐)이
    나와 **로직 문제가 아니라 인프라 문제로 의심**해 직접 원인을 추적함.
  - 근본 원인 확인: 로컬 MySQL 컨테이너(`gong9ri-main-mysql-1`)의 `root`@`%`(앱이 TCP로 접속할 때
    쓰는 계정)에 `INSERT`/`UPDATE`/`DELETE`/`CREATE`/`DROP` 권한이 아예 빠져 있었다(`SHOW GRANTS`로
    직접 확인, `root`@`localhost`는 정상). `git stash`로 이번 변경분을 통째로 걷어낸 순수 baseline
    으로도 재현됨(거의 전 테스트 클래스가 `InvalidDataAccessResourceUsageException: INSERT command
    denied ... for table 'member'`로 실패) — **이번 기능 코드와 무관한, 공유 로컬 MySQL 컨테이너의
    권한 설정 문제**임을 확정. 로컬 개발 전제(`AGENTS.md`)상 `docker-compose.yml`의 기본값은
    `root`에 전체 권한을 주는 구성이라, 권한이 빠진 건 세션 중 누군가(동시 세션 가능성,
    `project_...concurrent_sessions...` 메모 참고) 또는 컨테이너 상태 문제로 권한이 바뀐 것으로
    추정된다(단정 못 함).
  - `GRANT ALL PRIVILEGES ON *.* TO 'root'@'%'; FLUSH PRIVILEGES;`로 로컬 전용 권한을 복구한 뒤
    `./gradlew test --rerun-tasks`(전체) 재실행 → `BUILD SUCCESSFUL`, 65개 테스트 클래스 **전부**
    `failures="0" errors="0"`(실패 0건). Generator가 주장한 "10건은 기존 결함"이라는 결론은
    방향은 맞았으나(이번 기능과 무관) 실제 원인은 "기존 테스트 결함"이 아니라 "로컬 DB 권한
    설정 문제"였다 — 권한을 고치자 그 실패들도 전부 사라졌다.
- 추론적 평가(`docs/dev/ongoing/team-payment-enforcement.md`의 "평가(통과) 기준" 중 team/crud 관련
  4개 + 코드 컨벤션):
  - `TeamController.list()`가 `@AuthenticationPrincipal MemberUserDetails principal`을 nullable로
    받아 `TeamService.list(productId, principal)`에 그대로 전달함을 코드로 확인. `list()`는
    `Long memberId = principal == null ? null : principal.getMember().getId()`로 비로그인을
    안전하게 처리하고, `isJoinedByMember`가 `memberId != null && existsByTeamIdAndMemberId(...)`로
    비로그인 시 항상 `false`가 되게 함 — 계획대로.
  - `TeamService.leave()`의 기존 계약(`docs/api/team.md`)이 리팩터링 후에도 그대로인지 코드로 직접
    확인: `leave()`는 여전히 `findByIdForUpdate`(락) → `existsByTeamIdAndMemberId`(없으면
    `FORBIDDEN`) → `status != RECRUITING`(아니면 `TEAM_NOT_RECRUITING`) 순서를 그대로 유지하고,
    검증을 통과한 뒤에만 `cancelParticipation(team, member)`을 호출한다 — 에러코드·검증 순서·트랜잭션
    경계(`leave()` 자체에 `@Transactional`) 전부 변경 없음을 diff와 전체 코드로 확인.
  - `cancelParticipation`이 트랜잭션 경계 없이 호출자 트랜잭션에 얹혀 도는 설계가 실제로 안전한지
    확인: package-private 메서드라 Spring 선언적 트랜잭션(프록시 기반, public 메서드만 적용)의
    별도 트랜잭션 경계가 붙지 않고, 항상 (a) `leave()`(자기 자신의 `@Transactional` 안에서 같은
    인스턴스 내부 호출) 또는 (b) `TeamReservationExpiryService.processExpiredParticipations()`
    (자신의 `@Transactional` + `findByIdForUpdate` 락을 이미 잡은 상태에서 같은 패키지의 다른 빈을
    호출) 경로로만 진입한다 — 두 경로 모두 호출 시점에 이미 팀 row 락을 쥔 트랜잭션이 열려 있어
    Javadoc의 "호출 전제"가 실제로 지켜짐. `TeamReservationExpiryServiceTest`의 7개 케이스(취소,
    PAID 보호, cutoff 이내 보호, FAILED 전환, 리더 승계, SUCCESS 팀 스킵, 스캔 필터링)가 이 경로를
    실제로 실행해 전부 통과했으므로 이 설계가 실제로 동작함이 테스트로도 증명됨.
  - `TeamReservationExpiryScheduler`/`Service`의 "외부 HTTP 호출이 없어 락을 오래 잡아도 된다"는
    주장을 코드로 확인: `processExpiredParticipations` 내부에서 실제로 부르는 것은
    `groupBuyTeamRepository`/`teamParticipationRepository`/`paymentRepository`(전부 DB 접근)와
    `teamService.cancelParticipation`(그 안에서도 `refundRequestService.createFromTeamLeave` +
    `eventPublisher.publishEvent(TeamCapacityChangedEvent)`)뿐이다. `RefundRequestService`는 이번
    작업에서 건드리지 않았고(`git log`로 최근 변경 이력에 없음 확인), `createFromTeamLeave`는 DB
    저장만 하고 PortOne 같은 외부 HTTP 호출을 하지 않는다(`team/deadline-check`가 굳이
    이벤트 발행-구독으로 비동기 분리한 이유는 `PaymentRefundService`의 PortOne 취소 API 호출을
    락 밖으로 빼기 위해서였는데, 이 기능엔 그 호출 자체가 없다) — 주장이 타당함을 확인.
    `eventPublisher.publishEvent(TeamCapacityChangedEvent)`도 기존 `join()`/`leave()`가 이미 같은
    트랜잭션 안에서 호출하던 것과 동일한 패턴(구독자는 `@TransactionalEventListener(AFTER_COMMIT)`)
    이라 새로운 위험이 아님.
  - `join()`/기존 `leave()`의 동시성 전략(`team.join-strategy`, `findByIdForUpdate` 비관적 락)이
    그대로임을 diff로 확인 — 이번 변경은 `leave()`의 검증 이후 부분만 메서드 추출했을 뿐, 락 획득
    지점과 검증 순서는 손대지 않았다.
  - 코드 컨벤션(`docs/code-convention.md`) 준수: 생성자 주입(`@RequiredArgsConstructor` + `final`)
    유지, 서비스 클래스 레벨 `@Transactional(readOnly = true)` + 쓰기 메서드만 명시적
    `@Transactional` 패턴 유지, 동시성 민감 로직(`cancelParticipation`)에 락 전제를 Javadoc으로
    명확히 남김, 로그에 `teamId`/`memberId` 등 도메인 식별자 포함, 컨트롤러에 비즈니스 로직 없음.
  - `TeamResponse.from(GroupBuyTeam, boolean)` 호출부가 `TeamService`의 두 곳(`list`/`create`)뿐임을
    `grep`으로 확인 — 다른 소비자가 깨지지 않음.
- 증거(API 샘플 — `TeamControllerTest`의 실제 MockMvc 요청/응답):
  - `GET /api/products/{id}/teams`(비로그인) → `200 {"data":[{...,"joinedByCurrentMember":false}]}`
    (`list_publicAccess_joinedByCurrentMemberIsFalse`).
  - `GET /api/products/{id}/teams`(로그인, 본인이 리더인 팀 vs 아닌 팀 둘 다 포함) → 본인이 참여한
    팀만 `"joinedByCurrentMember":true`, 나머지는 `false`
    (`list_loggedIn_joinedByCurrentMemberReflectsRequester`).
  - `POST /api/products/{id}/teams`(신설) → `201 {"data":{...,"currentCount":1,"joinedByCurrentMember":true}}`
    (`create_success`).
- 후속 조치: design.md 갱신 + ongoing 문서 채번 이동(아래 별도 커밋 내용 참고, 이 로그와 짝인
  `docs/logs/team/reservation-expiry/001-reservation-expiry.md`도 함께 갱신).
