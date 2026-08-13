# 이메일 인증 (로그인 고도화 2단계) — Design

## 개요

로그인 고도화 로드맵의 2단계(1단계는 `docs/dev/auth/login/design.md`의 "로그인 시도 제한" 참고). 지금까지는 가입만 하면 바로 로그인이 됐다 — 이 기능부터는 회원가입 후 이메일 인증 링크를 클릭해야 로그인이 가능해진다. 비밀번호 재설정(`docs/dev/auth/password-reset/design.md`)과 "이메일 발송 인프라(`EmailService`)"·"1회성 토큰 인프라(`TokenService`)"를 공유해서 같이 진행했다.

## API / 인터페이스

- `GET /api/auth/verify-email?token=` — 이메일 안의 링크를 브라우저로 직접 클릭해서 들어오는 요청이라 JSON이 아니라 안내 HTML을 직접 응답한다.
- `POST /api/auth/verify-email/resend` — 인증 메일 재발송. 상세: `docs/api/auth.md`.

## 데이터 모델

- `member.email_verified` (BOOLEAN, NOT NULL, DEFAULT false) 추가. `member.email`에 UNIQUE 제약 추가. 상세: `docs/db/member.md`.

## 공용 인프라 (비밀번호 재설정과 공유)

### EmailService (`common/mail/EmailService.java`, 신규)

실제 발송 트랜스포트를 감싸는 얇은 컴포넌트. 이메일이 인증/재설정 2종뿐이라 Thymeleaf 같은 템플릿 엔진 없이 인라인 문자열로 처리한다. `sendVerificationEmail`/`sendPasswordResetEmail` 둘 다 `@Async`(기존 `AsyncConfig`의 기본 executor 재사용) — 메일 발송이 느리거나 실패해도 회원가입 트랜잭션·재설정 요청 응답을 막으면 안 된다는 원칙(AI 기능들의 장애격리 원칙과 동일). 발송 실패는 `warn` 로그만 남기고 삼킨다.

**발송 트랜스포트 교체(2026-08-12)**: 처음엔 `JavaMailSender`(Gmail SMTP)로 구현했으나, Railway가 아웃바운드 SMTP(587번 포트)를 막고 있어 프로덕션에서 발송이 전혀 안 되는 걸 실측으로 확인(`docs/logs/cd/deploy/004-smtp-blocked.md`) — HTTP(443) 기반 SendGrid REST API로 교체했다. `EmailSender` 인터페이스(신규) + `SendGridEmailSender`(신규, `RestClient`로 직접 호출, SDK 의존성 추가 없음 — `PortOneApiClient`와 같은 판단)로 분리했고, `EmailService`는 이제 `EmailSender`에 의존한다. `spring-boot-starter-mail` 의존성과 `spring.mail.*`/`management.health.mail.enabled` 설정은 전부 제거됨(아래 서술 중 `JavaMailSender`/Gmail SMTP를 언급하는 부분은 이 교체 이전 시점의 기록).

### TokenService (`common/security/TokenService.java`, 신규)

이메일 인증 토큰과 비밀번호 재설정 토큰을 둘 다 처리하는 공용 컴포넌트 — 같은 메커니즘(랜덤 토큰 발급, Redis TTL로 만료, 1회 사용 후 즉시 삭제)을 두 번째로 실제 쓰는 시점이라 `RateLimitFilter`를 규칙 리스트로 일반화했을 때와 같은 판단으로, 거의 동일한 클래스 두 개 대신 이거 하나로 처리했다.

- 키 형식 `{prefix}:{token}` → 값 `memberId`. `prefix`로 용도 구분(`email-verify`, `password-reset`).
- `issue(prefix, memberId, ttl)`: `UUID.randomUUID()`(내부적으로 `SecureRandom` 기반이라 토큰 생성에 그대로 사용해도 안전) 기반 토큰 발급.
- `resolveAndConsume(prefix, token)`: 조회 후 즉시 삭제(1회성 보장). 만료/이미 사용됨/존재 안 함을 전부 빈 `Optional`로 통일해서 클라이언트가 그 차이를 구분 못 하게 한다(계정 존재 여부 등 정보 노출 방지 원칙과 동일).
- **fail-closed**(다른 Redis 기반 컴포넌트인 `RateLimitFilter`/`LoginAttemptGuard`와 반대): Redis 장애 시 토큰의 진위를 판단할 근거 자체가 없어지므로, "장애 시 통과"를 허용하면 아무 문자열이나 유효한 토큰으로 받아들이는 보안 구멍이 된다. 그래서 여기만 예외적으로 장애 시 무효 토큰 취급한다.

## 규칙 / 검증

- `MemberService.signup()`: 저장 후 `MemberSignedUpEvent(memberId, email)` 발행. `Member`는 생성자에서 `emailVerified = false`로 시작.
- `event/MemberSignedUpEvent.java` + `MemberSignedUpEventListener.java`(신규) — 기존 `TeamRefundedEvent`/`TeamCapacityChangedEvent`와 동일한 `@TransactionalEventListener(phase = AFTER_COMMIT)` 패턴. 회원가입 트랜잭션이 실제로 커밋된 뒤에만 메일을 보낸다(롤백된 가입에 메일 보내면 안 됨). 리스너가 `TokenService.issue("email-verify", memberId, Duration.ofHours(24))` 후 `EmailService.sendVerificationEmail(...)` 호출.
  - **알려진 한계**: `@Transactional` 테스트(rollback 기반)에서는 AFTER_COMMIT 리스너가 아예 발동하지 않는다(`TeamCapacityBroadcastTest`에서 이미 겪은 것과 같은 제약) — `AuthControllerTest`의 이메일 인증 관련 테스트는 이 이벤트 체인을 거치지 않고 `TokenService.issue()`를 직접 호출해서 토큰을 만들어 검증한다.
- `GET /verify-email?token=`: `tokenService.resolveAndConsume("email-verify", token)` 성공 시 `memberService.verifyEmail(memberId)` 호출 후 성공 HTML, 실패(무효/만료/이미 사용) 시 400 + 실패 HTML. 별도 정적 페이지 없이 컨트롤러가 직접 HTML 문자열을 응답(한 번 보고 마는 랜딩이라 정적 파일까지는 안 만듦).
- `POST /verify-email/resend`: 계정이 없거나 이미 인증된 경우를 포함해서 **항상 같은 성공 응답**을 반환한다(계정 존재 여부·인증 상태 비노출 — `LoginAttemptGuard`/`LOGIN_FAILED` 통일 응답과 같은 원칙). 실제로 존재하고 미인증인 경우에만 새 토큰 발급 + 메일 발송.
- **로그인 차단**: `AuthController.login()`에서 비밀번호 인증까지 성공한 뒤(`loginAttemptGuard.recordSuccess()` 호출 이후 — 비밀번호는 맞았으므로), `candidate.getMember().isEmailVerified()`가 false면 세션을 만들지 않고 `BusinessException(EMAIL_NOT_VERIFIED)`(403)를 던진다. 프론트(`login.js`)는 이 에러 코드일 때만 "인증 메일 다시 보내기" 버튼을 노출한다.
- **레이트리밋**: `RateLimitFilter`에 `POST /api/auth/verify-email/resend`(5분·3회, 키 접두사 `verify-email-resend`) 규칙 추가 — 이메일 폭탄 방지. 임계값은 **실측 근거 없는 초기값**이다.
- 신규 `ErrorCode`: `DUPLICATE_EMAIL`(409, 비밀번호 재설정과 공유하는 email unique 전제조건), `EMAIL_NOT_VERIFIED`(403), `INVALID_OR_EXPIRED_TOKEN`(400, 비밀번호 재설정과 공유).

## 필요한 운영 환경변수

- `SENDGRID_API_KEY`/`SENDGRID_FROM_EMAIL`(SendGrid, 2026-08-12부터 — 이전엔 `MAIL_USERNAME`/`MAIL_PASSWORD`로 Gmail SMTP를 썼으나 Railway가 SMTP를 막아 교체함), `APP_BASE_URL`(이메일 링크용 서버 공개 URL) — Railway 설정 방법은 `docs/deploy-guide.md` 참고.

## 알려진 문제 이력 — 프로덕션에서 인증 메일이 발송되지 않던 문제 (2026-08-13, 해결됨)

**Railway는 Free/Trial/Hobby 플랜에서 아웃바운드 SMTP 포트(25/465/587/2525)를 전부 차단**한다(Pro 플랜 이상에서만 허용, Railway 공식 문서·커뮤니티로 확인). 로컬에서는 Attempt 3(`docs/logs/auth/email-verification/001-mail-infra-and-migration.md`)에서 실제 발송까지 검증됐지만, 프로덕션에서는 `MailConnectException: Couldn't connect to host, port: smtp.gmail.com, 587`으로 조용히 실패해서 회원가입 후 로그인이 사실상 막히는 문제가 있었다 — **이 문서 위쪽에 이미 서술된 SendGrid 전환(2026-08-12)이 바로 이 문제의 최종 해결책이다.** 팀원이 같은 문제를 독자적으로 진단해서(Railway Log Explorer로 원인 확정, `docs/logs/auth/email-verification/001-mail-infra-and-migration.md` Attempt 4) 같은 시간대에 별도로 기록을 남겼는데, 그 시점엔 이 SendGrid 전환 작업이 아직 병합되기 전이라 그 문서엔 "미해결"로 남아있다 — 지금은 해결된 상태이므로 그 문서를 참고할 땐 이 설명을 최신 상태로 볼 것.

## 실측 검증 (2026-08-12)

- 로컬 dev DB에 `member.email_verified` 컬럼 + `email` UNIQUE 인덱스를 실제로 마이그레이션 적용해서 기존 row 있는 상태에서 안전한지 실측 확인함. 상세 트러블슈팅: `docs/logs/auth/email-verification/001-migration-and-mail-health.md`.
- `AuthControllerTest`에 회귀 포함 전체 158개 테스트가 실제로 로컬 MySQL/Redis에 붙어서 통과하는 것까지 확인함(`JavaMailSender`는 `@MockitoBean`으로 대체해서 실제 SMTP 연결 없이 검증).
- **실제 Gmail SMTP로 발송 검증 완료(2026-08-12)**: 실제 발신 계정(Gmail 앱 비밀번호)으로 로컬 서버를 띄워 실제 회원가입 → 실제 이메일 수신(받은편지함에서 직접 확인) → 인증 전 로그인 시도가 `EMAIL_NOT_VERIFIED`(403)로 실제로 막히는 것 → 메일의 실제 링크 클릭 → 인증 완료 → 같은 계정으로 로그인 성공(200)까지 전체 플로우를 실제로 완주함. 검증 후 실제 DB의 테스트 계정과 Redis 키 전부 정리함.

## 관련 코드 위치

- `entity/Member.java` — `emailVerified` 필드, `verifyEmail()` 도메인 메서드
- `repository/MemberRepository.java` — `existsByEmail`, `findByEmail` 추가
- `common/mail/EmailService.java`, `common/security/TokenService.java` — 신규(비밀번호 재설정과 공유)
- `common/filter/RateLimitFilter.java` — `verify-email-resend` 규칙 추가
- `common/exception/ErrorCode.java` — `DUPLICATE_EMAIL`, `EMAIL_NOT_VERIFIED`, `INVALID_OR_EXPIRED_TOKEN` 추가
- `event/{MemberSignedUpEvent,MemberSignedUpEventListener}.java` — 신규
- `service/MemberService.java` — 이메일 중복 체크, 이벤트 발행, `verifyEmail()`
- `controller/AuthController.java` — `verifyEmail()`, `resendVerificationEmail()`, `login()`에 인증 확인 추가
- `dto/EmailVerificationResendRequest.java` — 신규
- `config/SecurityConfig.java` — `GET /verify-email`, `POST /verify-email/resend` permitAll
- `static/login.html`/`js/login.js` — 미인증 안내 + 재발송 버튼
- 테스트: `src/test/.../controller/AuthControllerTest.java`(signup_duplicateEmail, login_beforeEmailVerification_isBlocked, verifyEmail_success/invalidToken/tokenIsSingleUse, resendVerificationEmail_alwaysReturnsGenericSuccess)
