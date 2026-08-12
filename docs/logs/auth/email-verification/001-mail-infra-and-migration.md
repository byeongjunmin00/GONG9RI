# 001-mail-infra-and-migration — 이메일 인증 + 비밀번호 재설정 인프라 구축 (로그)

## Attempt 1 — 2026-08-12 ✅ PASS

- 목적: "실제 사이트처럼" 로그인 고도화 2단계. `spring-boot-starter-mail` 추가, `EmailService`/`TokenService` 신규 구현, `Member.emailVerified` 필드 + `email` UNIQUE 제약 추가, 회원가입 이벤트(`MemberSignedUpEvent`) → 인증 메일 발송, 비밀번호 재설정 요청/확정 엔드포인트 4개 신규.
- 설계: `docs/dev/auth/email-verification/design.md`, `docs/dev/auth/password-reset/design.md` 참고.

## Attempt 1 (디버깅 — MailHealthIndicator가 배포 게이팅을 깨뜨림) — 2026-08-12

`spring-boot-starter-mail` 추가 직후 로컬 `bootRun`으로 실제 기동해보니 `/actuator/health`가 계속 `503 DOWN`이었다 — 로그에 `jakarta.mail.AuthenticationFailedException: failed to connect, no password specified?`가 헬스체크 폴링마다 반복 출력됨. 원인: Actuator가 `spring-boot-starter-mail`을 감지하면 `MailHealthIndicator`를 자동 등록해서 헬스체크 때마다 실제 SMTP `testConnection()`을 시도하는데, `MAIL_USERNAME`/`MAIL_PASSWORD`가 비어있는 로컬/기본 상태에선 당연히 실패함.

**심각도가 높았던 이유**: 이날 앞서 겪은 502 사고(`docs/logs/ai/policy-rag/002-boot-decoupling.md`)를 계기로 방금 막 만들어둔 Railway 헬스체크 게이팅(`railway.json`의 `deploy.healthcheckPath: /actuator/health`)이 이 문제 때문에 **영원히 통과 못 하는 상태**가 될 뻔했다 — Railway에 `MAIL_USERNAME`/`MAIL_PASSWORD`를 미리 설정해두지 않으면 새 배포가 전부 헬스체크에서 막혀버리는 구조였음.

**해결**: `application.yaml`에 `management.health.mail.enabled: false` 추가 — 메일 발송 실패는 이미 앱 전체에 영향 안 주게 설계돼 있으므로(`EmailService.send()`가 예외를 삼키고 `warn` 로그만 남김), 헬스체크도 같은 원칙으로 메일 상태를 안 보게 함(DB/Redis만 배포를 실제로 막아야 함). 두 번째 `bootRun` + `curl /actuator/health`로 `200 UP` 확인.

## Attempt 1 (디버깅 — ddl-auto가 기존 컬럼에 UNIQUE를 안 걸어줌) — 2026-08-12

`Member.email`에 `@Column(unique = true)`를 붙였는데, `bootRun` 후 `SHOW INDEX FROM member`로 직접 확인해보니 `PRIMARY`/`username` UNIQUE만 있고 `email` 인덱스가 전혀 없었다 — `ddl-auto: update`는 새 컬럼(`email_verified`)은 안전하게 추가하지만, **기존에 있던 컬럼에 새로 거는 제약은 반영하지 않는다**는 걸 실측으로 확인. 로컬 dev DB(`SELECT COUNT(*) FROM member`가 0이라 안전하게)에 수동으로 `ALTER TABLE member ADD UNIQUE INDEX uk_member_email (email);`를 실행해서 정상 적용되는 걸 확인함. Railway 프로덕션 DB에 대한 동일 절차는 `docs/deploy-guide.md` 9-2절에 정리해서 안내함(중복 이메일 사전 확인 쿼리 포함) — 아직 실제로 프로덕션에 적용은 안 함, 배포 전 사용자가 직접 수행해야 하는 단계.

## Attempt 2 (테스트 작성 중 디버깅 — 테스트 리소스가 메인 설정을 완전히 덮어씀) — 2026-08-12

`AuthControllerTest`에 신규 테스트 10개를 추가하고 `./gradlew test --tests AuthControllerTest`만 돌렸을 땐 24개 전부 통과했는데, `./gradlew test`로 전체 스위트(158개)를 돌리니 129개가 `PlaceholderResolutionException: Could not resolve placeholder 'app.base-url'`로 컨텍스트 로딩 자체가 실패했다.

원인: `src/test/resources/application.yaml`이 존재하면 Spring Boot는 `src/main/resources/application.yaml`을 **아예 안 읽고 테스트 쪽만** 적용한다(merge 안 됨, 같은 상대 경로 리소스라 테스트 클래스패스가 우선). 메인 쪽에만 추가해뒀던 `app.base-url`이 테스트 컨텍스트엔 없어서, `EmailService`의 `@Value("${app.base-url}")` 필드 주입이 실패 → 그 빈을 참조하는 모든 `@SpringBootTest` 컨텍스트가 통째로 안 뜸. `AuthControllerTest`만 따로 돌렸을 때 통과했던 이유는 스프링이 컨텍스트를 캐싱해서, 그 이전에 이미 뜬 캐시된 컨텍스트를 재사용했기 때문(실제로는 깨져있었는데 우연히 안 드러난 것) — **부분 테스트 실행으로 "통과"를 확인하는 게 왜 불충분한지 실제로 보여준 사례**.

- 수정 1: `src/test/resources/application.yaml`에 `app.base-url: http://localhost:8080` 추가.
- 재실행 결과: 129개 실패 → 24개 실패(전부 `AuthControllerTest`)로 줄었지만 여전히 실패. 원인 확인해보니 이번엔 `NoSuchBeanDefinitionException: No qualifying bean of type JavaMailSender` — 테스트 쪽엔 `spring.mail.host`가 없어서 Spring Boot 메일 자동 구성 자체가 `JavaMailSender` 빈을 안 만들었던 것(같은 "테스트 리소스가 메인을 덮어쓴다" 문제의 연장).
- 수정 2: `src/test/resources/application.yaml`에 `spring.mail.{host,port,username,password,properties...}` 추가(테스트용 빈 자격증명).
- 재실행 결과: 158개 중 1개만 실패 — 이번엔 `mailHealthContributor` 빈 생성 중 `'beans' must not be empty` 예외. `management.health.mail.enabled: false`도 메인에만 있고 테스트엔 없어서 발생한, 역시 같은 근본 원인. `AuthControllerTest`가 `JavaMailSender`를 `@MockitoBean`으로 대체해두다 보니 이 조합에서 컨텍스트가 깨졌다.
- 수정 3: `src/test/resources/application.yaml`에 `management.health.mail.enabled: false` 추가.
- 최종 재실행: 158/158 전부 통과.

**교훈**: 이 프로젝트는 `src/test/resources/application.yaml`이 메인 설정을 완전히 대체하는 구조라서, 메인 `application.yaml`에 새 설정을 추가할 때마다 테스트 쪽에도 필요한 최소값을 같이 넣어야 한다는 걸 이번에 명시적으로 확인함 — 앞으로 새 `@Value`/자동구성 의존 설정을 추가할 때 체크리스트에 추가할 만한 항목.

## Attempt 2 (테스트 격리 — rate-limit 키 정리 누락) — 2026-08-12

전체 스위트를 반복 실행하는 과정에서 `resendVerificationEmail_alwaysReturnsGenericSuccess`, `requestPasswordReset_alwaysReturnsGenericSuccess` 두 테스트가 간헐적으로 429(`TOO_MANY_REQUESTS`)로 실패하는 걸 발견했다. 원인: 이 두 테스트가 호출하는 `POST /verify-email/resend`, `POST /password/reset-request`에 새로 건 레이트리밋(5분·3회, IP 단위) 키를 `@BeforeEach`/`@AfterEach`가 정리 안 하고 있었음 — 로그인 IP 레이트리밋 키(`002-login-rate-limit.md`에서 이미 겪은 것과 정확히 같은 패턴) 때는 정리하면서 이번에 새로 추가한 2개는 빠뜨렸었다. `cleanUpBeforeEach`/`cleanUpLoginAttemptKeys`에 `rate-limit:verify-email-resend:127.0.0.1`, `rate-limit:password-reset-request:127.0.0.1` 삭제를 추가해서 해결.

## 최종 검증

- `./gradlew test`(전체 스위트, `clean` 없이) — 158/158 전부 통과, 회귀 없음.
- 로컬 dev DB/Redis에 테스트가 남긴 커밋된 데이터 없음 확인(`AuthControllerTest`가 `@Transactional`이라 전부 롤백됨) — Redis는 반복 디버깅 중 쌓인 잔여 키를 `FLUSHALL`로 정리함(이것도 `002-login-rate-limit.md`와 동일한 로컬 반복 실행 특유의 상황, CI는 매번 새 Redis 서비스 컨테이너로 시작해서 재현 안 됨).

## Attempt 3 — 실제 Gmail SMTP 발송 검증 — 2026-08-12 ✅ PASS

발신용 Gmail 계정(`byeongjunmin00@gmail.com`)에 2단계 인증 + 앱 비밀번호를 발급받아, `MAIL_USERNAME`/`MAIL_PASSWORD`/`APP_BASE_URL` 환경변수를 실제 값으로 채워서 로컬 서버를 띄우고 실제 SMTP 발송까지 검증함.

- **이메일 인증**: 실제 이메일로 회원가입(`POST /api/auth/signup`) → 인증 전 로그인 시도 시 실제로 `EMAIL_NOT_VERIFIED`(403) → 받은편지함에서 실제 메일 수신 확인 → 메일 안의 실제 링크 클릭(`GET /api/auth/verify-email`) → 같은 계정 로그인 성공(200) 확인.
- **비밀번호 재설정**: 실제 이메일로 재설정 요청(`POST /api/auth/password/reset-request`) → 받은편지함에서 실제 메일 수신 확인 → Redis에서 실제 발급된 토큰으로 재설정 실행(`POST /api/auth/password/reset`) → 이전 비밀번호 로그인 시도 시 `LOGIN_FAILED`(401), 새 비밀번호로는 로그인 성공(200) 확인.
- 두 흐름 다 예외 없이 첫 시도에 정상 동작함 — 이번 항목에서 나온 버그 4개는 전부 인프라/테스트 설정 문제였고, 실제 메일 발송 자체는 설계대로 문제없이 붙었음.
- 검증 후 정리: 실제 로컬 dev DB에 남은 테스트 계정(`gong9ri-smtp-test1`) 삭제, Redis `FLUSHALL`로 테스트 토큰 정리, 로컬 서버 프로세스 종료, 앱 비밀번호가 찍힌 임시 로그 파일 삭제(내용엔 안 찍혔지만 안전 차원에서 확인 후 삭제).
