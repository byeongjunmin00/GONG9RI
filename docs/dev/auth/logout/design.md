# 로그아웃 (auth/logout) — Design

## 개요

로그인된 세션을 서버측에서 무효화한다. 요청 body 없이 세션 쿠키만으로 동작하며, 미인증 상태로 호출하면 다른 인증 필요 엔드포인트와 동일하게 401을 반환한다.

## API / 인터페이스

- `POST /api/auth/logout` — 상세: `docs/api/auth.md`.

## 데이터 모델

- 추가 테이블 없음.

## 규칙 / 검증

- `SecurityConfig`: `/api/auth/**` 전체 permitAll이 아니라 `POST /api/auth/signup`, `POST /api/auth/login`만 permitAll이고, `logout`은 `anyRequest().authenticated()`에 걸린다 — 미인증 요청은 `ApiAuthenticationEntryPoint`가 401(`UNAUTHORIZED`)로 응답해 컨트롤러가 인증 여부를 직접 분기하지 않는다.
- 인증된 요청만 `AuthController.logout()`에 도달하며, 세션·인증 정보를 다음 세 가지로 확실히 정리한 뒤 `204 No Content`를 반환한다(2026-08-14 강화 — 로그아웃 후에도 인증이 남아있는 것처럼 보인다는 실사용 버그 리포트로 발견. 세션 무효화만으로는 (1) 현재 요청 스레드의 `SecurityContextHolder`에 남은 인증 정보, (2) 브라우저가 여전히 들고 있는 세션 쿠키가 정리되지 않았음):
  1. `HttpServletRequest.getSession(false)`로 세션을 찾아 `invalidate()`
  2. `SecurityContextHolder.clearContext()`
  3. `JSESSIONID` 쿠키를 `path=/`, `httpOnly=true`, `maxAge=0`으로 재발급해 브라우저가 즉시 만료시키도록 함
- 표준 Spring Security `.logout(...)` DSL(`LogoutFilter`)은 도입하지 않았다 — 이 프로젝트는 로그인/카카오콜백 전부 `AuthenticationManager`/`SecurityContextRepository`를 컨트롤러가 직접 호출하는 수동 구현 스타일이고(`docs/dev/auth/social-login/design.md`), `.logout()` DSL을 추가하면 `LogoutFilter`가 `/api/auth/logout`을 가로채 JSON 204 대신 302 리다이렉트를 반환하게 돼(별도 `logoutSuccessHandler` 커스터마이징 필요) 기존 `logout_success`(204 기대) 계약과 충돌한다. 컨트롤러가 정리 단계를 직접 수행하는 현재 스타일을 유지.
- 새 `ErrorCode` 없음 — 기존 `UNAUTHORIZED` 재사용.

### bfcache(브라우저 뒤로가기 캐시) 대응 (2026-08-14 추가)

로그아웃 후 브라우저 뒤로가기를 누르면 bfcache에 저장된 로그인 상태의 이전 페이지(개인 데이터 포함)가 그대로 보일 수 있다는 가능성도 위 실사용 버그 조사 중 확인됐다. `common/filter/AuthPageCacheControlFilter`(신규, `OncePerRequestFilter`, `@Component`로 서블릿 컨테이너에 자동 등록 — `SecurityConfig`에 별도 등록 불필요)가 로그인이 필요한 정적 페이지(`/buyer/mypage.html`, `/seller/mypage.html`, `/seller/products/new.html`, `/seller/products/edit.html`, `/checkout.html`)에만 `Cache-Control: no-store`를 붙여 해당 페이지가 bfcache 대상에서 제외되도록 한다. 이 프로젝트는 정적 HTML을 `SecurityConfig`에서 permitAll로 열어두고 클라이언트 JS(`header-auth.js` 등)가 401 응답으로 로그인 여부를 판별하는 구조라, 서버가 인증을 강제하는 대신 캐시 헤더만 얹는 방식을 택함(전체 `*.html`이 아니라 실제로 개인 데이터를 보여주는 페이지로 범위 한정). 자동 테스트는 없고(수동 확인 대상), 정적 리소스 서빙 방식에 따라 적용 범위가 제한될 수 있는 알려진 한계로 남긴다.

## 관련 코드 위치

- `config/SecurityConfig.java` — 인가 규칙(permitAll 범위 축소)
- `controller/AuthController.java` — `logout()` 메서드(세션 무효화 + `SecurityContextHolder.clearContext()` + 쿠키 만료)
- `common/filter/AuthPageCacheControlFilter.java`(2026-08-14 추가) — 인증 필요 정적 페이지 `Cache-Control: no-store`
- 테스트: `src/test/.../controller/AuthControllerTest.java` (signup/login 테스트와 같은 클래스에 이어서 작성. `logout_thenReusingSameSession_isUnauthorized`(2026-08-14 추가) — 로그아웃 후 같은 세션으로 재호출 시 401 확인)
- 이번 강화 작업의 상세 시도/증거는 `docs/logs/auth/social-login/001-kakao-login-session-fix.md`, 채번 문서는 `docs/dev/auth/social-login/changes/001-kakao-login-session-fix.md`에 있음(카카오 role 불일치 안내와 같은 작업으로 함께 처리됨)
