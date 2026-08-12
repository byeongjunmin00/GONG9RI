# 004-smtp-blocked — Railway 아웃바운드 SMTP 차단 발견 + SendGrid 전환 (로그)

## Attempt 1 — 2026-08-12 (실제 장애 대응 중 발견)

- **배경**: 로그인 고도화 2단계(이메일 인증/비밀번호 재설정)를 로컬(`bootRun`)에서는 실제 Gmail SMTP로 발송·수신·클릭까지 전부 검증했다(`docs/dev/auth/email-verification/design.md`). 하지만 그건 전부 **로컬 컴퓨터 네트워크**에서 나간 연결이었고, 프로덕션(Railway)에서 실제로 발송을 시도한 건 이번이 처음이었다.
- **증상**: 프로덕션에서 `POST /api/auth/verify-email/resend`를 여러 번 호출했지만 실제 이메일이 도착하지 않음(팀원도 같은 증상 보고).
- **실측 확인**: Railway Deploy Logs를 `이메일`로 검색해서 실제 원인을 찾음.
  ```
  WARN c.g.gong9ri.common.mail.EmailService : 이메일 발송 실패: to=..., subject=[GONG9RI] 이메일 인증을 완료해주세요,
  error=Mail server connection failed. Failed messages: org.eclipse.angus.mail.util.MailConnectException:
  Couldn't connect to host, port: smtp.gmail.com, 587; timeout -1;
  ```
  인증(아이디/비밀번호) 단계까지 가지도 못하고 **TCP 연결 자체가 거부**됨 — Railway 컨테이너에서 나가는 아웃바운드 SMTP(587번 포트) 자체가 막혀있는 것으로 판단(많은 PaaS가 스팸 방지 목적으로 아웃바운드 SMTP를 기본 차단하는 것과 일치하는 증상). Railway 측 정책 문서로 100% 확증하지는 못했지만(정직하게 남김), 동일 자격증명·동일 코드가 로컬에서는 되고 프로덕션(Railway)에서만 "연결 자체가 거부"되는 것으로 봐서 네트워크 레벨 차단이 가장 유력한 설명이다.
- **결론**: `EmailService`가 자격증명 문제가 아니라 애초에 **SMTP 직접 연결이라는 방식 자체**가 이 호스팅 환경에서 성립하지 않는다. 자격증명을 다시 확인하거나 바꾸는 걸로는 해결 안 됨.

## Attempt 2 — 2026-08-12 ✅ PASS (SendGrid HTTP API로 전환)

- **조치**: SMTP(포트 587, 막힘) 대신 HTTPS(포트 443, 거의 모든 클라우드에서 허용)로 통신하는 SendGrid REST API(`POST https://api.sendgrid.com/v3/mail/send`)로 전환.
  - `common/mail/EmailSender.java`(신규 인터페이스) + `SendGridEmailSender.java`(신규 구현, `RestClient`로 직접 호출 — `PortOneApiClient`와 같은 판단으로 별도 SDK 의존성 추가 안 함).
  - `EmailService`가 `JavaMailSender` 대신 `EmailSender`에 의존하도록 변경 — `@Async`/장애격리(발송 실패를 삼키고 warn 로그만 남김) 등 나머지 설계는 그대로 유지.
  - `spring-boot-starter-mail` 의존성 제거(`build.gradle`), `spring.mail.*`/`management.health.mail.enabled` 설정 전부 제거(더 이상 필요 없음 — `MailHealthIndicator` 자체가 클래스패스에 없으므로 해당 문제도 같이 사라짐).
  - 신규 설정: `sendgrid.api-key`(`SENDGRID_API_KEY`), `sendgrid.from-email`(`SENDGRID_FROM_EMAIL`) — 발신 주소는 SendGrid 콘솔의 **Single Sender Verification**으로 인증한 주소여야 한다(이 프로젝트는 커스텀 도메인이 없어 도메인 전체 인증 대신 이 방식을 씀).
  - 테스트: `AuthControllerTest`의 `@MockitoBean JavaMailSender` → `@MockitoBean EmailSender`로 교체(인터페이스를 목으로 대체하는 `PortOneClient`와 동일 패턴). `./gradlew test` 190개 전체 재통과(회귀 없음).
- **로컬 검증**: 수정된 `Dockerfile` 기반 이미지를 `docker run --memory=1g`로 실제 기동 — 정상 부팅, `/actuator/health` 200. SendGrid API 키를 아직 실제 값으로 안 넣은 상태라 실제 발송 성공까지는 이 Attempt에서 검증 못함(다음 단계).
- **미완료(다음 단계)**: SendGrid 계정 가입 + Single Sender Verification(`byeongjunmin00@gmail.com`) + API 키 발급 → Railway 환경변수(`SENDGRID_API_KEY`, `SENDGRID_FROM_EMAIL`) 설정 → 프로덕션 재배포 → 실제 이메일 발송·수신까지 실측 필요.
