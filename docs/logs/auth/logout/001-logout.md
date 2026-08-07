# 001-logout — 로그아웃 (로그)

## Attempt 1 — 2026-08-07  ✅ PASS
- 시도: `SecurityConfig`의 `/api/auth/**` permitAll 규칙을 `POST /api/auth/signup`, `POST /api/auth/login`로 좁혀, `logout`은 `anyRequest().authenticated()`에 걸리게 함(미인증 시 기존 `ApiAuthenticationEntryPoint`가 401 처리). `AuthController.logout()`을 추가해 `httpRequest.getSession(false)`로 세션을 찾아 `invalidate()`하고 `204 No Content` 반환. `AuthControllerTest`에 로그인 후 로그아웃 시 세션 무효화(`MockHttpSession.isInvalid()`) 확인 테스트와 미인증 로그아웃 401 테스트 추가.
- 결과: `./gradlew test --tests "*AuthControllerTest*"` 9케이스 전부 통과(회원가입 4 + 로그인 4 + 로그아웃 2, 기존 signup/login 테스트 회귀 없음). `./gradlew test` 전체 스위트도 통과 — `SecurityConfig`의 permitAll 축소가 다른 인증 필요 엔드포인트에 영향 없음을 확인.
- 증거(API 샘플, MockMvc):
  - 로그인 상태로 `POST /api/auth/logout` → `204 No Content`, 응답 후 해당 `HttpSession.isInvalid() == true`
  - 미인증 상태로 `POST /api/auth/logout` → `401 {"success":false,"code":"UNAUTHORIZED","message":"로그인이 필요합니다."}`
  - (회귀) `POST /api/auth/signup`, `POST /api/auth/login`은 여전히 인증 없이 호출 가능 — 기존 4+4케이스 그대로 통과
