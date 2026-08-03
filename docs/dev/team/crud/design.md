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
- **동시성 제어**(`docs/db/group_buy_team.md`, `docs/policy/team-success-criteria.md`): `join` 처리 순서
  1. `GroupBuyTeamRepository.findByIdForUpdate`로 팀 row에 비관적 락(`SELECT ... FOR UPDATE`) 획득 — 없으면 `404 TEAM_NOT_FOUND`
  2. 락 획득 후 `ALREADY_JOINED` 확인(락으로 직렬화됐기 때문에 동시 중복 참가 요청도 안전하게 걸러짐)
  3. `currentCount >= maxParticipants`면 `409 TEAM_FULL`
  4. `GroupBuyTeam.increaseParticipant()`로 인원 증가, 도달 시 엔티티 내부에서 `SUCCESS`로 전환
  5. `TeamParticipation` 저장 — 전부 한 트랜잭션 안에서 처리
- 목록은 `RECRUITING` 상태만 반환, 인증 불필요(`GET /api/products/**`가 이미 permitAll)
- `team/deadline-check`(마감 지난 팀 자동 `FAILED`+환불)는 `payment` 기능이 있어야 의미가 있어 **이번 스코프 밖** — `docs/policy/refund-trigger.md` 참고, 다음에 별도 구현

## 관련 코드 위치

- `entity/{GroupBuyTeam,TeamStatus,TeamParticipation}.java`
- `dto/{TeamResponse,TeamJoinResponse}.java`
- `repository/{GroupBuyTeamRepository,TeamParticipationRepository}.java` — `findByIdForUpdate`가 비관적 락 지점
- `service/TeamService.java`
- `controller/TeamController.java`
- `common/exception/ErrorCode.java` — `TEAM_NOT_FOUND`/`TEAM_FULL`/`ALREADY_JOINED` 추가
- 테스트: `controller/TeamControllerTest.java`(일반 케이스 13개), `service/TeamConcurrencyTest.java`(동시 참가 8건 중 정원만큼만 성공하는지 실제 멀티스레드로 검증 — `@Transactional` 안 씀, 수동 정리)
