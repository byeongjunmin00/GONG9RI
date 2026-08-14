# 소셜 로그인 — 카카오 (로그인 고도화 3단계) — Design

## 개요

로그인 고도화 로드맵의 3단계(1단계 로그인 시도 제한, 2단계 이메일 인증+비밀번호 재설정 — `docs/dev/auth/login/design.md`, `docs/dev/auth/email-verification/design.md`). 카카오 계정으로 바로 가입/로그인할 수 있게 한다.

이 프로젝트는 세션 기반 인증을 처음부터 전부 수동으로 구현해왔다(`spring-boot-starter-oauth2-client`의 `oauth2Login()` 자동 필터 대신 `AuthController.login()`이 직접 `AuthenticationManager.authenticate()` → `SecurityContextRepository.saveContext()`를 호출) — 카카오 로그인도 같은 방식(Authorization Code 흐름을 직접 구현)으로 일관되게 갔다. PortOne 연동 때 공식 SDK 없이 `RestClient`로 직접 REST 호출한 것과 같은 판단으로, 카카오도 별도 SDK 의존성 없이 `RestClient`로 직접 호출한다.

## 흐름

1. `GET /api/auth/kakao/login?role=BUYER|SELLER`(선택, 기본 `BUYER`) — 카카오 인가 URL로 302 리다이렉트. 랜덤 nonce(`state`)와 함께 `role`도 `HttpSession`에 저장한다(신규 가입 시에만 사용, CSRF 방지는 `state`가 담당). 인가 URL에는 `prompt=login`을 항상 포함한다(2026-08-14 추가 — 아래 "카카오 자체 세션 강제 재인증" 참고).
2. 사용자가 카카오에서 로그인·동의 → 카카오가 `GET /api/auth/kakao/callback?code=...&state=...`로 리다이렉트.
3. 콜백에서: 세션에 저장된 `state`와 일치 확인(불일치 시 즉시 거부, 카카오 API 호출 자체를 안 함) → `code`로 액세스 토큰 발급 → 사용자 정보 조회 → 카카오 `id`로 기존 연동 계정 조회, 없으면 신규 생성 → 세션 생성(기존 `SecurityContextRepository` 재사용, `AuthController.login()`과 동일한 방식) → `/`(성공) 또는 `/login.html?error=kakao`(실패)로 302 리다이렉트.
4. 실패(state 불일치, 토큰 교환 실패, 이메일 충돌 등)는 전부 `/login.html?error=kakao`로 리다이렉트한다 — 이 흐름은 브라우저 풀 리다이렉트라 JSON 에러 응답이 의미 없다(`?signup=success`/`?reset=success` 처리하는 기존 `login.js` 패턴과 동일하게 쿼리 파라미터로 안내).

## 신규 가입 처리 (카카오 `id` 기준)

`Member.kakaoId`(String, UNIQUE, nullable) — 일반 회원가입 계정은 null, 카카오 계정만 값이 있다. 신규 카카오 로그인 시 `Member.ofKakao(...)` 팩토리로 생성(`MemberService.findOrCreateByKakao(KakaoUserInfo, Role intendedRole)`):

- `username`: `"kakao_" + kakaoId`(합성값 — 카카오 계정은 이 아이디로 일반 로그인 폼에 시도해도 비밀번호를 모르니 문제없음)
- `password`: 랜덤 UUID를 BCrypt로 인코딩해서 저장(추측 불가능한 값 — 컬럼을 nullable로 바꾸는 스키마 변경 대신, 알 수 없는 값을 채워서 일반 로그인 경로로는 사실상 로그인 불가능하게 만드는 쪽을 택함)
- `email`: 카카오 동의 항목에 이메일이 포함돼 있으면 그 값을 쓴다. **이미 다른 계정이 그 이메일을 쓰고 있으면 카카오 로그인 자체를 거부**한다(자동 연동 안 함 — 이메일 소유권을 우리가 검증한 게 아니라서, 자동 연동하면 계정 탈취 경로가 될 수 있음). 이메일 동의가 없거나 카카오 계정에 이메일이 없으면 `"kakao_" + kakaoId + "@kakao.local"`(합성 placeholder, 우리 이메일 인증 플로우 대상 아님 — 이 주소로 발송 시도 자체를 안 함)
- `emailVerified`: **true로 시작**한다. 카카오 로그인 자체가 본인 확인 수단이라 우리 쪽 이메일 인증 게이트가 의미 없고, placeholder 이메일이면 애초에 인증 메일을 보낼 수도 없어서 false로 두면 영구적으로 로그인 못 하는 버그가 된다.
- `role`: **회원가입 페이지의 진입 버튼이 넘긴 `role` 쿼리파라미터를 그대로 쓴다**(2026-08-13 추가 — 최초엔 `BUYER` 고정이었으나, "카카오 로그인이 편한데 판매자는 왜 못 쓰나"는 실사용 관점 피드백을 받아 확장함). `AuthController.kakaoLogin()`에서 `role`을 세션에 저장해뒀다가, 콜백에서 **신규 가입일 때만** 꺼내 쓴다.

이미 연동된 카카오 `id`로 다시 로그인하면 기존 `Member`를 그대로 찾아서 로그인만 시킨다(재가입 안 함, `findByKakaoId` 우선 조회) — 이때는 `intendedRole`을 완전히 무시하고 로그인은 기존 role 그대로 진행한다. 로그인 페이지의 "카카오로 로그인" 버튼(role 파라미터 없음)으로 기존 판매자 계정에 재로그인해도 role이 바뀌는 일이 없어야 하기 때문 — `KakaoLoginTest.kakaoCallback_existingAccount_ignoresIntendedRole`로 검증.

### role 불일치 안내 (2026-08-14 추가)

role을 그대로 유지하는 것과 별개로, "이미 SELLER로 가입된 계정인데 회원가입 페이지의 '카카오로 구매자 시작하기' 버튼을 눌러 재로그인"하는 경우 기존엔 아무 안내 없이 조용히 기존 role로 로그인됐다(실사용 버그 리포트로 발견). 로그인은 그대로 진행하되 안내만 추가한다:

- `MemberService.findOrCreateByKakao()`의 반환 타입을 `Member` → `KakaoLoginResult(Member member, boolean roleMismatch)`(record, `dto/KakaoLoginResult.java`)로 바꿔, 기존 회원 재로그인 시 `member.getRole() != intendedRole`을 계산해 호출부에 알려준다.
- `AuthController.kakaoLogin()`의 `role` 쿼리파라미터 파싱을 `parseRoleOrDefault`(없으면 `BUYER`로 세션에 저장) → `parseRoleOrNull`(없거나 잘못된 값이면 세션에 아예 저장하지 않음)로 변경 — "역할을 명시적으로 골라 들어온 진입(회원가입 페이지의 역할별 버튼)"과 "role 파라미터 없는 일반 '카카오로 로그인' 버튼 진입"을 구분해야, 후자는 role이 달라도 안내를 띄우지 않는(현행 유지) 요구사항을 만족할 수 있어서다.
- `kakaoCallback()`은 `explicitRoleRequested(세션에 role 값이 있었는지) && result.roleMismatch()`일 때만 성공 리다이렉트를 `/?kakaoRoleMismatch=<실제 role>`로 보낸다(그 외엔 기존과 동일하게 `/`). role이 일치하거나 role 파라미터 없는 일반 로그인 경로는 안내 없이 조용히 `/`로 리다이렉트된다(현행 유지).
- 프론트: `index.html`에 배너 영역(`#page-alert`, 기존 `.form-alert`/`.form-alert--success` 재사용), `js/main.js`가 `?kakaoRoleMismatch=BUYER|SELLER` 쿼리를 읽어 "이미 O로 가입되어 있어 O로 로그인되었습니다" 문구를 표시한다(`login.js`의 `?signup=success`와 같은 "쿼리파라미터 + 페이지 로드시 배너" 패턴).
- 검증: `KakaoLoginTest.kakaoCallback_existingAccount_ignoresIntendedRole`(role 유지 + 안내 신호 확인), `kakaoCallback_existingAccount_withoutExplicitRole_noMismatchSignal`(신규, role 파라미터 없는 재로그인은 안내 없음 확인).

### 카카오 자체 세션 강제 재인증 (2026-08-14 추가)

실사용 중 발견: 우리 앱에서 로그아웃한 뒤 "카카오로 로그인"을 다시 눌러도, 카카오 자체 로그인
세션(기본 24시간, "로그인 상태 유지" 선택 시 최대 1개월 — 카카오 고객센터 안내 기준)이
남아있으면 카카오가 로그인 화면 없이 곧바로 인가 코드를 내려줘서 재인증 없이 재로그인됐다.
실제 프로덕션(`gong9ri-production.up.railway.app`)에서 직접 재현 확인함.

`AuthController.kakaoLogin()`이 조립하는 인가 URL에 `prompt=login` 파라미터를 추가해 해결한다.
카카오 공식 문서(Kakao Developers REST API): "기존 사용자 인증 여부와 상관없이 사용자에게
카카오계정 로그인 화면을 출력하여 다시 사용자 인증을 수행하고자 할 때 사용". role 파라미터
유무와 무관하게 `kakaoLogin()`의 모든 진입 경로(로그인 페이지의 일반 버튼, 회원가입 페이지의
역할별 버튼)에 항상 적용한다 — 별도 분기 없음. 부수 효과로, 사용자가 다른 카카오 계정으로
전환해서 로그인하고 싶을 때도 항상 로그인 화면이 뜨므로 계정 전환이 가능해진다.

**알려진 제한**: 카카오톡 인앱 브라우저에서는 `prompt=login`이 지원되지 않는다(카카오 공식
문서에 명시) — 이 경우 카카오 자체 세션이 남아있으면 여전히 재인증 없이 통과될 수 있다. 이번
스코프에서는 별도 분기 처리를 하지 않는다(알려진 한계로 기록).

검증: `KakaoLoginTest.kakaoLogin_authorizeUrl_includesPromptLogin` — 인가 요청의 `Location`
헤더에 `prompt=login`이 포함되는지 확인.

### 카카오 합성 username 중복 사전 검증 (2026-08-14 추가)

신규 가입 분기에서 `username = "kakao_" + kakaoId`를 만들기 전에 `memberRepository.existsByUsername(username)`으로 사전 검증한다(일반 회원가입으로 같은 합성 username을 먼저 선점했을 가능성 — 희박하지만 실존). 충돌 시 `signup()`과 동일한 `BusinessException(ErrorCode.DUPLICATE_USERNAME)`을 던지고, 컨트롤러의 기존 `catch(Exception) → /login.html?error=kakao` 경로를 그대로 탄다. 검증: `KakaoLoginTest.kakaoCallback_synthesizedUsernameConflict_redirectsToError`.

### 진단성 개선 (2026-08-14 추가)

`kakaoCallback()`의 catch 블록이 `log.warn("...", e.getMessage())`로 메시지만 남기고 스택트레이스를 남기지 않아 실패 원인 추적이 어려웠다 — `log.error("...: error={}", e.getMessage(), e)`로 변경해 예외 객체(스택트레이스)까지 남긴다(`GlobalExceptionHandler.handleException()`의 `log.error("Unexpected exception", e)` 패턴과 동일). 단, 이 catch 블록은 `BusinessException`(이메일/username 충돌 등 예상 가능한 상황)과 진짜 예상 못 한 예외를 구분하지 않고 전부 ERROR로 남긴다 — `docs/code-convention.md`의 로그 레벨 기준(BusinessException류는 WARN)과는 다르다. 컨트롤러가 리다이렉트로 흐름을 끊는 구조라 `GlobalExceptionHandler`를 거치지 않기 때문인데, 레벨을 세분화하는 건 이번 스코프 밖으로 남겨둔다(알려진 한계로 기록).

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

- `login.html` — "카카오로 로그인" 버튼(단순 `<a href="/api/auth/kakao/login">`, role 파라미터 없음 → 신규 가입 시 기본 `BUYER`, 기존 계정 재로그인 시 role 불일치 안내 없음). `login.js`에 `?error=kakao` 쿼리 처리 추가(기존 `?signup=success`/`?reset=success`와 같은 패턴).
- `signup.html` — "카카오로 구매자 시작하기"(`?role=BUYER`)/"카카오로 판매자 시작하기"(`?role=SELLER`) 두 버튼(2026-08-14: "카카오로 구매자/판매자 가입"에서 문구 변경). 둘 다 전체 페이지 리다이렉트라 별도 JS 불필요 — 일반 회원가입 폼의 "구매자로 가입/판매자로 가입" 라디오 버튼과 같은 두 갈래 구조를 그대로 반영한 것.
- `index.html`/`js/main.js`(2026-08-14 추가) — `?kakaoRoleMismatch=BUYER|SELLER` 안내 배너(위 "role 불일치 안내" 참고).

## 실측 검증 (2026-08-12)

- `KakaoLoginTest`(신규, `KakaoClient`를 `@MockitoBean`으로 대체한 통합 시나리오 4개): 신규 가입 성공, 기존 연동 계정 재로그인(중복 생성 안 됨), 이메일 충돌 거부, state 불일치 거부(카카오 API 자체를 호출 안 하는 것까지 `Mockito.verify(never())`로 확인). 전체 회귀 194개 포함 전부 통과.
- **로컬 dev DB에 `kakao_id` 컬럼이 UNIQUE 인덱스까지 실제로 자동 생성되는지 실측 확인**(`SHOW INDEX FROM member`) — `email` UNIQUE를 기존 컬럼에 리트로핏했을 때(`docs/db/member.md` 마이그레이션 메모)와 달리, **브랜드 뉴 컬럼은 `ddl-auto: update`가 UNIQUE 제약까지 한 번에 만들어준다**는 걸 이번에 실측으로 확인함(추측 아님) — `kakao_id varchar(100) YES UNI` 확인.
- **실제 카카오 로그인 전체 브라우저 흐름 실측 완료(2026-08-13)**: 카카오 개발자 콘솔에 실제 앱 등록(REST API 키 발급, Redirect URI 2개 등록, "카카오 로그인" 활성화) 후 로컬(`localhost:8080`)에서 실제 브라우저로 "카카오로 로그인" 버튼 클릭 → 카카오 로그인/동의 → 콜백 → 로그인 완료까지 전체 완주. DB에서 실제 생성된 회원(`kakao_id=5036215176`) 확인 — `username=kakao_5036215176`, `email=kakao_5036215176@kakao.local`(이메일 동의 안 받은 계정이라 placeholder 경로가 실제로 탐), `role=BUYER`, `email_verified=true`(BIT 컬럼이라 `HEX()`로 재확인). 검증 후 실제 DB 회원과 Redis 키 정리함.
  - **실측 중 발견한 이슈**: Kakao REST API 키는 **발급 시점부터 Client Secret이 기본 활성화**돼 있어서(`client_id`만 보내면 `KOE010: Bad client credentials`로 거부됨), `KAKAO_CLIENT_SECRET`을 반드시 같이 보내야 했다 — 계획 당시엔 "선택"으로 예상했으나 실제로는 이 앱 기준 필수였음(콘솔에서 직접 비활성화하지 않는 한). `docs/deploy-guide.md` 11-1절에 반영.
  - **Redirect URI 등록 위치가 예상과 달랐음**: "카카오 로그인 > 일반" 메뉴가 아니라 **"앱 > 플랫폼 키 > REST API 키 상세" 안에 있는 "카카오 로그인 리다이렉트 URI"** 섹션에서 등록해야 했다(콘솔 UI 개편으로 위치가 옮겨진 것으로 보임) — 카카오 공식 FAQ 검색으로 정확한 위치를 확인함.

## 카카오 role 분리 (2026-08-13 추가)

처음엔 카카오 가입을 `BUYER` 고정으로 스코프 밖 처리했었는데, 실사용 관점에서 "카카오 로그인이 편해서 쓰고 싶은데 판매자면 못 쓰나"라는 질문을 받고 확장했다. 별도 화면 분기(카카오 콜백 후 역할 선택 인터스티셜 등) 없이, **진입 시점에 role을 쿼리파라미터로 미리 받는** 쪽을 택함 — 일반 회원가입 폼이 이미 "구매자로 가입/판매자로 가입" 두 갈래인 것과 대칭되는 구조라 신규 상태(세션에 뭘 더 들고 있을지, 콜백 흐름을 더 쪼갤지)를 늘리지 않고 기존 `state` 세션 저장 패턴에 값 하나만 얹어서 구현 가능했다.

- `AuthController.kakaoLogin()`이 `role` 쿼리파라미터를 받아 세션에 저장(`parseRoleOrNull` — 없거나 잘못된 값이면 세션에 저장하지 않음, 2026-08-14부터. 콜백에서 신규 가입 시엔 `BUYER`로 안전하게 폴백해 400 에러로 흐름을 끊지 않는 건 동일).
- `kakaoCallback()`은 **신규 가입일 때만** 이 값을 꺼내 `findOrCreateByKakao(userInfo, intendedRole)`에 넘긴다. 이미 연동된 계정이면 완전히 무시 — 재로그인 시 role이 바뀌는 걸 막기 위한 설계 결정(테스트: `kakaoCallback_existingAccount_ignoresIntendedRole`).
- `signup.html`에 역할별 카카오 버튼 2개 추가, `login.html`의 기존 버튼은 role 파라미터 없이 그대로 둠(기본값 `BUYER` 유지, 하위호환).

## 알려진 한계 / 리스크

- **이메일 동의항목은 카카오 심사가 필요할 수 있음** — 개인 개발자 테스트 앱은 보통 "선택 동의"로 등록한 테스트 사용자(카카오 콘솔에 등록한 본인 계정 등)에 한해 이메일을 받을 수 있고, 불특정 다수에게 이메일 동의를 받으려면 카카오 비즈 심사가 필요할 수 있다 — 실제 콘솔 설정에 따라 이메일이 아예 안 올 수 있고, 그 경우 placeholder 이메일 경로가 정상 동작한다(위 "신규 가입 처리" 참고).
- **role은 서버가 검증하지 않고 그대로 신뢰한다** — `?role=SELLER`로 신규가입 자체는 누구나 할 수 있다(일반 회원가입 폼도 마찬가지로 라디오 버튼 값을 그대로 신뢰하는 구조라 기존 정책과 일관됨, 판매자 권한으로 뭘 할 수 있는지는 이 기능 스코프 밖).
- **로그인 시도 제한(`LoginAttemptGuard`)이 카카오 로그인에는 적용되지 않는다** — 카카오 쪽에서 자체적으로 무차별 대입을 방어하고, 우리 서버는 이미 발급된 유효한 인가 코드만 받으므로 이 프로젝트의 계정 단위 잠금 로직이 적용될 대상 자체가 없다(정직하게 남기는 설계 결정).

## 실측 검증 (2026-08-14)

- role 불일치 안내 + 합성 username 중복 검증 + 로그 스택트레이스 보존 수정 후 `./gradlew test` 전체 통과 확인(202개, 실패 0 — `KakaoLoginTest` 6→8, `AuthControllerTest` 24→25, 나머지 회귀 전부 그대로 통과). 상세 시도/증거는 `docs/logs/auth/social-login/001-kakao-login-session-fix.md` 참고.
- 실제 카카오 브라우저 흐름으로 role 불일치 배너를 재현하는 수동 확인은 로컬 카카오 앱 재설정이 필요해 이번엔 수행하지 않음 — `KakaoLoginTest`의 Mockito 기반 통합 테스트로만 검증됨(알려진 한계로 아래 기록).
- `prompt=login` 추가 후 `./gradlew test --rerun-tasks` 전체 통과 확인(203개, 실패 0 — `KakaoLoginTest` 8→9). 실제 프로덕션에서의 재현("카카오 자체 세션 때문에 재인증 없이 재로그인됨")을 사용자가 직접 확인해준 것을 바탕으로 원인을 특정함. 상세 시도/증거는 `docs/logs/auth/social-login/002-kakao-force-reauth.md` 참고.

## 필요한 운영 환경변수

- `KAKAO_CLIENT_ID`(REST API 키), `KAKAO_CLIENT_SECRET`(선택) — Railway 설정 방법은 `docs/deploy-guide.md` 참고.

## 관련 코드 위치

- `entity/Member.java` — `kakaoId` 필드, `ofKakao()` 팩토리
- `repository/MemberRepository.java` — `findByKakaoId` 추가
- `client/{KakaoClient,KakaoApiClient,KakaoUserInfo}.java` — 신규
- `dto/KakaoLoginResult.java`(2026-08-14 추가) — `findOrCreateByKakao()` 반환값(회원 + role 불일치 여부)
- `service/MemberService.java` — `findOrCreateByKakao()`
- `controller/AuthController.java` — `kakaoLogin()`, `kakaoCallback()`
- `config/SecurityConfig.java` — 두 엔드포인트 permitAll
- `static/login.html`/`js/login.js` — 카카오 로그인 버튼, `?error=kakao` 안내
- `static/signup.html` — 역할별 카카오 버튼 2개("...시작하기", 2026-08-14 문구 변경)
- `static/index.html`/`js/main.js`(2026-08-14 추가) — role 불일치 안내 배너
- 테스트: `src/test/.../controller/KakaoLoginTest.java`(총 9개 — role 불일치 안내 2개, 합성 username 충돌 1개, `prompt=login` 확인 1개 포함)

## 알려진 한계 / 리스크 (추가, 2026-08-14)

- 위 "진단성 개선"에서 언급한 대로, `kakaoCallback()`의 catch 블록은 예상 가능한 `BusinessException`과 진짜 예외를 구분하지 않고 전부 ERROR 레벨로 로그를 남긴다(`docs/code-convention.md` 로그 레벨 기준과 다름) — 이번 스코프 밖으로 남겨둔 알려진 한계.
