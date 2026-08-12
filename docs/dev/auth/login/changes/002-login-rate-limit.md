# 로그인 시도 제한 (auth/login)

대상: auth/login
담당: 민병준

## 배경 / 요구

"실제 사이트처럼" 로그인을 고도화하는 로드맵의 1단계. `POST /api/auth/login`은 지금까지 시도 횟수·속도 제한이 전혀 없었다 — `AuthenticationManager.authenticate()`를 무제한으로 시도할 수 있는 상태. 외부 의존성이 없고, 이미 검증된 `RateLimitFilter`(team/join용) 패턴을 그대로 확장할 수 있어서 로그인 고도화 항목 중 제일 먼저 진행한다.

## 설계

`docs/dev/auth/login/design.md`의 "로그인 시도 제한" 절 참고. 두 레이어(IP 단위 `RateLimitFilter` 규칙 추가 + 계정 단위 `LoginAttemptGuard` 신규)로 구성.

## 태스크

- [x] `RateLimitFilter`를 규칙 리스트(`RateLimitRule`) 구조로 일반화, 로그인 규칙(60초·10회) 추가
- [x] `ErrorCode.LOGIN_ATTEMPTS_EXCEEDED` 추가
- [x] `common/security/LoginAttemptGuard` 신규(isLocked/recordFailure/recordSuccess)
- [x] `AuthController.login()`에 잠금 확인/실패기록/성공시리셋 연결
- [x] 테스트: `LoginAttemptGuardTest`(신규), `LoginRateLimitFilterTest`(신규), `AuthControllerTest` 통합 시나리오 2케이스 추가
- [x] 기존 `RateLimitFilterTest`(team/join) 회귀 없음 확인
- [x] `docs/dev/auth/login/design.md`, `docs/api/auth.md` 갱신

## 평가(통과) 기준

- `./gradlew build` 전체 회귀 없음(실제 Redis 대상)
- 같은 계정 5회 연속 실패 → 6번째부터 맞는 비밀번호를 넣어도 `LOGIN_ATTEMPTS_EXCEEDED`(429)
- 로그인 성공 시 실패 카운터가 리셋됨을 실제 확인
- 같은 클라이언트(IP) 10회 초과 시 `TOO_MANY_REQUESTS`(429), team/join 쪽 임계값(10초·20회)은 그대로 유지
