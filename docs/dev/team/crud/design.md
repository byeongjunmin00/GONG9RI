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
  - **스파이크 테스트(`lock` 전략)**: VU 100~2000(민병준)에서는 에러 없이 우아하게 열화(처리량 35~38 req/s로 평평, 지연만 선형 증가). VU 3000(전용운, 준비 단계 타임아웃 문제를 해결해서 이어서 측정)에서 **실제 breaking point 확인** — `checks_failed` 56.56%, 원인은 HikariCP 커넥션 타임아웃이 아니라 **Tomcat의 동시 연결 수용 한계**(TCP 연결 자체가 거부됨, 앱 프로세스는 안 죽음). 진짜 한계점은 VU 2000(에러 0%)~3000(에러 37.68%) 사이로 좁혀짐. 상세: `docs/logs/team/crud/004-spike-test.md`.
- 목록은 `RECRUITING` 상태만 반환, 인증 불필요(`GET /api/products/**`가 이미 permitAll)
- `team/deadline-check`(마감 지난 팀 자동 `FAILED`+환불)는 전용운이 구현 완료(`docs/dev/team/deadline-check/`) — `TeamService.join()`의 락 경로(`findByIdForUpdate`)를 재사용해 마감 처리와 참가 시도의 동시성 경합을 막음

## 관련 코드 위치

- `entity/{GroupBuyTeam,TeamStatus,TeamParticipation}.java` — `TeamParticipation`에 `uk_team_member`(team_id+member_id) 유니크 제약 추가
- `dto/{TeamResponse,TeamJoinResponse}.java`
- `repository/{GroupBuyTeamRepository,TeamParticipationRepository}.java` — `findByIdForUpdate`(락 경로), `incrementIfCapacity`(원자적 경로)
- `service/TeamService.java` — `join()`이 `team.join-strategy`로 `joinWithLock`/`joinAtomic` 분기
- `controller/TeamController.java`
- `common/exception/ErrorCode.java` — `TEAM_NOT_FOUND`/`TEAM_FULL`/`ALREADY_JOINED` 추가
- `src/main/resources/application.yaml` — `team.join-strategy: lock`(기본값)
- `k6/team-join-load-test.js` — 두 전략 공통 부하테스트 스크립트(설정값만 바꿔 재사용)
- 테스트: `controller/TeamControllerTest.java`(일반 케이스 13개), `service/TeamConcurrencyTest.java`(락 경로 동시성 검증), `service/TeamConcurrencyAtomicTest.java`(원자적 경로 동시성 검증, `@TestPropertySource`로 전략 전환)
