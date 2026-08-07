# 로그아웃 (auth/logout) — Design

## 개요

로그인된 세션을 서버측에서 무효화한다. 요청 body 없이 세션 쿠키만으로 동작하며, 미인증 상태로 호출하면 다른 인증 필요 엔드포인트와 동일하게 401을 반환한다.

## API / 인터페이스

- `POST /api/auth/logout` — 상세: `docs/api/auth.md`.

## 데이터 모델

- 추가 테이블 없음.

## 규칙 / 검증

- `SecurityConfig`: `/api/auth/**` 전체 permitAll이 아니라 `POST /api/auth/signup`, `POST /api/auth/login`만 permitAll이고, `logout`은 `anyRequest().authenticated()`에 걸린다 — 미인증 요청은 `ApiAuthenticationEntryPoint`가 401(`UNAUTHORIZED`)로 응답해 컨트롤러가 인증 여부를 직접 분기하지 않는다.
- 인증된 요청만 `AuthController.logout()`에 도달하며, `HttpServletRequest.getSession(false)`로 세션을 찾아 `invalidate()`한 뒤 `204 No Content`를 반환한다.
- 새 `ErrorCode` 없음 — 기존 `UNAUTHORIZED` 재사용.

## 관련 코드 위치

- `config/SecurityConfig.java` — 인가 규칙(permitAll 범위 축소)
- `controller/AuthController.java` — `logout()` 메서드
- 테스트: `src/test/.../controller/AuthControllerTest.java` (signup/login 테스트와 같은 클래스에 이어서 작성)
