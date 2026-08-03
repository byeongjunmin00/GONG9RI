# 공구팀 신설/참가/목록 (team/crud)

대상: team/crud
담당: 민병준

## 배경 / 요구

`docs/api/team.md` 계약대로 목록(공개)/신설(구매자)/참가(구매자) 구현. 이 프로젝트의 핵심 동시성 시나리오(정원 마지막 자리 경쟁)가 실제로 들어가는 지점 — `docs/db/group_buy_team.md`에 확정된 비관적 락 방식을 그대로 구현한다. 팀 유지 마감기한은 오늘 "생성 시점 + 7일"로 확정함(문서에 없던 부분).

## 설계

- `GroupBuyTeam`(→`Product`, →`Member` leader), `TeamStatus` enum, `TeamParticipation`(→`GroupBuyTeam`, →`Member`)
- 참가(`join`) 트랜잭션 순서: 팀을 비관적 락으로 조회(`@Lock(PESSIMISTIC_WRITE)`) → 이미 참가했는지 확인(락 획득 후 확인이라 동시성 안전) → 정원 확인 → 인원 증가(도달 시 SUCCESS 전환) → 참여 기록 저장
- 신설/참가는 구매자(BUYER)만 가능, 판매자면 `403 FORBIDDEN`
- 참고 계약: `docs/api/team.md`, `docs/db/group_buy_team.md`, `docs/db/team_participation.md`, `docs/policy/team-success-criteria.md`

## 태스크

- [ ] `GroupBuyTeam`, `TeamStatus`, `TeamParticipation` 엔티티
- [ ] `TeamResponse`(신설/목록), `TeamJoinResponse`(참가) DTO
- [ ] `GroupBuyTeamRepository`(상품별 RECRUITING 목록, 비관적 락 단건조회), `TeamParticipationRepository`(중복확인)
- [ ] `TeamService` (역할검사, 락, 정원/성사 처리)
- [ ] `TeamController` (3개 엔드포인트)
- [ ] `ErrorCode`에 `TEAM_NOT_FOUND`/`TEAM_FULL`/`ALREADY_JOINED` 추가
- [ ] `docs/ERD.md` deadline 설명에 "생성+7일" 반영
- [ ] 일반 테스트(목록/신설/참가 성공·실패 케이스)
- [ ] **동시성 테스트**: 정원 4자리 남은 팀에 8명 동시 참가 → 정확히 4명만 성공

## 평가(통과) 기준

- 목록: 비로그인 200, RECRUITING만 반환
- 신설: BUYER 201(currentCount=1, deadline=+7일) / SELLER 403 / 비로그인 401 / 없는 상품 404
- 참가: 정상 200(count 증가) / 정원도달시 SUCCESS 전환 / 정원초과 409 TEAM_FULL / 중복참가 409 ALREADY_JOINED / 없는팀 404 / SELLER 403 / 비로그인 401
- 동시 참가 테스트: 정확히 정원만큼만 성공, 나머지 TEAM_FULL, 최종 currentCount=maxParticipants, status=SUCCESS
- `./gradlew test` 통과
