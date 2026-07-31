# 001-login — 로그인 (로그)

## Attempt 1 — 2026-07-31  ❌ FAIL
- 시도: `MemberLoginRequest`, `MemberRepository.findByUsername`, `MemberUserDetails`/`MemberDetailsService`(Spring Security 표준 인증), `SecurityConfig` 수정(AuthenticationManager/SecurityContextRepository 빈, `/api/auth/**`만 permitAll), `AuthController.login()` 구현. 테스트에서 로그인 성공 시 `Set-Cookie: JSESSIONID` 쿠키가 응답에 존재하는지로 세션 생성을 검증하도록 작성.
- 결과: `./gradlew test`에서 `login_success` 1개 실패 (`AssertionError: No cookie with name 'JSESSIONID'`). 나머지 7개는 통과.
- 원인: MockMvc는 실제 서블릿 컨테이너가 아니라서, 코드에서 `request.getSession(true)`로 세션을 생성해도 그걸 `Set-Cookie` 응답 헤더로 자동 변환해주지 않음(Mock 환경의 알려진 한계). 즉 로그인/세션 저장 로직 자체는 정상이었고, **테스트의 검증 방식이 잘못됨**.
- 다음: 쿠키 헤더 대신 `MvcResult.getRequest().getSession(false)`로 세션 객체가 실제로 만들어졌는지 직접 확인하는 방식으로 테스트만 수정 (같은 접근으로 고칠 수 있는 실패 → Generate 루프로 재시도, Plan 회귀 불필요)

## Attempt 2 — 2026-07-31  ✅ PASS
- 시도: `login_success` 테스트를 세션 존재 여부(`request.getSession(false) != null`) 검증으로 수정.
- 결과: `./gradlew build` 전체 통과. `AuthControllerTest` 7케이스(회원가입 3 + 로그인 4) 전부 성공.
- 증거(API 샘플, MockMvc):
  - `POST /api/auth/login` (정상, 가입된 회원) → `200 {"success":true,"data":{"memberId":..,"username":"gonguri4","name":"홍길동","role":"BUYER"}}`, 요청에 대해 `HttpSession` 생성 확인됨
  - `POST /api/auth/login` (존재하지 않는 아이디) → `401 {"success":false,"code":"LOGIN_FAILED","message":"아이디 또는 비밀번호가 일치하지 않습니다."}`
  - `POST /api/auth/login` (틀린 비밀번호) → `401` 동일 (아이디 없음과 응답 구분 안 됨 — 계약대로)
  - `POST /api/auth/login` (username 빈 값) → `400 {"success":false,"code":"VALIDATION_FAILED",...}`
- DB 증거: 테스트가 `@Transactional`이라 실행 후 `member` 테이블 row 0건 확인(DB 오염 없음, 회원가입 테스트와 동일한 패턴).
