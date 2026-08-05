# 003-atomic-comparison — team/join 비관적 락 vs 원자적 UPDATE 비교

## Attempt 1 — 2026-08-05

- 목적: 002-loadtest(베이스라인)에서 확인한 "처리량 정체 + p95 지연 4.2배 증가" 병목에 대해, 실제 대안(원자적 조건부 UPDATE)을 구현해서 같은 k6 시나리오로 before/after 비교.
- 구현:
  - `application.yaml`에 `team.join-strategy`(기본 `lock`) 토글 추가, `TeamService.join()`이 이 값으로 `joinWithLock`/`joinAtomic` 분기. API 계약/엔드포인트는 하나로 유지.
  - `GroupBuyTeamRepository.incrementIfCapacity` — `UPDATE ... SET current_count=current_count+1, status=CASE WHEN ... THEN SUCCESS ELSE status END WHERE id=? AND current_count<max_participants`, 영향 row 수로 성공/실패 판정.
  - `TeamParticipation`에 `uk_team_member`(team_id+member_id) 유니크 제약 신규 추가 — 원자적 경로가 락 없이도 중복 참가를 안전하게 거를 수 있게 함.
- **1차 시도 실패 → 데드락 발견**: 처음엔 "참여기록 INSERT 먼저 → 정원 UPDATE"순서로 짰는데, `TeamConcurrencyAtomicTest`(정원 5명, 8명 동시 참가) 돌리자 `successCount`가 기대값(4)보다 훨씬 적게(2) 나옴. 원인: INSERT가 FK 체크 때문에 team row에 **공유 락**을 걸고, 그 상태에서 UPDATE가 **배타 락**으로 승급하려 하는데, 여러 스레드가 동시에 이 순서를 밟으면 서로의 배타 락 승급을 기다리며 **데드락**이 남(InnoDB가 일부 트랜잭션을 강제 종료시킴 → `BusinessException`이 아닌 다른 예외라 테스트에서 안 잡히고 조용히 실패). **수정**: 순서를 "UPDATE(정원 증가) 먼저 → 성공하면 참여기록 INSERT" 로 바꿈 — 이 트랜잭션이 배타 락을 먼저 확보해두면, 뒤이은 INSERT의 FK 공유 락 요청은 자기 자신의 락이라 즉시 허용됨. 수정 후 `TeamConcurrencyAtomicTest` 5회 연속 통과 확인(동시성 버그는 1회 통과로 안심 안 하고 반복 검증).
- 정확성 검증: `TeamConcurrencyAtomicTest`(원자적 경로, 정원 5명에 8명 동시 참가 → 정확히 4명 성공) + 기존 `TeamConcurrencyTest`(락 경로) 둘 다 통과. 전체 71케이스(기존 70 + 신규 1) 회귀 없음.
- 유니크 제약 반영 확인: `SHOW CREATE TABLE team_participation`에서 `UNIQUE KEY uk_team_member (team_id, member_id)` 실제 생성 확인.

## Attempt 1 (Evaluate) — 2026-08-05 ✅ PASS (예상과 다른 결과)

k6로 동일 시나리오(`k6/team-join-load-test.js`, VU 10/30/50)를 `lock`/`atomic` 각각 앱을 재시작해가며 실행. **k6 실행 후엔 이전에 배운 교훈([[feedback_loadtest_data_cleanup]])대로 `k6%` 접두사 데이터를 매번 직접 정리함.**

### 결과 비교

| VU | 전략 | p50 | p90 | p95 | 처리량(req/s) |
|---|---|---|---|---|---|
| 10 | lock | 72.14ms | 110.9ms | 117.21ms | 27.18 |
| 10 | atomic | 71.4ms | 101.97ms | 110.54ms | 27.53 |
| 30 | lock | 90.71ms | 255.56ms | 298.31ms | 33.97 |
| 30 | atomic | 94.7ms | 255.83ms | 300.53ms | 34.30 |
| 50 | lock | 154.75ms | 420.04ms | 503.13ms | 35.13 |
| 50 | atomic | 102.84ms | 404.79ms | 485.96ms | 35.16 |

### 해석 — "atomic이 이겼다"고 말할 수 없음, 더 정직한 결론

두 전략의 수치가 **거의 동일함**(오차 범위 수준). 원래 가설("락 대기 시간이 줄어서 빨라질 것")과 다른 결과라, 억지로 "atomic이 낫다"고 결론내지 않고 원인을 다시 생각해봄:

- 이 앱은 `HikariCP` 커넥션 풀 크기를 별도로 설정 안 해서 **기본값(10개)** 그대로 씀. VU 30/50은 이미 커넥션 풀 크기를 훨씬 초과하는 동시 요청이라, **팀 row 락 대기보다 "커넥션 풀에서 빈 커넥션 기다리기"가 더 앞단의 병목일 가능성이 높음** — 이 경우 join() 내부 로직(락이든 원자적 UPDATE든)이 뭐든 간에 커넥션을 기다리는 시간이 지배적이라 두 전략의 차이가 안 드러남.
- k6 시나리오가 **로그인 + 참가** 2단계라, 로그인의 `PasswordEncoder`(BCrypt) 검증 자체가 의도적으로 CPU 비용이 큰 연산임 — 동시 요청이 늘수록 이 CPU 비용이 코어 수만큼 경쟁하면서 생기는 지연이, 팀 row 락 경쟁으로 인한 지연과 섞여서 측정됐을 가능성이 있음.
- 즉 002-loadtest에서 "비관적 락 때문"이라고 단정했던 지연 증가가, 실제로는 **커넥션 풀 크기 + 로그인 CPU 비용**이 더 큰 비중을 차지했을 수 있다는 게 이번 비교로 드러난 더 정확한 그림.

### 다음 단계(스코프 밖, 참고용)

- 진짜 병목이 뭔지 더 좁히려면: (1) 로그인 없이 이미 인증된 세션으로 join만 반복하는 시나리오로 다시 재보거나, (2) HikariCP 풀 크기를 늘려서(예: 30) 같은 시나리오를 다시 재보는 것으로 "커넥션 풀 vs 락" 중 뭐가 진짜 병목인지 분리해볼 수 있음.
- 코드 자체(원자적 UPDATE 경로)는 정확성이 검증된 정식 대안으로 남겨두되(`team.join-strategy=atomic`로 언제든 전환 가능), 기본값은 `lock`으로 유지(성능 차이가 없으니 굳이 바꿀 이유 없음, 락 방식이 더 오래 검증된 경로).
