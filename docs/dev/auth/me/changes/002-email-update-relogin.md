# 이메일 수정 시 자동 로그아웃 및 재인증 안내 (email-update-relogin) — Change Record

## 1. 개요 및 목적

- **문제점**: 현 구조에서는 이메일 미인증 상태 시 로그인 시도(`POST /api/auth/login`)를 `EMAIL_NOT_VERIFIED`(403)로 차단하지만, 이미 로그인한 회원이 마이페이지에서 이메일을 변경하는 경우 세션이 그대로 유지되어 미인증 상태로 서비스를 계속 이용할 수 있는 보안 정책 우회 경로가 존재했음.
- **목적**: 이메일이 변경된 경우 현재 세션을 즉시 무효화(로그아웃)하고, 프론트엔드에서 인증 메일 확인 및 재로그인을 안내하는 흐름으로 전환하여 서비스 인증 정책의 정합성을 확보함.

## 2. 변경 내용

### 백엔드 (Controller / Security)
- **`com.gong9ri.gong9ri.controller.AuthController` (`updateMe`)**:
  - 기존 이메일과 변경 요청 이메일을 비교 (`emailChanged`).
  - `emailChanged == true` 일 때:
    - 세션 무효화 (`httpRequest.getSession(false).invalidate()`) 및 `SecurityContextHolder.clearContext()` 수행.
    - SecurityContext를 갱신 저장하지 않고, `emailVerified = false`가 담긴 `MemberResponse` 응답 반환.
  - `emailChanged == false` 일 때 (이름만 변경):
    - 기존과 동일하게 SecurityContext를 갱신하여 세션 로그인 유지.

### 프론트엔드 (Static Script)
- **`src/main/resources/static/js/account-info.js`**:
  - `PATCH /api/auth/me` 성공 핸들러 처리:
    - `member.emailVerified === false` 인 경우:
      - *"이메일이 변경되어 재인증이 필요합니다. 새 주소로 발송된 인증 메일을 확인하신 후 다시 로그인해주세요."* 안내 메시지 노출.
      - 안내 후 로그인 페이지(`/login.html`)로 이동 (`window.location.href = '/login.html'`).

## 3. 평가 결과 및 검증

- `./gradlew test` 전체 통과.
- `AuthControllerTest`:
  1. `updateMe_success_emailChanged`: 이메일 변경 정보수정 호출 시 `emailVerified: false` 반환 및 기존 세션으로 재조회 시 401(UNAUTHORIZED) 반환 검증 완료.
  2. `updateMe_success_nameOnly`: 이름만 변경 정보수정 호출 시 기존처럼 세션 갱신 및 로그인 유지 검증 완료.
