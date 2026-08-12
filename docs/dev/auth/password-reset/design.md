# 비밀번호 찾기/재설정 (로그인 고도화 2단계) — Design

## 개요

로그인 고도화 로드맵의 2단계 중 하나. 지금까지는 비밀번호를 잊으면 계정을 되찾을 방법이 전혀 없었다 — 이메일로 재설정 링크를 받아 새 비밀번호로 바꾸는 흐름을 추가한다. 이메일 인증(`docs/dev/auth/email-verification/design.md`)과 "이메일 발송 인프라(`EmailService`)"·"1회성 토큰 인프라(`TokenService`)"를 공유한다 — 공용 인프라 설명은 그쪽 문서 참고, 여기서는 비밀번호 재설정 고유 로직만 다룬다.

## API / 인터페이스

- `POST /api/auth/password/reset-request` — 재설정 이메일 요청
- `POST /api/auth/password/reset` — 토큰으로 실제 비밀번호 변경
- 상세: `docs/api/auth.md`

## 데이터 모델

- 추가 테이블 없음. `member` 재사용(`docs/db/member.md`). 재설정 흐름은 "이메일로 계정을 유일하게 찾을 수 있어야" 성립하므로, 이메일 인증 작업에서 같이 처리한 `member.email` UNIQUE 제약이 이 기능의 전제조건이다.

## 규칙 / 검증

- `Member.changePassword(String encodedPassword)` 도메인 메서드 추가(setter 대신 — `Payment.refund()`와 같은 스타일).
- `POST /password/reset-request`: `MemberRepository.findByEmail`로 조회, **존재 여부와 무관하게 항상 동일한 성공 응답**을 반환한다(계정 존재 여부 비노출 — 이메일 인증 재발송과 같은 원칙). 존재하면 `TokenService.issue("password-reset", memberId, Duration.ofMinutes(30))` 후 `EmailService.sendPasswordResetEmail(...)`. DB 트랜잭션이 없는 순수 조회 + Redis 쓰기라 `AFTER_COMMIT` 이벤트 없이 컨트롤러에서 바로 `@Async` 메서드를 호출한다(이메일 인증의 회원가입 흐름과 다른 점 — 거긴 가입 트랜잭션 커밋을 기다려야 함).
- `POST /password/reset`: `TokenService.resolveAndConsume("password-reset", token)`으로 memberId 확인(실패 시 `INVALID_OR_EXPIRED_TOKEN`, 400), 성공 시 `memberService.changePassword(memberId, newPassword)`(내부적으로 BCrypt 인코딩 후 저장).
- **레이트리밋**: `RateLimitFilter`에 `POST /api/auth/password/reset-request`(5분·3회, 키 접두사 `password-reset-request`) 규칙 추가 — 이메일 폭탄 방지. 임계값은 **실측 근거 없는 초기값**이다.
- 신규 `ErrorCode.INVALID_OR_EXPIRED_TOKEN`(400) — 이메일 인증 링크 실패와 공유.

## 알려진 한계 (구현 안 함, 정직하게 남김)

- **재설정 후 기존 로그인 세션을 강제 무효화하지 않는다.** 비밀번호를 바꿔도 이미 로그인된 다른 세션(다른 기기·브라우저 등)은 그대로 유지된다. Spring Security `SessionRegistry`(동시 세션 제어) 도입이 필요한 별개 작업이라 이번 스코프 밖 — 계정 탈취 대응 시나리오에서는 원래 이 무효화까지 해야 완전하다는 걸 인지하고 있다.

## 필요한 운영 환경변수

- `MAIL_USERNAME`/`MAIL_PASSWORD`(Gmail SMTP), `APP_BASE_URL` — 이메일 인증과 공유, `docs/deploy-guide.md` 참고.

## 실측 검증 (2026-08-12)

- 로컬에서 `TokenService.issue()`로 실제 Redis에 토큰을 만들어 `POST /password/reset` 전체 흐름(토큰 소진 → 비밀번호 변경 → 이전 비밀번호로 로그인 실패 → 새 비밀번호로 로그인 성공)을 실제 curl + 이후 `AuthControllerTest`로 검증함.
- 토큰 1회성(재사용 시 `INVALID_OR_EXPIRED_TOKEN`), 이메일 존재 여부 비노출(존재/비존재 이메일 모두 동일 응답)을 각각 실제 요청으로 확인함.
- 실제 Gmail SMTP로 진짜 재설정 이메일을 발송해서 링크를 눌러보는 검증은 아직 안 함(발신용 Gmail 앱 비밀번호 준비 후 진행 예정).

## 관련 코드 위치

- `entity/Member.java` — `changePassword()` 도메인 메서드
- `common/mail/EmailService.java`, `common/security/TokenService.java` — 이메일 인증과 공유
- `common/filter/RateLimitFilter.java` — `password-reset-request` 규칙 추가
- `common/exception/ErrorCode.java` — `INVALID_OR_EXPIRED_TOKEN`(이메일 인증과 공유)
- `service/MemberService.java` — `changePassword()`
- `controller/AuthController.java` — `requestPasswordReset()`, `resetPassword()`
- `dto/{PasswordResetRequestDto,PasswordResetConfirmRequest}.java` — 신규
- `config/SecurityConfig.java` — `POST /password/reset-request`, `POST /password/reset` permitAll
- `static/{forgot-password.html,reset-password.html}`, `js/{forgot-password.js,reset-password.js}` — 신규, `login.html`에 "비밀번호를 잊으셨나요?" 링크 추가
- 테스트: `src/test/.../controller/AuthControllerTest.java`(requestPasswordReset_alwaysReturnsGenericSuccess, resetPassword_success/invalidToken/tokenIsSingleUse)
