# 공구팀 신설/참가/목록 (team/crud) — Design

## 개요

구매자(BUYER)가 상품에 공구팀을 신설하거나 기존 팀에 참가한다. 팀 신설자는 자동으로 리더+첫 참여자가 되고(`currentCount=1`), 정원이 다 차면 참가 처리 중 실시간으로 `SUCCESS`로 전환된다. 이 기능의 핵심은 "마지막 자리 경쟁" 상황에서 정원을 절대 넘기지 않는 동시성 제어(비관적 락)다.

## API / 인터페이스

- `GET/POST /api/products/{productId}/teams`, `POST /api/teams/{teamId}/join` — 상세: `docs/api/team.md`

## 데이터 모델

- `group_buy_team`, `team_participation` — 상세: `docs/db/group_buy_team.md`, `docs/db/team_participation.md`
- `deadline`은 팀 신설 시점 + 7일로 확정(2026-08-03, `docs/ERD.md` 반영)

## 규칙 / 검증

- 신설/참가는 `Role.BUYER`만 가능(판매자 시도 시 `403 FORBIDDEN`) — `docs/api/team.md` 계약
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

- `entity/{GroupBuyTeam,TeamStatus,TeamParticipation}.java` — `TeamParticipation`에 `uk_team_member`(team_id+member_id) 유니크 제약 추가
- `dto/{TeamResponse,TeamJoinResponse}.java`
- `repository/{GroupBuyTeamRepository,TeamParticipationRepository}.java` — `findByIdForUpdate`(락 경로), `incrementIfCapacity`(원자적 경로)
- `service/TeamService.java` — `join()`이 `team.join-strategy`로 `joinWithLock`/`joinAtomic` 분기
- `controller/TeamController.java`
- `common/exception/ErrorCode.java` — `TEAM_NOT_FOUND`/`TEAM_FULL`/`ALREADY_JOINED` 추가
- `src/main/resources/application.yaml` — `team.join-strategy: lock`(기본값)
- `k6/team-join-load-test.js` — 두 전략 공통 부하테스트 스크립트(설정값만 바꿔 재사용)
- `common/filter/RateLimitFilter.java` — 트래픽 제어 필터
- `common/exception/ErrorCode.java` — `TOO_MANY_REQUESTS` 추가
- `k6/team-join-rate-limit-test.js` — 트래픽 제어 전용 검증 스크립트(처리량이 아니라 429 발동 여부 확인)
- `config/WebSocketConfig.java` — STOMP 엔드포인트/브로커 설정
- `event/{TeamCapacityChangedEvent,TeamCapacityChangedEventListener}.java` — 실시간 메시징
- `src/main/resources/static/product.html`, `js/product.js` — 실시간 갱신 구독(프론트)
- 테스트: `controller/TeamControllerTest.java`(일반 케이스 13개), `service/TeamConcurrencyTest.java`(락 경로 동시성 검증), `service/TeamConcurrencyAtomicTest.java`(원자적 경로 동시성 검증, `@TestPropertySource`로 전략 전환), `common/filter/RateLimitFilterTest.java`(트래픽 제어 검증), `event/TeamCapacityBroadcastTest.java`(실시간 메시징 검증)
