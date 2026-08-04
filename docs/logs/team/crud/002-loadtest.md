# 002-loadtest — team/join 부하테스트 (k6 베이스라인)

## Attempt 1 — 2026-08-04 ✅ PASS

- 목적: `TeamConcurrencyTest`(멀티스레드 유닛테스트)로는 이미 "비관적 락이 정원을 절대 안 넘긴다"는 **정확성**을 검증했지만, "동시 요청이 많아지면 응답이 얼마나 느려지는지"는 실측한 적이 없었음. 발제 필수항목 "부하테스트" 대응 + 향후 "성능병목개선(before/after)" 비교의 베이스라인 확보.
- 스크립트: `k6/team-join-load-test.js` (커피숍 프로젝트 `k6/order-concurrency-test.js` 패턴 재사용). `setup()`에서 판매자 1명·상품 1개(정원 100000, VU 수보다 훨씬 크게 잡아 TEAM_FULL 없이 전부 성공하는 조건으로 설계)·리더 1명·팀 1개·VU 수만큼의 구매자 계정을 실제 API(`signup`/`login`/`products`/`teams`)로 생성. 각 VU는 로그인 1번 + `POST /api/teams/{teamId}/join` 1번씩 수행(`shared-iterations` executor, VU 수 = iteration 수).
- 실행: 로컬 `./gradlew bootRun` + 로컬 MySQL로 `k6 run -e VUS={10,30,50} k6/team-join-load-test.js` 3회 순차 실행.

### 결과

| VU | 성공률 | http_req_duration p50 | p90 | p95 | 처리량(req/s) |
|---|---|---|---|---|---|
| 10 | 100% (20/20 checks) | 69.87ms | 99.55ms | 114.47ms | 27.5 |
| 30 | 100% (60/60 checks) | 92.9ms | 255.3ms | 296.66ms | 34.85 |
| 50 | 100% (100/100 checks) | 102.77ms | 397.93ms | 482.94ms | 35.58 |

### 관찰한 병목 패턴

- **정확성은 3단계 전부 유지됨** — `TEAM_FULL`/실패 0건, 체크 100% 통과(동시 참가자가 늘어도 `TEAM_FULL` 없이 전부 성공 확인 = 유닛테스트 결과와 일치).
- **처리량(req/s)은 VU 10→50에서 27.5→35.58로 거의 안 늘어나는데, p95 지연은 114ms→483ms로 약 4.2배 증가.** 이게 비관적 락의 전형적인 신호 — 동시 요청이 늘어도 서버가 그만큼 "병렬로" 처리하는 게 아니라, `group_buy_team` row 락을 잡은 트랜잭션이 끝날 때까지 나머지가 순서대로 대기하면서 큐잉 지연만 쌓이는 것. 즉 처리 능력(처리량) 자체는 한계에 근접해있고, 늘어난 동시 요청은 대부분 "대기 시간"으로 흡수됨.
- 이 결과는 저널 6-1 섹션에 미리 남겨뒀던 가설("비관적 락은 대기가 생기지만 확실함")을 실측으로 뒷받침함.

### 다음 단계(스코프 밖, 참고용)

- 원자적 조건부 UPDATE(`UPDATE group_buy_team SET current_count = current_count + 1 WHERE id = ? AND current_count < max_participants`) 방식으로 바꿨을 때 같은 시나리오에서 p95가 어떻게 달라지는지 비교하는 것이 다음 "성능병목개선(before/after)" 후보 — 이번엔 베이스라인만 확정하기로 범위를 좁혔음(사용자 확인).
