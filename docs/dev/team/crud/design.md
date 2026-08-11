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
- 테스트: `controller/TeamControllerTest.java`(일반 케이스 13개), `service/TeamConcurrencyTest.java`(락 경로 동시성 검증), `service/TeamConcurrencyAtomicTest.java`(원자적 경로 동시성 검증, `@TestPropertySource`로 전략 전환), `common/filter/RateLimitFilterTest.java`(트래픽 제어 검증)
