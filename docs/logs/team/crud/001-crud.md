# 001-crud — 공구팀 신설/참가/목록 (로그)

## Attempt 1 — 2026-08-03  ✅ PASS
- 시도: `GroupBuyTeam`/`TeamStatus`/`TeamParticipation` 엔티티, `TeamResponse`/`TeamJoinResponse` DTO, `GroupBuyTeamRepository`(`@Lock(PESSIMISTIC_WRITE)`로 `findByIdForUpdate`), `TeamParticipationRepository`, `TeamService`(락 획득 → ALREADY_JOINED 확인 → TEAM_FULL 확인 → 증가 → 저장 순서), `TeamController`(3개 엔드포인트), `ErrorCode`에 `TEAM_NOT_FOUND`/`TEAM_FULL`/`ALREADY_JOINED` 추가. `docs/ERD.md`에 deadline=생성+7일 결정 반영. 일반 테스트(`TeamControllerTest`) 13케이스 + 동시성 테스트(`TeamConcurrencyTest`) 1케이스 작성.
- 결과: `./gradlew build` **첫 시도에 전체 통과**. 전체 34케이스(기존 auth 7 + product 12 + team 13 + 동시성 1 + 앱컨텍스트 1) 전부 성공.
- 동시성 테스트 상세: 정원 5명(리더 포함 1명 이미 참여) 팀에 8명이 동시에 `join` 시도 → 정확히 4명 성공, 4명 `TEAM_FULL`, 최종 `currentCount=5`, `status=SUCCESS` 확인. `ExecutorService` + `CountDownLatch`로 8개 스레드를 동시에 출발시켜 실제 레이스 상황을 재현했고, 비관적 락이 정원을 절대 넘기지 않는다는 걸 실측으로 검증함.
- 참고(테스트 설계): 동시성 테스트는 여러 스레드가 서로 다른 DB 커넥션/트랜잭션을 쓰기 때문에, 다른 테스트들처럼 `@Transactional` 롤백 방식을 쓰면 워커 스레드가 메인 스레드의 미커밋 데이터를 못 보는 문제가 있음 — 그래서 이 테스트만 `@Transactional` 없이 실제 커밋시키고 `@AfterEach`에서 수동으로 정리(FK 순서: team_participation → group_buy_team → price_tier → product → member).
- 증거(API 샘플, MockMvc):
  - `POST /api/products/{id}/teams`(BUYER) → `201 {"data":{"teamId":..,"currentCount":1,"status":"RECRUITING","deadline":"2026-08-10T..."}}` (deadline이 생성 시점+7일 범위인지 리포지토리로 직접 확인)
  - `POST /api/teams/{id}/join`(정원 도달) → `200 {"data":{"currentCount":2,"status":"SUCCESS"}}`
  - `POST /api/teams/{id}/join`(정원 초과) → `409 {"code":"TEAM_FULL"}`
  - `POST /api/teams/{id}/join`(중복 참가) → `409 {"code":"ALREADY_JOINED"}`
- DB 증거: `group_buy_team`/`team_participation` 스키마 확인, 일반 테스트는 롤백으로 0건, 동시성 테스트는 수동 정리 후 전 테이블 0건 확인.
