# 002-login-rate-limit — 로그인 시도 제한 (브루트포스 방어) (로그)

## Attempt 1 — 2026-08-12 ✅ PASS

- 목적: "실제 사이트처럼" 로그인 고도화 1단계. `POST /api/auth/login`에 시도 횟수·속도 제한이 전혀 없던 상태(`Member` 엔티티에 실패 횟수·잠금 필드 자체가 없음)를 실제로 확인 후 진행.
- 구현: `RateLimitFilter`를 규칙 리스트 구조로 일반화해 로그인 IP 규칙(60초·10회) 추가, `LoginAttemptGuard` 신규(계정 단위 실패 5회·10분 윈도우), `AuthController.login()`에 연결(잠금 확인 → 실패 시 기록 → 성공 시 리셋).
- 단위/통합 테스트: `LoginAttemptGuardTest`(3케이스), `LoginRateLimitFilterTest`(2케이스), `AuthControllerTest`에 통합 시나리오 2케이스 추가 — 전부 실제 Redis 대상(목 아님), MockMvc로 실제 Spring Security 필터 체인·BCrypt 검증까지 거치는 진짜 통합 테스트.

## Attempt 1 (디버깅 — off-by-one 발견·수정) — 2026-08-12

첫 구현에서 `LoginAttemptGuard.isLocked()`가 `count > LIMIT`(초과)로 판정하고 있었는데, "5회 실패하면 잠긴다"는 의도와 맞지 않았다 — 5번째 실패 직후에도 `5 > 5`가 거짓이라 6번째 시도가 그대로 통과해버림. 실제 통합 테스트(`login_repeatedFailures_locksAccount`: 5회 실패 후 6번째 요청이 맞는 비밀번호여도 잠겨야 함)로 이 어긋남을 실제로 잡아냈다. `>=`(도달)로 수정하고 `LoginAttemptGuardTest`의 케이스 이름·루프 횟수도 이 의미에 맞게 정정("임계값 미만" vs "임계값에 도달").

## Attempt 1 (테스트 격리 문제 — 발견·해결) — 2026-08-12

새 로그인 IP 규칙(60초·10회)을 추가한 뒤 기존 `AuthControllerTest`의 일부 테스트(`login_validationFailed`, `logout_success` 등)가 갑자기 429로 실패하기 시작했다. 원인: `AuthControllerTest`의 여러 테스트가 `X-Forwarded-For` 없이 `/api/auth/login`을 호출해서 전부 MockMvc 기본 클라이언트 IP(127.0.0.1)를 공유하는데, 새로 생긴 IP 레이어 임계값(10회)을 그 클래스의 누적 로그인 호출 수가 넘겨버린 것 — team/join 트래픽 제어 때 이미 배웠던 "테스트 간 Redis 키 격리" 교훈을 이번엔 처음에 놓쳤다가 실제 테스트 실패로 다시 확인한 사례. `@BeforeEach`/`@AfterEach`에서 `rate-limit:login:127.0.0.1` 키를 매 테스트 전후로 정리하도록 고쳐서 해결.

## 최종 검증

- `./gradlew clean build` — 148/148(기존 141 + 신규 7) 전부 통과, 회귀 없음. 특히 기존 `RateLimitFilterTest`(team/join, 10초·20회)가 그대로 통과함을 확인 — 규칙 리스트 일반화가 기존 동작을 안 건드렸음을 재확인.
- 디버깅 과정에서 실제로 겪은 것: 반복적으로 로컬 테스트를 재실행하는 동안 임시 Redis 컨테이너에 이전 실행의 잔여 키가 쌓여서 일시적으로 테스트가 실패한 적이 있었음(`FLUSHALL`로 해결) — 이건 코드 문제가 아니라 로컬 반복 디버깅 특유의 상황이고, CI는 매번 새 Redis 서비스 컨테이너로 시작하므로 이 문제가 재현되지 않는다.
- 테스트 데이터·Redis 키 전부 정리 확인, 임시 Redis 컨테이너 정리 완료.
