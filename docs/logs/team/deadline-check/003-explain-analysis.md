# 003-explain-analysis — refund-trigger 스캔 쿼리 EXPLAIN 기반 실행계획 분석 (발제 필수9 성능병목개선)

## 배경

발제 백엔드 필수 9번 "성능 병목 개선"은 "느린 쿼리/병목 지점 식별 + **EXPLAIN 기반 실행계획 분석** + 개선 전후 수치 비교"를 요구한다. 이 프로젝트에서 그동안 "성능병목개선"으로 문서화해온 작업(`docs/logs/team/crud/003-atomic-comparison.md`, 비관적 락 vs 원자적 UPDATE 비교)은 실제로는 **동시성 제어 전략 비교**(필수7)와 **부하테스트**(필수8)의 증거였고, 이 항목이 요구하는 SQL 쿼리 단위의 EXPLAIN 분석은 이 프로젝트에서 한 번도 한 적이 없었다는 걸 뒤늦게 발견해서 이번에 채운다.

## Attempt 1 — 2026-08-11 ✅ PASS

- 대상 쿼리 선정: `refund-trigger` 스케줄러(`TeamDeadlineService.findExpiredTeamIds`)가 1분마다 실행하는 스캔 쿼리 — `SELECT id FROM group_buy_team WHERE status = 'RECRUITING' AND deadline < NOW()`. 이 쿼리는 Plan 단계(`docs/db/group_buy_team.md`)에서 `idx_status_deadline(status, deadline)` 인덱스를 미리 설계해뒀지만, 그 근거가 실제 EXPLAIN 실측이 아니라 순수 논리적 판단이었다 — 이번에 그 판단을 실측으로 검증한다.
- **데이터 준비**: 로컬 DB가 비어있어(테스트 데이터 정리 습관대로 항상 0건) 의미 있는 차이를 보려면 실제 규모의 데이터가 필요했다. 판매자/구매자/상품 각 1건 + `group_buy_team` 20만 건을 재귀 CTE(`WITH RECURSIVE`)로 생성 — status는 `RECRUITING`/`SUCCESS`/`FAILED` 균등 분포(각 66,667건 내외), deadline은 현재 시각 기준 ±10,000분 범위에 균등 분포시켜서, 실제 쿼리 조건(`status='RECRUITING' AND deadline<NOW()`)에 걸리는 행이 33,336건(전체의 약 16.7%) 나오도록 만들었다 — 스케줄러가 매번 "일부만" 걸러내는 실제 운영 패턴과 비슷한 선택도.
- **Before(인덱스 없다고 가정) — `idx_status_deadline`를 임시로 `DROP`한 뒤 측정**:
  - `EXPLAIN`: `Table scan on group_buy_team` — 인덱스를 안 타고 전체 테이블을 순차 스캔.
  - `EXPLAIN ANALYZE`: `rows=200000`(테이블 전체) 스캔 후 필터링해서 `rows=33336` 반환, `actual time=1.31..43.5`(스캔 자체) 총 65ms.
  - 실제 쿼리 3회 반복 실행(`SHOW PROFILES`) 평균 약 **52ms**(48.1/48.7/60.5ms).
- **After(현재 상태, 인덱스 복구) — `idx_status_deadline` 재생성 후 측정**:
  - `EXPLAIN`: `Covering index range scan on group_buy_team using idx_status_deadline` — 인덱스 레인지 스캔으로 전환, 커버링 인덱스라 테이블 접근 자체가 필요 없음.
  - `EXPLAIN ANALYZE`: 정확히 매칭되는 `rows=33336`만 스캔(전체 20만 건이 아니라 실제 결과 건수만큼만), `actual time=0.66..10.4` 총 20.4ms.
  - 실제 쿼리 3회 반복 실행 평균 약 **13ms**(12.7/13.1/12.8ms).
- **결과 비교(Before → After)**:
  | 지표 | 인덱스 없음(Before) | 인덱스 있음(After, 현재 상태) |
  |---|---|---|
  | 스캔 방식 | Table scan(전체 스캔) | Covering index range scan |
  | 스캔한 행 수 | 200,000건(테이블 전체) | 33,336건(매칭 결과만) |
  | 평균 실행 시간(3회) | 약 52ms | 약 13ms |
  | 속도 | 1x | **약 4배 빠름** |

  절대적인 ms 차이(52ms→13ms)는 이 프로젝트 규모에서는 사용자가 체감할 정도는 아니지만, **더 중요한 건 스캔 행 수의 구조적 차이**(200,000건 고정 vs 매칭 건수만) — 테이블이 지금보다 훨씬 커지면(팀 개수가 수백만 건으로 늘면) 인덱스 없는 쪽은 스캔량이 테이블 전체 크기에 비례해서 계속 늘어나지만, 인덱스 있는 쪽은 그 시점의 "마감 지난 RECRUITING 팀" 개수에만 비례한다 — 이게 인덱스를 설계 단계에서 넣어둔 논리적 근거였는데, 이번에 실측으로 직접 확인한 것.
- **정리**: 측정 후 원래 인덱스 상태로 정확히 복구(`SHOW INDEX`로 재확인), 생성했던 합성 데이터(회원 2건, 상품 1건, 공구팀 20만 건) 전부 삭제 확인(`SELECT COUNT(*)`로 0건 확인). `./gradlew test --tests "*TeamDeadline*"` 재실행해서 회귀 없음 확인.

## 결론

`idx_status_deadline(status, deadline)`가 이 스캔 쿼리에 실제로 효과가 있다는 걸 EXPLAIN + 실측 시간으로 증명함 — Plan 단계의 논리적 판단이 사후 검증에서도 맞았음을 확인한 사례. 별도 코드 변경은 없음(이미 존재하는 인덱스의 근거를 사후에 실측으로 보강하는 작업).
