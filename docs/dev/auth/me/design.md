# 현재 로그인한 사용자 조회 (auth/me) — Design

## 개요

현재 세션이 인증된 상태인지, 인증됐다면 어떤 회원인지를 조회한다. 프론트(헤더 로그인 상태 표시 등)가 페이지 로드 시 "로그인 여부·역할"을 판정하는 용도.

## API / 인터페이스

- `GET /api/auth/me` — 상세: `docs/api/auth.md`.
- 요청 body 없음. 응답은 `signup`/`login`과 동일한 `MemberResponse`(`memberId`/`username`/`name`/`role`), 신규 DTO 없음.

## 데이터 모델

- 추가 테이블 없음.

## 규칙 / 검증

- `SecurityConfig`: `/api/auth/me`를 permitAll 매처에 추가하지 않는다 — 기존 `.anyRequest().authenticated()`에 자연스럽게 걸려, 미인증 요청은 `ApiAuthenticationEntryPoint`가 다른 인증 필요 엔드포인트와 동일한 형식(`{success:false, code:"UNAUTHORIZED", message:...}`, 401)으로 응답한다(`auth/logout`과 동일한 인가 패턴).
- 컨트롤러는 `@AuthenticationPrincipal MemberUserDetails principal`로 현재 인증 주체를 받아 `MemberResponse.from(principal.getMember())`를 반환한다.
- `PATCH /api/auth/me`: 이름만 수정된 경우 새 Principal로 SecurityContext를 갱신해 로그인을 유지하지만, **이메일이 새로 변경된 경우(`emailChanged`)**에는 `emailVerified`가 `false`로 초기화되므로 세션을 즉시 무효화(`session.invalidate()`)하고 로그아웃 처리한다. 프론트엔드는 이를 수신하여 이메일 재인증 안내 및 `/login.html`로 리다이렉트한다.

## 관련 코드 위치

- `controller/AuthController.java` — `me()`, `updateMe()` 메서드
- `static/js/account-info.js` — 마이페이지 정보 수정 처리
- 테스트: `src/test/.../controller/AuthControllerTest.java`의 `updateMe_success_emailChanged` 등
- 경위: `docs/dev/auth/me/changes/001-me.md`, `docs/dev/auth/me/changes/002-email-update-relogin.md`
