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
- 컨트롤러는 `@AuthenticationPrincipal MemberUserDetails principal`로 현재 인증 주체를 받아 `MemberResponse.from(principal.getMember())`를 그대로 반환한다. 새 `ErrorCode` 없음.

## 관련 코드 위치

- `controller/AuthController.java` — `me()` 메서드
- 테스트: `src/test/.../controller/AuthControllerTest.java`의 `me_success`/`me_unauthorized`
- 경위: `docs/dev/auth/me/changes/001-me.md`, 실행 로그: `docs/logs/frontend/header-auth/001-header-auth.md`(백엔드+프론트 통합 작업이라 로그는 `frontend/header-auth`에 함께 있음)
