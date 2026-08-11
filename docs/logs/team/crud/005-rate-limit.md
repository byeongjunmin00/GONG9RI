# 005-rate-limit — team/join 트래픽 제어 (RateLimitFilter) 실측 검증

## Attempt 1 — 2026-08-11 ✅ PASS

- 목적: 발제 백엔드 도전과제 "트래픽 제어". `004-spike-test.md`로 실측한 `team/join`의 실제 약점(VU 약 3000 부근에서 Tomcat 동시 연결 수용 한계로 무너짐)을 근거로, 같은 클라이언트가 반복 요청하는 훨씬 흔한 시나리오를 애플리케이션 레벨에서 막는 `RateLimitFilter`를 추가하고 실제로 동작하는지 검증.
- 구현: `common/filter/RateLimitFilter`(Redis 고정 윈도우, 윈도우 10초·임계값 20회, `X-Forwarded-For` 우선 클라이언트 식별, fail-open). 상세 설계는 `docs/dev/team/crud/design.md`의 "트래픽 제어" 절.
- 단위 테스트: `common/filter/RateLimitFilterTest`(`@SpringBootTest`+`@AutoConfigureMockMvc`, 실제 Redis 대상) 3케이스 — 임계값 이내 요청은 정상 동작, 21번째부터 429, team/join이 아닌 다른 엔드포인트는 대상이 아님. `./gradlew clean build` 전체 139케이스(기존 136 + 신규 3) 통과, 회귀 없음.

## Attempt 1 (k6 실제 부하테스트 검증) — 2026-08-11 ✅ PASS

로컬 MySQL(기존 `brew services`) + 임시 Docker Redis(`gong9ri-redis-temp3`, 테스트 후 정리) + `bootRun`으로 실제 서버를 띄우고 신규 `k6/team-join-rate-limit-test.js` 실행.

1. **같은 클라이언트 반복 요청 → 21번째부터 실제 429**: `X-Forwarded-For`를 고정한 1개 클라이언트가 `team/join`에 25회 연속 요청 → 1~20번째는 전부 200/409(정상 흐름), 21~25번째는 정확히 429. 서버 로그에 `RateLimitFilter`의 `트래픽 제어 — 요청 거절: clientId=203.0.113.201` 5회, `POST /api/teams/{id}/join -> 429`도 정확히 5회 기록됨.
2. **서로 다른 클라이언트는 서로 영향 없음(오탐 없음)**: `X-Forwarded-For`가 서로 다른 5개 클라이언트가 (1번 clientId가 위 burst로 한창 429를 유발하고 있는 동안) 각자 1회씩 참가 → 전부 200(429 없이 정상 성공). Redis 키가 `clientId`별로 분리돼서 한 클라이언트의 과도한 요청이 다른 클라이언트에 영향을 주지 않음을 실측으로 확인.
3. **k6 스크립트 버그 2건 발견·수정**(스크립트 자체의 결함, `RateLimitFilter` 로직과 무관):
   - `distinctClient` 시나리오에서 `__VU - 1`로 계정 배열 인덱스를 뽑았는데, `__VU`는 `same_client_burst`(VU 1개)와 VU 풀을 공유하는 **전역** 카운터라 시나리오마다 1부터 시작한다는 보장이 없음. 1차 실행에서 인덱스가 배열 범위를 벗어나 `signup` 없는 빈 username으로 로그인 시도 → `username: 공백일 수 없습니다` 검증 실패로 재현.
   - `(__VU - 1) % DISTINCT_CLIENT_COUNT`로 1차 수정했지만 2차 실행에서 두 클라이언트가 같은 인덱스로 겹쳐(같은 계정으로 같은 팀에 두 번 참가 시도) 1건이 `ALREADY_JOINED`(409)로 실패.
   - 최종적으로 `k6/execution` 모듈의 `exec.scenario.iterationInTest`(해당 시나리오 안에서만 0부터 유일하게 매겨지는 카운터)로 교체해서 해결 — 이후 2회 연속 실행 모두 `checks_succeeded 100%`(36/36) 확인.
4. **데이터 정리**: 3회 실행 동안 쌓인 `k6rl%` 접두사 실데이터(회원 32건, 상품 4건 등) FK 순서(참여→팀→가격구간→상품→회원) 지켜서 전부 삭제, `SELECT COUNT(*)`로 0건 확인. `bootRun` 프로세스 종료, 임시 Redis 컨테이너(`gong9ri-redis-temp3`) `docker stop`/`rm`으로 정리.

### 결론

- 임계값(10초당 20회)을 넘긴 클라이언트만 정확히 429를 받고, 정상 범위 내 요청이나 다른 클라이언트는 전혀 영향받지 않음을 실제 서버·실제 Redis로 확인함 — 발제가 요구하는 "임계값 설정 근거, 초과 시 429 응답, 부하테스트로 실제 동작 검증"을 전부 채움.
- 스코프 한계(같은 IP 반복 요청만 방어, 분산 대규모 트래픽은 WAF/CDN 영역이라 스코프 밖)는 `docs/dev/team/crud/design.md`에 정직하게 명시함 — `004-spike-test.md`의 VU 3000 스파이크 자체를 이 필터가 막아준다는 과장된 주장은 하지 않음.
