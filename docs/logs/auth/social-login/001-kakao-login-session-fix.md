# 001-kakao-login-session-fix — 카카오 role 불일치 안내 + 로그아웃 세션 정리 강화 (로그)

대상: auth/social-login(주) + auth/logout(부) — 계획 문서: `docs/dev/ongoing/kakao-login-session-fix.md`

## Attempt 1 — 2026-08-14

- 시도:
  1. **역할 불일치 안내(A)**: `MemberService.findOrCreateByKakao()`의 반환 타입을
     `Member` → `KakaoLoginResult(member, roleMismatch)` record(dto)로 바꿔, 기존 회원 재로그인 시
     `member.getRole() != intendedRole`를 계산해 호출부에 알려주게 했다(로그인 자체는 기존 role
     그대로 진행 — 변경 없음).
     - `AuthController.kakaoLogin()`에서 `role` 쿼리파라미터를 세션에 저장하는 로직을
       `parseRoleOrDefault`(항상 BUYER 폴백) → `parseRoleOrNull`(없거나 잘못되면 세션에 아예
       저장 안 함)로 바꿨다 — "역할을 명시적으로 골라 들어온 진입(회원가입 페이지 버튼)"과
       "role 파라미터 없는 일반 카카오로그인 버튼 진입"을 구분해야, 후자는 role이 달라도 안내를
       띄우지 않는(현행 유지) 요구사항을 만족할 수 있어서다.
     - `kakaoCallback()`은 `explicitRoleRequested(세션값 존재 여부) && result.roleMismatch()`일
       때만 성공 리다이렉트를 `/?kakaoRoleMismatch=<실제role>`로 보낸다(그 외엔 기존과 동일하게 `/`).
  2. **프론트 배너(A)**: `index.html`에 `#page-alert`(기존 `.form-alert`/`.form-alert--success`
     CSS 재사용) 추가, `main.js`에 `?kakaoRoleMismatch=BUYER|SELLER` 쿼리를 읽어 "이미 O로
     가입되어 있어 O로 로그인되었습니다" 배너를 띄우는 IIFE 추가(login.js의 `?signup=success`
     패턴과 동일한 "쿼리파라미터 + 페이지 로드시 배너" 방식).
  3. **signup.html 버튼 문구(B)**: "카카오로 구매자/판매자 가입" → "카카오로 구매자/판매자
     시작하기".
  4. **로그아웃 정리(C)**: `AuthController.logout()`에 `SecurityContextHolder.clearContext()` +
     `JSESSIONID` 쿠키 만료(`Cookie(name, null), path=/, maxAge=0`) 추가. `SecurityConfig`에
     표준 `.logout(...)` DSL은 추가하지 않음 — 이 프로젝트가 로그인/카카오콜백 전부
     `AuthenticationManager`/`SecurityContextRepository`를 컨트롤러에서 직접 호출하는 수동 구현
     스타일이라(docs/dev/auth/social-login/design.md), 로그아웃도 같은 스타일(컨트롤러가 정리
     단계를 직접 수행)로 일관되게 갔다. `.logout()` DSL을 추가하면 `LogoutFilter`가 자동으로
     `/api/auth/logout` 경로를 가로채 JSON 204 대신 302 리다이렉트를 반환하게 돼(별도
     `logoutSuccessHandler` 커스터마이징이 필요) 기존 `logout_success`(204 기대) 테스트와도
     충돌하는 방향이라 배제했다.
  5. **bfcache 대응(C)**: `common/filter/AuthPageCacheControlFilter`(신규, `OncePerRequestFilter`)
     추가 — 로그인 필요 정적 페이지(`/buyer/mypage.html`, `/seller/mypage.html`,
     `/seller/products/new.html`, `/seller/products/edit.html`, `/checkout.html`)에
     `Cache-Control: no-store`를 붙여 로그아웃 후 뒤로가기(bfcache)로 이전 개인 데이터 화면이
     그대로 보이는 걸 막는다. 이 프로젝트는 정적 HTML을 `SecurityConfig`에서 permitAll로 열어두고
     클라이언트 JS(`header-auth.js` 등)가 401로 로그인 여부를 판별하는 구조라, 서버 필터로
     페이지별 캐시 헤더만 얹는 쪽이 범위가 가장 명확하다고 판단했다(전체 `*.html`에 걸지 않고
     실제로 개인 데이터를 보여주는 페이지로 한정).
  6. **로그 스택트레이스(D)**: `kakaoCallback()`의 catch 블록을
     `log.warn("...", e.getMessage())` → `log.error("...: error={}", e.getMessage(), e)`로 변경 —
     `GlobalExceptionHandler.handleException()`의 `log.error("Unexpected exception", e)` 패턴과
     동일하게 예외 객체 자체(스택트레이스)를 남긴다.
  7. **합성 username 중복 검증(D)**: `findOrCreateByKakao()` 신규 가입 분기에
     `memberRepository.existsByUsername("kakao_" + kakaoId)` 사전 검증 추가, 충돌 시
     `BusinessException(ErrorCode.DUPLICATE_USERNAME)`(기존 `signup()`과 동일한 코드 재사용,
     새 ErrorCode 불필요). 컨트롤러의 기존 catch(Exception) → `/login.html?error=kakao` 경로를
     그대로 탄다.

- 테스트 변경/추가:
  - `KakaoLoginTest.kakaoCallback_existingAccount_ignoresIntendedRole` — role 유지 검증은 남기고,
    리다이렉트가 `/?kakaoRoleMismatch=BUYER`인지 추가 검증.
  - `KakaoLoginTest.kakaoCallback_existingAccount_withoutExplicitRole_noMismatchSignal`(신규) —
    role 파라미터 없는 재로그인은 role이 달라도 안내 신호 없이 `/`로만 리다이렉트되는지 확인.
  - `KakaoLoginTest.kakaoCallback_synthesizedUsernameConflict_redirectsToError`(신규) — 일반
    회원가입으로 `kakao_{id}` username을 선점한 상태에서 같은 id로 카카오 신규 가입 시도 →
    `/login.html?error=kakao` 리다이렉트 + 회원 미생성 확인.
  - `AuthControllerTest.logout_thenReusingSameSession_isUnauthorized`(신규) — 로그아웃 후 같은
    `MockHttpSession`으로 `GET /api/auth/me` 재호출 시 401/UNAUTHORIZED 확인.

- 변경 파일:
  - `service/MemberService.java`, `dto/KakaoLoginResult.java`(신규),
    `controller/AuthController.java`, `common/filter/AuthPageCacheControlFilter.java`(신규)
  - `static/signup.html`, `static/index.html`, `static/js/main.js`
  - `test/.../controller/KakaoLoginTest.java`, `test/.../controller/AuthControllerTest.java`

- 로컬 실행 환경(이번 시도에서 준비):
  - 로컬 MySQL 8.4가 Windows 서비스로 등록돼 있지 않아 `mysqld.exe --defaults-file=...`로 수동
    기동(포트 3306, `gong9ri_db` 기존 데이터 재사용, `root`/`1234` 자격증명 기존
    `application.yaml` 기본값과 일치 확인).
  - Redis는 Docker Desktop을 띄우니 기존 `docker-compose` 컨테이너(`gong9ri-main-redis-1`)가
    자동으로 healthy 상태로 떠 있어 별도 기동 불필요(포트 6379).

- 결과: `./gradlew compileJava`, `./gradlew compileTestJava` 성공. `./gradlew test` 전체 통과
  — `BUILD SUCCESSFUL`, 테스트 결과 XML 합산 `tests=202, failures=0, errors=0`
  (`KakaoLoginTest` 6→8, `AuthControllerTest` 24→25 — 신규 3개 추가, 나머지 회귀 전부 그대로 통과).

## Evaluate — 2026-08-14  ✅ PASS

- 계산적 평가: 로컬 MySQL(포트 3306, Windows 서비스로 이미 기동 중 — `netstat`로 LISTENING 확인)과
  Redis(Docker, 포트 6379, `netstat`로 LISTENING 확인) 둘 다 떠 있는 상태에서 직접
  `./gradlew test --rerun-tasks`(캐시 우회, 강제 재실행)를 실행 — `BUILD SUCCESSFUL in 55s`.
  `build/test-results/test/*.xml`을 전부 합산해 실제로 재확인: `tests=202, failures=0, errors=0`
  (generator가 보고한 수치와 일치). `TEST-...KakaoLoginTest.xml`은 `tests="8"`,
  `TEST-...AuthControllerTest.xml`은 `tests="25"`로 개별 확인.
- 추론적 평가 — 계획 준수: `git diff --stat`으로 변경 파일이 계획/Generate 보고와 정확히 일치함을
  확인(추가 파일 `dto/KakaoLoginResult.java`, `common/filter/AuthPageCacheControlFilter.java` 포함
  총 9개, 그 외 파일 변경 없음). `MemberService.findOrCreateByKakao()` diff, `AuthController.
  kakaoCallback()`/`logout()` diff를 직접 읽고 확인: role 유지 로직(기존 회원이면 intendedRole 무시)은
  그대로이고 `KakaoLoginResult.roleMismatch()`만 추가됐음, `explicitRoleRequested`(세션에 role 저장
  여부)와 `roleMismatch()`를 함께 만족할 때만 `/?kakaoRoleMismatch=<role>`로 리다이렉트하고 그 외엔
  `/`로 감(코드 레벨에서 조건 정확히 확인). 스코프 제외 항목(액세스 토큰 null 검증, 동시성, 코드
  중복 리팩터링, OAuth 테이블 분리, 404 핸들러, 매직 스트링, 결제 테스트, 임시 비밀번호 해싱 비용)은
  diff 어디에도 손대지 않음 확인 — 스코프 이탈 없음.
- 추론적 평가 — 코드 컨벤션(`docs/code-convention.md`): 계층 분리(controller/service/dto) 준수,
  생성자 주입(`@RequiredArgsConstructor`, `final` 필드) 유지, `KakaoLoginResult`를 `dto` 패키지에
  올바르게 배치. 로깅: `System.out`/`printStackTrace` 없음, SLF4J만 사용. 다만 **경미한 컨벤션 이탈
  1건 발견**: `kakaoCallback()`의 `catch (Exception e)`가 `BusinessException`(이메일/username
  충돌 — 문서 기준 WARN 대상)과 진짜 예상 못 한 예외(문서 기준 ERROR 대상)를 구분하지 않고 전부
  `log.error(..., e)`로 남긴다 — `docs/code-convention.md` 로그 레벨 기준, `GlobalExceptionHandler`의
  기존 구분 패턴과 다름. 이번 수정이 해결하려 한 "스택트레이스 유실" 문제 자체는 확실히 고쳤고,
  레벨 세분화는 계획 문서(D 항목)에 명시된 스코프가 아니었으므로 **블로킹 이슈로 판단하지 않고
  알려진 한계로 기록**함(design.md에 반영).
- 추론적 평가 — 실제 동작 논리: `KakaoLoginTest`의 신규/변경 테스트 3개(role 유지+안내 신호,
  role 파라미터 없는 재로그인은 안내 없음, 합성 username 충돌)가 의도한 시나리오를 정확히
  검증하고 있음을 코드로 확인. `login.html`의 일반 "카카오로 로그인" 버튼에 `role` 파라미터가
  없음을 직접 확인(`grep`) — `parseRoleOrNull(null)` → 세션 미저장 → `explicitRoleRequested=false`
  → 안내 없음 경로가 실제로 이 버튼에 연결됨을 확인. 로그아웃: `SecurityConfig`가
  `HttpSessionSecurityContextRepository`를 쓰므로 `session.invalidate()`만으로 저장된
  `SecurityContext` 세션 속성도 함께 사라지고, `SecurityContextHolder.clearContext()`는 현재
  요청 스레드의 잔여 정보까지 정리 — 방어적으로 타당. 쿠키 만료(`Cookie("JSESSIONID", null),
  path=/, maxAge=0`)는 표준적인 쿠키 삭제 기법이라 일반적인 상황에서 브라우저에 반영된다. 다만
  **경미한 관찰 사항**: 만약 운영에서 세션 쿠키가 `Secure` 속성으로 발급되는 환경이 되면(현재는
  `application.yaml`에 명시적 쿠키 보안 설정이 없고 forwarded-header 처리도 없어 해당 없음)
  이 삭제 쿠키도 동일하게 `Secure`를 지정해야 일부 브라우저에서 확실히 덮어써진다 — 지금 이
  프로젝트 배포 방식에서는 해당하지 않아 블로킹 이슈 아님. `AuthPageCacheControlFilter`는
  `@Component`(Filter 구현체)라 Spring Boot가 서블릿 컨테이너에 자동 등록하므로 `SecurityConfig`
  수정 없이도 전체 요청에 적용됨을 확인 — 전용 자동 테스트는 없음(계획의 평가 기준에도 이 필터
  전용 테스트는 명시돼 있지 않았음).
- 실사용 화면 수동 확인((a) SELLER 계정으로 "구매자로 시작하기" 클릭 시 안내 노출,
  (b) 로그아웃 후 재로그인 흐름): 로컬 카카오 앱 재설정이 필요해 이번 Evaluate에서는 수행하지
  않음 — 계획 문서의 리스크 항목에 이미 "없으면 Mockito 기반 통합 테스트로만 검증"이라고 명시돼
  있어 통과 조건에서 이 부분은 자동 테스트로 대체 검증된 것으로 판단.
- 증거(테스트 기반 API 샘플 — `KakaoLoginTest`/`AuthControllerTest` 통과 결과에서 발췌):
  - `GET /api/auth/kakao/callback`(회원가입 시 role=BUYER로 가입된 계정이 role=SELLER로 재로그인
    시도) → `302 Found`, `Location: /?kakaoRoleMismatch=BUYER`(role은 BUYER로 유지, 안내 신호만 부착)
  - `GET /api/auth/kakao/callback`(role=SELLER로 가입된 계정이 role 파라미터 없이 재로그인) →
    `302 Found`, `Location: /`(안내 신호 없음, DB role은 SELLER 그대로)
  - `GET /api/auth/kakao/callback`(일반 회원가입으로 `kakao_666666`을 이미 쓰고 있는 상태에서
    같은 카카오 id로 신규 가입 시도) → `302 Found`, `Location: /login.html?error=kakao`,
    회원 미생성 확인
  - `POST /api/auth/logout`(로그인 상태) → `204 No Content`, 이어서 같은 세션으로
    `GET /api/auth/me` → `401 {"success":false,"code":"UNAUTHORIZED",...}`
- 판정: **PASS**. 계산적 평가(202/202 통과) + 추론적 평가(계획 준수, 스코프 이탈 없음, 컨벤션 이슈
  1건은 비블로킹) 모두 통과로 판단. `docs/dev/auth/social-login/design.md`,
  `docs/dev/auth/logout/design.md`, `docs/api/auth.md` 갱신 완료. `docs/dev/ongoing/
  kakao-login-session-fix.md`를 `docs/dev/auth/social-login/changes/
  001-kakao-login-session-fix.md`로 채번 이동.
