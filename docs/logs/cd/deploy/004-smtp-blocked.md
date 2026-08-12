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

## Attempt 3 — 2026-08-13 ✅ PASS (로컬에서 실제 SendGrid 발송 실측, 스팸 분류 이슈 발견)

- **시도**: SendGrid 계정 가입, Single Sender Verification(`byeongjunmin00@gmail.com`) 완료, API 키 발급 후 로컬 `bootRun`을 실제 `SENDGRID_API_KEY`/`SENDGRID_FROM_EMAIL`로 띄워서 실제 회원가입 → 인증 메일 발송을 시도.
- **1차 시도 실패(원인: Single Sender 인증이 아직 실제로 안 끝나 있었음)** — 사용자가 "아까 눌렀다"고 착각했으나 실제로는 SendGrid의 "Please Verify Your Single Sender" 확인 메일을 그때 안 열어본 상태였다. 콘솔에서 "Sender Verified" 화면을 실제로 확인한 뒤 재발송하니 통과.
- **SendGrid Activity 대시보드로 실제 전달 상태 확인**: `Requests: 3, Delivered: 100%(3), Bounces: 0, Spam Reports: 0` — SendGrid → 수신 메일서버(Gmail) 전달 자체는 100% 성공.
- **그런데 실제 사용자 받은편지함(Primary)엔 안 보임 → Gmail 스팸함에서 발견**: `in:spam` 검색으로 3통 다 스팸함에 들어가 있는 걸 확인. **원인**: 발신 주소(`byeongjunmin00@gmail.com`)가 Gmail 도메인인데 실제 발송은 SendGrid 서버를 거치다 보니, 수신 측(Gmail)이 SPF/DKIM 도메인 인증 불일치로 스푸핑 의심 처리한 것으로 판단(자기 자신에게 보내는 발신자=수신자 패턴이라 더 의심스럽게 보였을 가능성도 있음). 스팸함의 링크를 실제로 클릭해서 인증까지는 정상 완료됨(DB `email_verified=true` 확인) — **발송·수신·인증 자체는 전부 작동**, 다만 "Primary 편지함에 바로 안 뜨고 스팸함까지 확인해야 한다"는 UX 한계가 실제로 확인됨.
- **알려진 한계로 남김(완전한 해결은 스코프 밖)**: 이 문제를 근본적으로 없애려면 SendGrid의 **Domain Authentication**(커스텀 도메인 소유 + SPF/DKIM DNS 레코드 등록)이 필요한데, 이 프로젝트는 Railway 서브도메인만 쓰고 커스텀 도메인이 없어서(구매는 비용이 드는 별도 결정) 이번 스코프에서는 적용하지 않는다 — Single Sender Verification 방식의 알려진 트레이드오프로 문서에 정직하게 남긴다.
- 검증 후 로컬 DB의 테스트 계정, Redis, 로그 파일 전부 정리함.
