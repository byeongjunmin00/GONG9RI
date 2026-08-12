# 로그인 (auth/login) — Design

## 개요

`member`의 username/password로 인증하고, 세션 기반으로 로그인을 유지한다. 아이디 없음/비밀번호 틀림을 구분하지 않고 `LOGIN_FAILED`로 통일 응답해 계정 존재 여부를 노출하지 않는다. 이번 기능으로 `SecurityConfig`가 `POST /api/auth/signup`·`POST /api/auth/login`만 인증 없이 열어두고 나머지는 인증을 요구하도록 좁혔다(이후 상품 조회·정적 리소스·WebSocket 핸드셰이크 등이 추가로 permitAll에 편입됐다 — 현재 전체 범위는 아래 "규칙/검증" 참고).

## API / 인터페이스

- `POST /api/auth/login` — 상세: `docs/api/auth.md`. 응답은 `signup`과 동일한 `MemberResponse` 형태 재사용.

## 데이터 모델

- 추가 테이블 없음. `member` 재사용(`docs/db/member.md`).

## 규칙 / 검증

- 인증 흐름: `AuthenticationManager.authenticate()` → 성공 시 `SecurityContext`를 `SecurityContextRepository`(`HttpSessionSecurityContextRepository`)로 세션에 저장
- 사용자 조회: `MemberDetailsService`(`UserDetailsService` 구현)가 `MemberRepository.findByUsername`으로 조회, 없으면 `UsernameNotFoundException` → Spring Security가 인증 실패로 처리
- 비밀번호 검증: Spring Security가 자동 구성하는 `DaoAuthenticationProvider`가 `MemberDetailsService` + signup 때 만든 `PasswordEncoder`(BCrypt)로 처리 — 별도 구현 불필요
- 인증 실패(아이디 없음/비번 틀림 모두) → `AuthController`가 `AuthenticationException`을 잡아 `BusinessException(LOGIN_FAILED)`로 변환, `401` 응답
- `SecurityConfig`: 이 기능 도입 시점엔 `POST /api/auth/signup`, `POST /api/auth/login`만 permitAll, 나머지 `anyRequest().authenticated()`였다 — 인증이 필요한 `/api/auth/logout`은 `docs/dev/auth/logout/design.md` 참고. 예견했던 대로("product/team 등 새 컨트롤러가 생기면 재검토 필요") 이후 `GET /api/products/**`, 정적 리소스(`/`, `/*.html`, `/**/*.html`, `/css/**`, `/js/**`, `/partials/**`), `/ws-team/**`도 permitAll로 추가됐다(각 기능 design.md 참고) — 지금은 "signup/login 외 나머지는 전부 인증 필요"가 더 이상 정확하지 않고, 최신 전체 규칙은 `config/SecurityConfig.java`를 기준으로 삼아야 한다.

## 로그인 시도 제한 (로그인 고도화 1단계, 2026-08-12)

"실제 사이트처럼" 로그인을 더 단단하게 만드는 작업의 첫 단계. 지금까지는 `AuthenticationManager.authenticate()`를 몇 번이고 무제한으로 시도할 수 있는 상태였다. 성격이 다른 두 레이어로 방어한다.

### 레이어 1 — IP 단위 요청 제어 (`RateLimitFilter` 확장)

`common/filter/RateLimitFilter`(발제 백엔드 도전과제 "트래픽 제어"로 `team/join`용으로 먼저 만들어짐, `docs/dev/team/crud/design.md` 참고)를 규칙 리스트(`RateLimitRule(method, pathPattern, keyPrefix, window, limit)`) 구조로 일반화하고, `POST /api/auth/login`용 규칙(윈도우 60초·임계값 10회, 키 접두사 `login`)을 추가했다. team/join 쪽 동작(10초·20회)은 완전히 그대로 유지된다 — 같은 메커니즘(INCR+EXPIRE 고정 윈도우, fail-open, `ApiResponse.failure` 수동 직렬화, `ErrorCode.TOO_MANY_REQUESTS`)을 두 번째로 실제 쓰는 시점이라, 거의 동일한 필터 클래스를 하나 더 만드는 것보다 매칭 규칙만 뽑아내는 쪽이 중복이 훨씬 적었다.

- 임계값(60초·10회)은 **실측 근거 없는 초기값**이다(`team.join-strategy`·`refund-trigger` 등과 같은 성격) — 정상 사용자가 비밀번호를 몇 번 틀려도 절대 안 걸리지만, 스크립트성 무차별 대입은 확실히 막는 수준으로 감으로 잡았다.
- 이 레이어는 "같은 클라이언트(IP)가 로그인 엔드포인트를 얼마나 자주 두드리는가"만 본다 — 어떤 계정을 노리는지는 관심 없다.

### 레이어 2 — 계정 단위 실패 횟수 제한 (`LoginAttemptGuard`)

IP 레이어만으로는 "여러 IP를 돌려가며 특정 계정 하나만 노리는" 공격을 못 막는다. 그래서 `username` 단위로 **실패한 로그인만** 세는 별도 컴포넌트(`common/security/LoginAttemptGuard`)를 뒀다 — 필터가 아니라 `AuthController`(이미 파싱된 `request.username()`을 갖고 있는 시점)에서 처리한다. 필터에서 하려면 body를 미리 읽어서 캐싱해야 하는 불필요한 복잡도가 생겨서 피했다.

- 키 `login-fail:{username}`, 윈도우 10분·임계값 5회(도달 시 잠금, `count >= LIMIT`) — 역시 실측 근거 없는 초기값. fail-open(Redis 장애 시 잠금 없이 통과).
- `AuthController.login()` 흐름: `isLocked()` 확인(잠겨있으면 `authenticate()` 자체를 호출 안 하고 즉시 `LOGIN_ATTEMPTS_EXCEEDED`) → 인증 실패 시 `recordFailure()` → **인증 성공 시 `recordSuccess()`로 카운터 리셋**(정상 사용자가 몇 번 틀리고 결국 맞게 입력하면 그걸로 끝, 이후 다시 불이익 안 받음).
- **보안 설계 포인트**: 존재하지 않는 username도 실제 계정과 똑같이 카운트한다(존재 여부로 분기하지 않음) — 그래야 "이 계정만 잠금 메시지가 뜬다"는 걸로 계정 존재 여부가 새는 걸 막을 수 있다(기존 `LOGIN_FAILED` 통일 응답과 같은 원칙).
- 신규 `ErrorCode.LOGIN_ATTEMPTS_EXCEEDED`(429) — 기존 `TOO_MANY_REQUESTS`(범용 트래픽 제어용)와 의미가 달라서 재사용하지 않고 새로 만들었다.
- **정직하게 남기는 트레이드오프**: 계정 단위 잠금은 "공격자가 피해자 계정 이름만 알면, 다른 IP로 일부러 틀린 비밀번호를 반복 입력해서 그 계정의 정상 로그인을 막아버릴 수 있다"는 역효과(계정 잠금 DoS)가 있다. CAPTCHA·점진적 백오프 같은 정교한 방어는 이번 스코프 밖 — 이 트레이드오프를 인지하고 있다는 것만 문서에 남긴다.

## 이메일 인증 확인 (로그인 고도화 2단계, 2026-08-12)

`POST /api/auth/login`에 이메일 인증 여부 확인이 추가됐다 — 상세 설계는 `docs/dev/auth/email-verification/design.md` 참고. 요약: 비밀번호 인증까지 성공한 뒤(`loginAttemptGuard.recordSuccess()` 호출 이후) `member.isEmailVerified()`가 false면 세션을 만들지 않고 `EMAIL_NOT_VERIFIED`(403)로 거절한다. `recordSuccess()`를 먼저 호출하는 이유: 비밀번호는 실제로 맞았으므로 로그인 시도 제한 카운터 관점에서는 "성공"으로 취급해야 하고, 그 뒤에 별도 조건(이메일 인증)으로 세션 발급만 막는 구조다.

## 관련 코드 위치

- `dto/MemberLoginRequest.java`
- `repository/MemberRepository.java` — `findByUsername` 추가됨
- `common/security/{MemberUserDetails,MemberDetailsService}.java`
- `config/SecurityConfig.java` — `AuthenticationManager`/`SecurityContextRepository` 빈, 인가 규칙
- `controller/AuthController.java` — `login()` 메서드
- `common/exception/ErrorCode.java` — `LOGIN_FAILED`, `LOGIN_ATTEMPTS_EXCEEDED` 추가
- `common/filter/RateLimitFilter.java` — 로그인 IP 규칙 추가(규칙 리스트로 일반화)
- `common/security/LoginAttemptGuard.java` — 계정 단위 실패 횟수 제한(신규)
- 테스트: `src/test/.../controller/AuthControllerTest.java`(signup 테스트와 같은 클래스에 이어서 작성, 계정 잠금 통합 시나리오 2케이스 추가), `common/security/LoginAttemptGuardTest.java`(신규), `common/filter/LoginRateLimitFilterTest.java`(신규)
