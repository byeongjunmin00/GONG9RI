# 공구팀 신설/참가/목록 (team/crud) — Design

## 개요

구매자(BUYER)가 상품에 공구팀을 신설하거나 기존 팀에 참가한다. 팀 신설자는 자동으로 리더+첫 참여자가 되고(`currentCount=1`), 정원이 다 차면 참가 처리 중 실시간으로 `SUCCESS`로 전환된다. 이 기능의 핵심은 "마지막 자리 경쟁" 상황에서 정원을 절대 넘기지 않는 동시성 제어(비관적 락)다.

## API / 인터페이스

- `GET/POST /api/products/{productId}/teams`, `POST /api/teams/{teamId}/join`,
  `POST /api/teams/{teamId}/leave`, `GET /api/teams/{teamId}/participants` — 상세: `docs/api/team.md`
- `TeamResponse`(목록/신설 응답)에 `joinedByCurrentMember`(boolean) 필드가 있다 — 이 요청을 보낸
  로그인 사용자 자신이 그 팀의 현재 참여자인지 여부(다른 참여자의 신원은 여전히 비공개). 비로그인
  요청이면 항상 `false`. `GET .../teams`는 `@AuthenticationPrincipal`을 nullable로 받아(permitAll)
  이 값을 채운다 — 상세: `team/reservation-expiry` 작업과 함께 도입, `docs/dev/team/reservation-expiry/design.md`
  참고.

## 데이터 모델

- `group_buy_team`, `team_participation` — 상세: `docs/db/group_buy_team.md`, `docs/db/team_participation.md`
- `deadline`은 팀 신설 시점 + 7일로 확정(2026-08-03, `docs/ERD.md` 반영)

## 규칙 / 검증

- 신설/참가는 `Role.BUYER`만 가능(판매자 시도 시 `403 FORBIDDEN`) — `docs/api/team.md` 계약
- **팀 신설 시 목표 인원 선택**: `POST /api/products/{productId}/teams`는 요청 body로 `targetParticipants`(int, 필수)를
  받는다. 구매자가 임의의 정수를 자유 입력하는 게 아니라, 그 상품에 판매자가 등록해둔 `price_tier.minCount`
  목록 중 정확히 하나와 일치해야 한다(범위 체크가 아니라 존재 여부 체크) — 일치하지 않으면
  `400 INVALID_TARGET_PARTICIPANTS`, 필드 자체가 없으면 `400 VALIDATION_FAILED`. 검증을 통과한 값이 그대로
  `GroupBuyTeam.maxParticipants`(정원 스냅샷)가 된다 — 더 이상 `product.maxParticipants`(상품에 하나뿐인
  상한 참고값)를 그대로 복사하지 않는다. `product.maxParticipants`는 여전히 존재하지만 "각 `price_tier.minCount`가
  넘지 않아야 할 상한 참고값"이라는 의미로만 남는다(서버가 이 상한을 강제하지는 않음, 프론트 등록 폼의
  가드레일로만 검증) — 상세: `docs/db/product.md`.
  - 구매자 화면(`product.js`/`product.html`)은 상품 상세 응답의 `priceTiers` 목록으로 라디오 버튼을 그려
    구매자가 하나를 고르게 한다. `renderTargetParticipantsOptions()`가 옵션을 그리는 시점에 **첫 번째
    옵션을 자동으로 선택된 상태**로 만든다(옵션이 1개든 여러 개든 동일) — 라디오의 DOM `checked` 속성뿐
    아니라 `updateCreateTeamButtonState()`가 실제로 검사하는 JS 변수 `selectedTargetParticipants`도
    함께 `tier.minCount`로 채워야 한다(DOM만 체크하고 변수를 안 채우면 "라디오는 체크돼 보이는데
    버튼은 계속 비활성"인 불일치가 남는다). 이후 사용자가 다른 옵션을 수동으로 클릭하면 기존 `change`
    이벤트 핸들러가 그 값으로 다시 채우고 버튼 상태를 재평가한다. 옵션 자체가 없는 상품(빈 `priceTiers`)은
    필드를 숨기고(`targetParticipantsFieldEl.hidden = true`) 자동 선택도 하지 않는다.
  - 이미 만들어진 `GroupBuyTeam`은 이후 판매자가 `price_tier`를 수정/삭제해도 영향받지 않는다(생성 시점
    스냅샷 원칙 유지).
- **동시성 제어**(`docs/db/group_buy_team.md`, `docs/policy/team-success-criteria.md`): `join`은 `team.join-strategy` 설정값(`application.yaml`, 기본 `lock`)에 따라 두 경로 중 하나로 처리된다 — API 계약/엔드포인트는 동일, 내부 전략만 다름.
  - **`lock`(기본값) — 비관적 락**:
    1. `GroupBuyTeamRepository.findByIdForUpdate`로 팀 row에 비관적 락(`SELECT ... FOR UPDATE`) 획득 — 없으면 `404 TEAM_NOT_FOUND`
    2. 락 획득 후 `ALREADY_JOINED` 확인(락으로 직렬화됐기 때문에 동시 중복 참가 요청도 안전하게 걸러짐)
    3. `currentCount >= maxParticipants`면 `409 TEAM_FULL`
    4. `GroupBuyTeam.increaseParticipant()`로 인원 증가, 도달 시 엔티티 내부에서 `SUCCESS`로 전환
    5. `TeamParticipation` 저장 — 전부 한 트랜잭션 안에서 처리
  - **`atomic` — 조건부 UPDATE**(성능 비교용 대안, `docs/logs/team/crud/003-atomic-comparison.md`):
    1. `existsById`로 존재만 확인 — 없으면 `404 TEAM_NOT_FOUND`(락 없음)
    2. `GroupBuyTeamRepository.incrementIfCapacity`(조건부 `UPDATE ... WHERE current_count < max_participants`)를 **먼저** 시도 — 영향 row 0건이면 `409 TEAM_FULL`
    3. 성공하면 `TeamParticipation` 저장 — 유니크 제약(`uk_team_member`, `team_id`+`member_id`)이 중복 참가를 막아줌, 위반 시 `409 ALREADY_JOINED`(트랜잭션 롤백으로 방금 증가시킨 인원수도 함께 취소됨)
    4. **순서 주의**: UPDATE를 참여기록 INSERT보다 먼저 해야 함 — INSERT를 먼저 하면 FK 체크 때문에 team row에 공유 락이 걸리고, 여러 스레드가 그 상태에서 UPDATE의 배타 락 승급을 동시에 기다리며 데드락이 실제로 재현됨(멀티스레드 테스트로 발견·수정).
  - **비교 결과**: 동일 k6 시나리오(VU 10/30/50)에서 두 전략의 p95 지연·처리량이 거의 동일하게 나옴 — 이 부하 수준에서는 팀 row 락 대기보다 HikariCP 커넥션 풀(기본 10개) 확보 대기나 로그인의 BCrypt 연산이 더 큰 병목일 가능성이 높음. 상세: `docs/logs/team/crud/003-atomic-comparison.md`.
  - 정확성은 두 경로 모두 `TeamConcurrencyTest`/`TeamConcurrencyAtomicTest`(정원 5명 팀에 8명 동시 참가 → 정확히 4명만 성공)로 동일하게 검증됨.
  - **스파이크 테스트(`lock` 전략)**: VU 100~2000(민병준)에서는 에러 없이 우아하게 열화(처리량 35~38 req/s로 평평, 지연만 선형 증가). VU 3000(전용운, 준비 단계 타임아웃 문제를 해결해서 이어서 측정)에서 **실제 breaking point 확인** — `checks_failed` 56.56%, 원인은 HikariCP 커넥션 타임아웃이 아니라 **Tomcat의 동시 연결 수용 한계**(TCP 연결 자체가 거부됨, 앱 프로세스는 안 죽음). 이후 이진 탐색(2500→2750→2875→2940→2970)으로 좁힌 결과 **정확한 한계점은 VU 2970(에러 0%)~3000(에러 37.68%) 사이, 즉 VU 약 3000 부근**으로 확정. 상세: `docs/logs/team/crud/004-spike-test.md`.
- 목록은 `RECRUITING` 상태만 반환, 인증 불필요(`GET /api/products/**`가 이미 permitAll)
- `team/deadline-check`(마감 지난 팀 자동 `FAILED`+환불)는 전용운이 구현 완료(`docs/dev/team/deadline-check/`) — `TeamService.join()`의 락 경로(`findByIdForUpdate`)를 재사용해 마감 처리와 참가 시도의 동시성 경합을 막음

## 참여자 목록 표시

참가를 고민하는 구매자가 "누가 벌써 참여했는지" 참고할 수 있게 `GET /api/teams/{teamId}/participants`로
그 팀의 현재 참여자 목록을 보여준다.

- **노출 정보**: `displayName`(마스킹된 이름, 첫 글자만 노출+나머지 글자수만큼 `*`. 1글자 이름은 `*` 하나로
  전체 마스킹), `isLeader`(팀장 여부), `joinedAt`(참여 시각 원본 ISO-8601, 화면에서는 "N일 전 참여"처럼
  대략적으로만 표시). `memberId`·실명 원문·이메일 등 식별정보는 응답에 포함하지 않는다.
- **정렬**: 팀장이 먼저, 이후 `joinedAt` 오름차순(참여한 순서). 리포지토리는 `joinedAt` 오름차순으로만
  가져오고, "리더 우선"은 서비스 계층에서 안정 정렬(stable sort)로 마무리한다.
- **인증**: 불필요(비로그인 공개, `SecurityConfig` permitAll) — 마스킹된 이름만 노출되고 `currentCount`처럼
  이미 공개된 정보와 같은 등급으로 취급한다.
- **에러**: `TEAM_NOT_FOUND`(404, 기존 코드 재사용) — 존재하지 않는 `teamId`.
- **N+1 방지**: `TeamParticipationRepositoryImpl.findAllByTeamIdWithMemberOrderByJoinedAtAsc`가
  `member`/`team`/`team.leader`를 모두 fetch join해 쿼리 1번으로 끝난다.
- **스키마 변경 없음**: 닉네임 필드를 새로 추가하지 않고 기존 `Member.name`을 읽기 시점에 마스킹한다(닉네임
  기능은 별도 스코프로 미룸). `TeamParticipation.joinedAt`/`GroupBuyTeam.leader`도 기존 컬럼 그대로 사용.
- **스코프 제외**: 참여자 상세 클릭, 참여자 목록 페이지네이션, 팀 신설 실시간 브로드캐스트에 참여자 목록
  변경 반영 — 전부 제외(팀 정원 자체가 작은 수이고, 사용자가 펼칠 때 재조회하는 것으로 충분하다고 판단).
- **프론트**: `product.js`의 `createTeamItem()`에 "참여자 보기" 토글 버튼 + 펼치기 패널을 추가, 처음 펼칠 때만
  개별 조회한다(팀 목록 로드 시점에 한꺼번에 불러오지 않음).

## 참여 취소 (team/leave)

`join()`과 대칭적인 `POST /api/teams/{teamId}/leave` — 참여를 취소해 자리를 즉시 반환한다. 상세
설계·환불 연동은 신규 개념 `refund/request`가 주로 다루므로(`docs/dev/refund/request/design.md`),
여기서는 team 쪽 규칙만 요약한다.

- **취소 가능 조건**: 로그인한 `BUYER`가 그 팀의 현재 참여자여야 하고(`FORBIDDEN`, 403), 팀 상태가
  `RECRUITING`이어야 한다(그 외 상태는 `TEAM_NOT_RECRUITING`, 409). **팀이 정원을 채워 `SUCCESS`로
  전환된 뒤에는 이 가드 때문에 그 팀의 어떤 참여자도 더는 참여를 취소할 수 없다** — 팀 결제의 환불이
  오직 이 경로로만 열려 있다는 전체 제약(`docs/dev/refund/request/design.md`)의 절반을 여기서
  담당한다.
- **마지막 참여자 취소 시 팀 자동 해체 (`FAILED`)**: 마지막 남은 참여자가 참여를 취소하면 정원이 0이 되면서
  `GroupBuyTeam.decreaseParticipant()`가 `currentCount`를 0으로 만들고 팀 상태를 `FAILED`로 자동 전환한다.
  취소한 사람이 리더였더라도 팀 상태가 `FAILED`가 되었으므로 리더 승계는 실행되지 않는다.
- **동시성**: `join()`과 동일한 `GroupBuyTeamRepository.findByIdForUpdate`(비관적 락)를 그대로
  재사용해, 취소와 동시에 다른 사람이 참가를 시도하는 경합을 직렬화한다.
- **한 트랜잭션 안에서 처리**: 참여 기록 실제 삭제(`TeamParticipationRepository.deleteByTeamIdAndMemberId`, 이 기능에서만 하드 삭제 — `docs/db/team_participation.md`) →
  `GroupBuyTeam.decreaseParticipant()`로 정원 감소(자리 즉시 반환 / 0명 시 FAILED 전환) → 취소한 사람이 리더였고 팀이 FAILED가 아니라면 `changeLeader()`로 그다음 최초 참가자에게 승계 → 취소한 사람의 `PAID` 결제가 있으면 `RefundRequestService.createFromTeamLeave()`로 환불 요청 자동 생성(같은 결제에 이미 대기 중인 요청이 있으면 스킵 — `docs/dev/refund/request/design.md` 참고).
- **내부 구조(team-payment-enforcement 이후)**: `leave(principal, teamId)`는 여전히 API 계약대로
  락 획득 → 참여자 검증(`FORBIDDEN`) → `RECRUITING` 검증(`TEAM_NOT_RECRUITING`)까지 담당하고, 검증
  통과 후의 실제 취소 효과(위 문단의 삭제→정원감소→리더승계→환불요청)는 package-private
  `cancelParticipation(GroupBuyTeam team, Member member)`로 추출돼 있다. 이 메서드는 `@Transactional`을
  별도로 걸지 않는다 — 항상 호출자가 이미 열어 둔 트랜잭션(과 그 트랜잭션이 쥔 팀 row 비관적 락) 안에서
  실행되는 게 전제라 새 트랜잭션 경계가 불필요하기 때문이다. `leave()` 자신(같은 인스턴스 내부 호출) 외에,
  같은 패키지의 `TeamReservationExpiryService`(미결제 참여 자동 만료 스케줄러, 신규 개념
  `team/reservation-expiry`)가 principal 없이 팀+참여자만으로 이 메서드를 재사용한다 — 상세:
  `docs/dev/team/reservation-expiry/design.md`.
- **실시간 브로드캐스트**: 기존 `TeamCapacityChangedEvent`(`join()`과 동일 이벤트/토픽)를 그대로
  재사용 — 신규 이벤트 불필요, 취소도 정원 변경이므로 같은 채널로 나간다.
- **에러**: `TEAM_NOT_FOUND`(404), `TEAM_NOT_RECRUITING`(409), `FORBIDDEN`(403), `UNAUTHORIZED`(401).
- **참가/신설 후 결제 강제(team-payment-enforcement)**: 참가(`join`)/신설(`create`) 성공 시
  프론트(`product.js`)가 성공 배너 대신 곧바로 결제 페이지(`checkout.html?productId=...&teamId=...`)로
  이동시킨다 — 결제를 끝내지 않고 이탈하면 10분 뒤 자동으로 자리가 반환된다(스케줄러, 상세
  `docs/dev/team/reservation-expiry/design.md`). 결제 페이지의 "취소하기"(`checkout.js`)와, 상품 상세
  페이지 팀 목록에서 `joinedByCurrentMember=true`인 팀에 새로 뜨는 "참여 취소" 버튼(`product.js`)
  모두 이 `POST /api/teams/{teamId}/leave`를 그대로 호출한다 — 서버 로직 변경 없이 기존 엔드포인트를
  재사용(마이페이지의 참여 취소와 동일 경로).

## 트래픽 제어 (발제 백엔드 도전과제)

k6 스파이크 테스트(`docs/logs/team/crud/004-spike-test.md`)로 `team/join`이 VU 약 3000 부근에서 Tomcat 동시 연결 수용 한계로 실제로 무너지는 걸 확인해뒀다. 그 실측 약점을 근거로, `POST /api/teams/{teamId}/join`에 애플리케이션 레벨 요청 제어를 추가했다.

- **메커니즘**: `common/filter/RateLimitFilter`(`OncePerRequestFilter`, `RequestLoggingFilter`보다 뒤에서 실행되도록 `@Order(HIGHEST_PRECEDENCE + 10)`)가 `POST /api/teams/{teamId}/join` 요청만 가로채, Redis 고정 윈도우 카운터(`INCR` + 첫 증가 시 `EXPIRE`)로 클라이언트별 요청 수를 센다. 인증·비즈니스 로직(DB 접근) 전에 필터 단계에서 차단해 불필요한 자원 소모를 막는다.
- **클라이언트 식별**: `X-Forwarded-For` 헤더 우선(Railway가 프록시 뒤에 있어서 `getRemoteAddr()`만 쓰면 프로덕션에서 전부 프록시 IP로 잡힘), 없으면(로컬 개발) `getRemoteAddr()`로 폴백.
- **임계값**: 윈도우 10초에 20회. **실측 근거 없는 초기값**이다(`refund-trigger` 1분 주기, 챗봇 15초 타임아웃과 같은 성격) — 정상 사용자가 "참가하기"를 실수로 여러 번 눌러도 절대 도달 안 하는 여유 있는 값이면서, 스크립트성 반복 요청은 확실히 걸러내는 수준으로 잡았다. 나중에 실사용 데이터로 재검토 여지가 있음.
- **초과 시 응답**: `429 Too Many Requests` + 공통 에러 응답 형식(`ErrorCode.TOO_MANY_REQUESTS`), 기존 `ApiAuthenticationEntryPoint`(401)와 동일한 패턴으로 필터에서 직접 `ApiResponse.failure(...)`를 `ObjectMapper`로 직렬화해서 응답.
- **fail-open**: Redis 호출 자체가 실패하면 요청을 막지 않고 통과시킨다 — rate limit이 잠깐 안 걸리는 것보다 Redis 장애가 핵심 기능(공구 참가)까지 막는 게 훨씬 나쁘다는 판단(AI 기능의 장애격리 원칙과 동일).
- **스코프 한계**: 같은 클라이언트(IP)가 반복 요청하는 흔한 시나리오(봇/스크립트/오작동 클라이언트)는 확실히 막지만, 서로 다른 실제 IP 수천 개가 동시에 몰리는 분산 대규모 트래픽(우리가 실측한 VU 3000 스파이크 같은 상황)까지 애플리케이션 레벨에서 막는 건 아니다 — 그건 WAF/CDN/로드밸런서 영역이라 스코프 밖.
- **실제 검증**: `k6/team-join-rate-limit-test.js`로 로컬 `bootRun`(실제 Redis 포함) 대상 실행 — 같은 클라이언트(고정 `X-Forwarded-For`)로 25회 연속 요청 시 1~20번째는 429가 아니고 21~25번째는 정확히 429(체크 100% 통과, `RateLimitFilter` 로그에 `트래픽 제어 — 요청 거절` 확인), 서로 다른 클라이언트(5개, 각기 다른 `X-Forwarded-For`)는 같은 서버가 동시에 burst 트래픽을 처리 중이어도 전부 429 없이 정상 참가 성공(오탐 없음). 상세 로그: `docs/logs/team/crud/005-rate-limit.md`.

## 실시간 메시징 (발제 백엔드 도전과제)

`team/join` 성공(다른 참여자가 공구팀에 참가)을 그 상품 페이지를 보고 있는 모든 클라이언트에게 실시간으로 알려준다. 지금까지 `product.js`는 자기 자신이 참가/신설을 성공시켰을 때만 팀 목록을 재조회했고, 다른 사용자의 참가는 새로고침 전까지 화면에 반영되지 않았다 — 이 공백을 메운다.

- **SSE가 아니라 WebSocket/STOMP를 쓴 이유**: 이미 있는 SSE(구매자 챗봇)는 "서버 → 그 요청을 보낸 한 클라이언트"로만 흐르는 1:1 스트림이다. 공구팀 정원 변동은 "서버 → 그 상품 페이지를 보고 있는 여러 클라이언트 전원"에게 동시에 알려야 하는 1:N 브로드캐스트라 구조적으로 다른 문제다 — SSE로 억지로 구현하면 각 연결마다 별도 상태 관리가 필요해지지만, STOMP의 pub/sub 토픽 모델이 이 문제에 정확히 들어맞는다.
- **커밋 후에만 발행**: `TeamService.joinWithLock`/`joinAtomic`이 성공하면 `TeamCapacityChangedEvent(productId, TeamJoinResponse)`를 발행하고, `TeamCapacityChangedEventListener`가 `@TransactionalEventListener(phase = AFTER_COMMIT)`로 받아 `/topic/products/{productId}/teams`로 브로드캐스트한다 — 기존 `TeamRefundedEvent`/`TeamRefundedEventListener`(환불 알림)와 완전히 동일한 패턴 재사용. 트랜잭션이 실제로 커밋된 뒤에만 발행해야 롤백된 참가가 화면에 유령처럼 나타나지 않는다.
- **브로커**: 별도 메시지 브로커 인프라 없이 Spring의 인메모리 심플 브로커(`enableSimpleBroker("/topic")`)만 쓴다 — 2인 팀·단일 Railway 인스턴스 규모에 맞는 선택. **스코프 한계**: 다중 인스턴스로 확장하면 이 브로드캐스트가 인스턴스 경계를 못 넘는다(이 프로젝트는 단일 인스턴스라 해당 없음, 정직하게 명시).
- **엔드포인트**: `/ws-team`(STOMP handshake), SockJS 폴백은 안 씀 — Railway가 표준 WebSocket 업그레이드를 지원하는 걸 전제로 하고, 레거시 브라우저/사내망 프록시 대응까지는 2인 팀 데모 스코프에서 과한 엔지니어링이라고 판단.
- **보안**: `/ws-team/**`는 permitAll — 팀 정원 정보는 이미 `GET /api/products/**`로 공개돼 있어서 새로운 정보 노출이 아니고, 개인화 채널도 아니라 STOMP 메시지 단위 인증은 스코프 밖.
- **장애격리**: 브로드캐스트 리스너가 예외를 던져도 참가 자체(`join()`의 HTTP 응답)는 항상 성공해야 한다 — `@TransactionalEventListener(AFTER_COMMIT)` 콜백의 예외는 Spring이 로그만 남기고 호출자에게 전파하지 않는다는 게 문서상 알려진 동작인데, 이 프로젝트는 프레임워크 동작을 추측하지 않고 실제로 검증하는 게 원칙이라 리스너에 임시로 예외를 던지게 만들어 실제로 `join()`이 여전히 정상 응답하는지 확인했다(검증 후 원복). 상세: `docs/logs/team/crud/006-realtime-messaging.md`.
- **스코프 한계**: `create()`(팀 신설)는 브로드캐스트 대상이 아니다 — "참가로 인한 정원 변동"만 다룬다. 팀 신설 자체를 실시간으로 알리는 건 이번 스코프 밖.
- **프론트**: `product.js`가 페이지 로드 시 `/ws-team`에 연결해 자기 상품의 토픽을 구독하고, 메시지를 받으면 세밀한 DOM 패치 없이 기존 `loadTeams()`를 그대로 재사용해서 다시 그린다(기존 코드 스타일과 동일). STOMP 클라이언트(CDN `@stomp/stompjs`) 로드나 연결이 실패해도 조용히 넘어가고 기존 수동 새로고침 흐름으로 자연스럽게 폴백한다 — 실시간 갱신은 있으면 좋은 부가 기능이지 핵심 기능이 아니다.
- **실제 검증**: 신규 `TeamCapacityBroadcastTest`가 실제 Spring `WebSocketStompClient`(목 아님)로 `/ws-team`에 접속해 토픽을 구독한 뒤 `TeamService.join()`을 호출하고, 5초 안에 정확한 페이로드(`currentCount` 등)가 도착하는지 확인 — 이 프로젝트 첫 WebSocket 기능이라 서버·클라이언트 양쪽의 Jackson 3(`tools.jackson`) 직렬화 호환성도 이 테스트로 실제 확인됐다(Spring Framework 7의 `JacksonJsonMessageConverter` 사용, 구버전 `MappingJackson2MessageConverter`는 deprecated). UI 수준(실제 브라우저 두 탭에서 화면이 갱신되는지)은 이 환경에 브라우저 자동화 도구가 없어 직접 확인은 못 했다 — 사용자가 수동으로 확인 필요.

## 관련 코드 위치

- `entity/{GroupBuyTeam,TeamStatus,TeamParticipation}.java` — `TeamParticipation`에 `uk_team_member`(team_id+member_id) 유니크 제약 추가. `GroupBuyTeam.decreaseParticipant()`(participation 감소,
  0이 되면 `FAILED`)/`changeLeader(Member)`(리더 승계)
- `dto/{TeamResponse,TeamJoinResponse,TeamCreateRequest,TeamParticipantResponse}.java` — `TeamCreateRequest`는
  팀 신설 요청 body(`targetParticipants`), `TeamParticipantResponse`는 참여자 목록 응답(마스킹된 이름/팀장
  여부/참여 시각), `TeamJoinResponse`는 `join()`/`leave()` 공용 응답. `TeamResponse`는 `joinedByCurrentMember`
  (boolean, team-payment-enforcement) 필드를 가지며 `from(GroupBuyTeam team, boolean joinedByCurrentMember)`로
  생성한다(호출부가 항상 명시적으로 채우도록 정적 팩터리 시그니처에 강제).
- `repository/{GroupBuyTeamRepository,TeamParticipationRepository,PriceTierRepository}.java` —
  `findByIdForUpdate`(락 경로), `incrementIfCapacity`(원자적 경로), `findByProductIdOrderByMinCountAsc`
  (신설 시 `targetParticipants` 존재 검증), `findAllByTeamIdWithMemberOrderByJoinedAtAsc`(참여자 목록 fetch join),
  `deleteByTeamIdAndMemberId`/`findFirstByTeamIdOrderByJoinedAtAsc`(team/leave에서 추가 — 참여 기록 삭제/리더 승계 대상 조회),
  `existsByTeamIdAndMemberId`(`joinedByCurrentMember` 판정에도 재사용)
- `service/TeamService.java` — `create()`가 `PriceTierRepository`로 `price_tier.minCount` 존재 검증 후
  그 값으로 팀 생성(응답은 `joinedByCurrentMember=true` 고정), `join()`은 `team.join-strategy`로
  `joinWithLock`/`joinAtomic` 분기, `list(productId, principal)`은 principal이 null(비로그인)이면
  팀마다 `joinedByCurrentMember=false`, 로그인이면 팀별로 개별 판정, `participants()`는 팀 존재 검증 후
  리더 우선 정렬+마스킹 매핑, `leave()`는 `findByIdForUpdate` 재사용 + 참여자/RECRUITING 검증까지만
  담당하고 실제 취소 효과(정원 감소·리더 승계·환불 요청)는 package-private `cancelParticipation(team, member)`로
  위임 — 이 메서드는 `@Transactional` 없이 호출자의 트랜잭션(과 팀 row 락) 안에서만 실행되는 게 전제이며,
  같은 패키지의 `TeamReservationExpiryService`(`team/reservation-expiry`)도 재사용한다.
- `controller/TeamController.java` — `leave()`(`POST /api/teams/{teamId}/leave`), `list()`는
  `@AuthenticationPrincipal MemberUserDetails principal`을 nullable로 받는다(permitAll이라 비로그인은
  자동으로 null 주입).
- `common/exception/ErrorCode.java` — `TEAM_NOT_FOUND`/`TEAM_FULL`/`ALREADY_JOINED`/`INVALID_TARGET_PARTICIPANTS`/`TEAM_NOT_RECRUITING` 추가 (`LAST_PARTICIPANT_CANNOT_LEAVE` 제거)
- `src/main/resources/application.yaml` — `team.join-strategy: lock`(기본값)
- `k6/team-join-load-test.js` — 두 전략 공통 부하테스트 스크립트(설정값만 바꿔 재사용)
- `common/filter/RateLimitFilter.java` — 트래픽 제어 필터
- `common/exception/ErrorCode.java` — `TOO_MANY_REQUESTS` 추가
- `k6/team-join-rate-limit-test.js` — 트래픽 제어 전용 검증 스크립트(처리량이 아니라 429 발동 여부 확인)
- `config/WebSocketConfig.java` — STOMP 엔드포인트/브로커 설정
- `event/{TeamCapacityChangedEvent,TeamCapacityChangedEventListener}.java` — 실시간 메시징
- `src/main/resources/static/product.html`, `js/product.js` — 실시간 갱신 구독(프론트). 참가/신설(`handleJoin`/
  `handleCreateTeam`) 성공 시 배너 대신 `window.location.href`로 `checkout.html?productId=...&teamId=...`로
  강제 이동(team-payment-enforcement). `createTeamItem()`은 `team.joinedByCurrentMember`가 true면
  "참가하기" 대신 "참여 취소" 버튼(`handleLeaveTeam`, 확인창 후 `POST /teams/{teamId}/leave` →
  `loadTeams()` 재조회)을 렌더링한다.
- `src/main/resources/static/js/checkout.js` — `handleCancel()`이 `currentTeamId`가 있으면 상품
  페이지로 돌아가기 전에 먼저 `POST /teams/{teamId}/leave`를 호출한다(team-payment-enforcement,
  실패해도 이동은 진행 — 이미 취소된 상태 등은 사용자에게 에러로 막을 필요가 없어서).
- 테스트: `controller/TeamControllerTest.java`(일반 케이스 13개 + 참여자 목록 5개 + 참여 취소(leave) 12개 —
  성공/자리재참가/리더승계/마지막참여자탈퇴시FAILED전환(`leave_lastParticipant_teamBecomesFailed`)/
  TEAM_NOT_RECRUITING/FORBIDDEN/TEAM_NOT_FOUND/UNAUTHORIZED/PAID결제환불요청자동생성/결제없으면생성안됨/
  자동환불설정즉시APPROVED/재참가후재탈퇴중복방지 + `joinedByCurrentMember` 비로그인/로그인별 검증 3개
  — team-payment-enforcement), `service/TeamConcurrencyTest.java`(락 경로 동시성 검증),
  `service/TeamConcurrencyAtomicTest.java`(원자적 경로 동시성 검증, `@TestPropertySource`로 전략 전환),
  `common/filter/RateLimitFilterTest.java`(트래픽 제어 검증), `event/TeamCapacityBroadcastTest.java`(실시간 메시징 검증)
- **미결제 참여 자동 만료**(신규 개념, 신설/참가 후 10분 안에 결제 미완료 시 자동 취소)는 이 기능
  범위 밖이다 — `docs/dev/team/reservation-expiry/design.md` 참고.
