# 소셜 로그인 — 카카오 (로그인 고도화 3단계) — Design

## 개요

로그인 고도화 로드맵의 3단계(1단계 로그인 시도 제한, 2단계 이메일 인증+비밀번호 재설정 — `docs/dev/auth/login/design.md`, `docs/dev/auth/email-verification/design.md`). 카카오 계정으로 바로 가입/로그인할 수 있게 한다.

이 프로젝트는 세션 기반 인증을 처음부터 전부 수동으로 구현해왔다(`spring-boot-starter-oauth2-client`의 `oauth2Login()` 자동 필터 대신 `AuthController.login()`이 직접 `AuthenticationManager.authenticate()` → `SecurityContextRepository.saveContext()`를 호출) — 카카오 로그인도 같은 방식(Authorization Code 흐름을 직접 구현)으로 일관되게 갔다. PortOne 연동 때 공식 SDK 없이 `RestClient`로 직접 REST 호출한 것과 같은 판단으로, 카카오도 별도 SDK 의존성 없이 `RestClient`로 직접 호출한다.

## 흐름

1. `GET /api/auth/kakao/login` — 카카오 인가 URL로 302 리다이렉트. 랜덤 nonce(`state`)를 만들어 `HttpSession`에 저장 + 요청에 실어 보낸다(CSRF 방지).
2. 사용자가 카카오에서 로그인·동의 → 카카오가 `GET /api/auth/kakao/callback?code=...&state=...`로 리다이렉트.
3. 콜백에서: 세션에 저장된 `state`와 일치 확인(불일치 시 즉시 거부, 카카오 API 호출 자체를 안 함) → `code`로 액세스 토큰 발급 → 사용자 정보 조회 → 카카오 `id`로 기존 연동 계정 조회, 없으면 신규 생성 → 세션 생성(기존 `SecurityContextRepository` 재사용, `AuthController.login()`과 동일한 방식) → `/`(성공) 또는 `/login.html?error=kakao`(실패)로 302 리다이렉트.
4. 실패(state 불일치, 토큰 교환 실패, 이메일 충돌 등)는 전부 `/login.html?error=kakao`로 리다이렉트한다 — 이 흐름은 브라우저 풀 리다이렉트라 JSON 에러 응답이 의미 없다(`?signup=success`/`?reset=success` 처리하는 기존 `login.js` 패턴과 동일하게 쿼리 파라미터로 안내).

## 신규 가입 처리 (카카오 `id` 기준)

`Member.kakaoId`(String, UNIQUE, nullable) — 일반 회원가입 계정은 null, 카카오 계정만 값이 있다. 신규 카카오 로그인 시 `Member.ofKakao(...)` 팩토리로 생성(`MemberService.findOrCreateByKakao`):

- `username`: `"kakao_" + kakaoId`(합성값 — 카카오 계정은 이 아이디로 일반 로그인 폼에 시도해도 비밀번호를 모르니 문제없음)
- `password`: 랜덤 UUID를 BCrypt로 인코딩해서 저장(추측 불가능한 값 — 컬럼을 nullable로 바꾸는 스키마 변경 대신, 알 수 없는 값을 채워서 일반 로그인 경로로는 사실상 로그인 불가능하게 만드는 쪽을 택함)
- `email`: 카카오 동의 항목에 이메일이 포함돼 있으면 그 값을 쓴다. **이미 다른 계정이 그 이메일을 쓰고 있으면 카카오 로그인 자체를 거부**한다(자동 연동 안 함 — 이메일 소유권을 우리가 검증한 게 아니라서, 자동 연동하면 계정 탈취 경로가 될 수 있음). 이메일 동의가 없거나 카카오 계정에 이메일이 없으면 `"kakao_" + kakaoId + "@kakao.local"`(합성 placeholder, 우리 이메일 인증 플로우 대상 아님 — 이 주소로 발송 시도 자체를 안 함)
- `emailVerified`: **true로 시작**한다. 카카오 로그인 자체가 본인 확인 수단이라 우리 쪽 이메일 인증 게이트가 의미 없고, placeholder 이메일이면 애초에 인증 메일을 보낼 수도 없어서 false로 두면 영구적으로 로그인 못 하는 버그가 된다.
- `role`: `BUYER` 고정 — 판매자(SELLER) 가입은 계속 기존 이메일/비밀번호 경로만 지원한다(스코프 밖으로 명시).

이미 연동된 카카오 `id`로 다시 로그인하면 기존 `Member`를 그대로 찾아서 로그인만 시킨다(재가입 안 함, `findByKakaoId` 우선 조회).

## 신규 코드

- `client/KakaoClient.java`(인터페이스) + `client/KakaoApiClient.java`(`RestClient` 구현, SDK 의존성 없음 — `PortOneClient`/`PortOneApiClient`와 동일 패턴): `exchangeCodeForAccessToken(code, redirectUri)`(`POST https://kauth.kakao.com/oauth/token`, `application/x-www-form-urlencoded`), `getUserInfo(accessToken)`(`GET https://kapi.kakao.com/v2/user/me`). 카카오 응답은 snake_case(`access_token`, `kakao_account`)라 `@JsonProperty`로 명시적으로 매핑한다(프로젝트 전역 네이밍 전략 설정이 없어서 자동 변환에 기대지 않음).
- `client/KakaoUserInfo.java`(record) — `id`/`email`/`nickname`만 매핑(그 외 필드는 무시).
- `service/MemberService.findOrCreateByKakao(KakaoUserInfo)` — 위 "신규 가입 처리" 규칙 구현, 이메일 충돌 시 `BusinessException(DUPLICATE_EMAIL)`(컨트롤러가 잡아서 리다이렉트로 변환).
- `controller/AuthController.java`에 `GET /api/auth/kakao/login`, `GET /api/auth/kakao/callback` 추가. `redirect_uri`(`app.base-url` + `/api/auth/kakao/callback`)는 인가 요청/토큰 교환 양쪽에서 정확히 같은 값이어야 한다는 카카오 API 요구사항 때문에 `kakaoRedirectUri()` 한 곳에서만 조립한다.
- `config/SecurityConfig.java`에 두 엔드포인트 permitAll 추가(로그인 전 사용자가 쓰는 흐름).
- `entity/Member.java`에 `kakaoId` 필드 + `Member.ofKakao(...)` 정적 팩토리 추가.
- `repository/MemberRepository.java`에 `findByKakaoId(String)` 추가.
- 설정: `kakao.client-id`(`KAKAO_CLIENT_ID`), `kakao.client-secret`(`KAKAO_CLIENT_SECRET`, 카카오 콘솔에서 "보안" 활성화했을 때만 필요) — redirect-uri는 새 환경변수를 안 만들고 기존 `app.base-url` + 고정 경로로 조합해서 중복 정보를 줄인다.

## 프론트

`login.html`에 "카카오로 로그인" 버튼(단순 `<a href="/api/auth/kakao/login">`, 전체 페이지 리다이렉트라 JS API 호출 불필요) 추가. `login.js`에 `?error=kakao` 쿼리 처리 추가(기존 `?signup=success`/`?reset=success`와 같은 패턴).

## 실측 검증 (2026-08-12)

- `KakaoLoginTest`(신규, `KakaoClient`를 `@MockitoBean`으로 대체한 통합 시나리오 4개): 신규 가입 성공, 기존 연동 계정 재로그인(중복 생성 안 됨), 이메일 충돌 거부, state 불일치 거부(카카오 API 자체를 호출 안 하는 것까지 `Mockito.verify(never())`로 확인). 전체 회귀 194개 포함 전부 통과.
- **로컬 dev DB에 `kakao_id` 컬럼이 UNIQUE 인덱스까지 실제로 자동 생성되는지 실측 확인**(`SHOW INDEX FROM member`) — `email` UNIQUE를 기존 컬럼에 리트로핏했을 때(`docs/db/member.md` 마이그레이션 메모)와 달리, **브랜드 뉴 컬럼은 `ddl-auto: update`가 UNIQUE 제약까지 한 번에 만들어준다**는 걸 이번에 실측으로 확인함(추측 아님) — `kakao_id varchar(100) YES UNI` 확인.
- **실제 카카오 로그인 전체 브라우저 흐름 실측은 아직 안 함** — 카카오 개발자 콘솔 앱 등록(REST API 키 발급, Redirect URI 등록, "카카오 로그인" 활성화)이 선행 조건이라 별도로 진행 예정.

## 알려진 한계 / 리스크

- **이메일 동의항목은 카카오 심사가 필요할 수 있음** — 개인 개발자 테스트 앱은 보통 "선택 동의"로 등록한 테스트 사용자(카카오 콘솔에 등록한 본인 계정 등)에 한해 이메일을 받을 수 있고, 불특정 다수에게 이메일 동의를 받으려면 카카오 비즈 심사가 필요할 수 있다 — 실제 콘솔 설정에 따라 이메일이 아예 안 올 수 있고, 그 경우 placeholder 이메일 경로가 정상 동작한다(위 "신규 가입 처리" 참고).
- **판매자(SELLER) 계정은 카카오 가입 대상에서 제외** — 스코프 밖으로 명시한 제약, 판매자는 계속 이메일/비밀번호로만 가입해야 한다.
- **로그인 시도 제한(`LoginAttemptGuard`)이 카카오 로그인에는 적용되지 않는다** — 카카오 쪽에서 자체적으로 무차별 대입을 방어하고, 우리 서버는 이미 발급된 유효한 인가 코드만 받으므로 이 프로젝트의 계정 단위 잠금 로직이 적용될 대상 자체가 없다(정직하게 남기는 설계 결정).

## 필요한 운영 환경변수

- `KAKAO_CLIENT_ID`(REST API 키), `KAKAO_CLIENT_SECRET`(선택) — Railway 설정 방법은 `docs/deploy-guide.md` 참고.

## 관련 코드 위치

- `entity/Member.java` — `kakaoId` 필드, `ofKakao()` 팩토리
- `repository/MemberRepository.java` — `findByKakaoId` 추가
- `client/{KakaoClient,KakaoApiClient,KakaoUserInfo}.java` — 신규
- `service/MemberService.java` — `findOrCreateByKakao()`
- `controller/AuthController.java` — `kakaoLogin()`, `kakaoCallback()`
- `config/SecurityConfig.java` — 두 엔드포인트 permitAll
- `static/login.html`/`js/login.js` — 카카오 로그인 버튼, `?error=kakao` 안내
- 테스트: `src/test/.../controller/KakaoLoginTest.java`(신규)
