# 001-header-auth — 헤더 로그인 상태 연동 (`auth/me` + `frontend/header-auth`) (로그)

## Attempt 1 — 2026-08-10

- 시도:
  - 계획 문서(`docs/dev/ongoing/header-login-sync.md`) 태스크를 그대로 구현. "확인 필요" 3항목은 사전에 확정된 대로 반영: (1) 역할별 nav 링크는 숨기지 않고 강조만, (2) 로그아웃 후 현재 페이지 새로고침, (3) 기존 401/403 사후 판정 흐름 유지.

  - **백엔드**
    - `src/main/java/com/gong9ri/gong9ri/controller/AuthController.java`에 `GET /me` 추가. `docs/api/auth.md`에 이미 작성된 계약대로 `MemberResponse.from(principal.getMember())`를 재사용(신규 DTO 없음), `@AuthenticationPrincipal MemberUserDetails principal` 파라미터로 현재 인증 주체를 받음. `SecurityConfig`는 손대지 않음 — 기존 `.anyRequest().authenticated()`가 이 엔드포인트를 자연스럽게 커버(미인증 시 `ApiAuthenticationEntryPoint`가 401 `UNAUTHORIZED`를 반환하는 것은 기존 `logout_unauthorized` 테스트가 이미 증명하는 패턴과 동일).
    - `src/test/java/com/gong9ri/gong9ri/controller/AuthControllerTest.java`에 `me_success`(로그인 후 세션으로 `GET /api/auth/me` → 200 + `memberId`/`username`/`name`/`role` 검증)와 `me_unauthorized`(세션 없이 호출 → 401 + `UNAUTHORIZED`) 추가. 기존 `login_success`/`logout_success`가 쓰는 Given(회원가입 헬퍼 `signup()`)→로그인→세션 추출 패턴과 `logout_unauthorized`의 401 검증 패턴을 그대로 따름.

  - **프론트엔드**
    - `src/main/resources/static/js/include.js`: `includeAll()`이 반환하는 `Promise.all(...)`에 `.then(...)`을 추가해, 모든 `data-include` 삽입이 끝난 시점에 `document.dispatchEvent(new CustomEvent('gong9ri:includes-ready'))`를 정확히 1회 발행하도록 확장. 기존 `loadInclude`/`includeAll` 삽입 로직 자체(fetch, innerHTML 대입, 에러 처리)는 변경하지 않음.
    - 신규 `src/main/resources/static/js/header-auth.js`: `gong9ri:includes-ready` 이벤트를 구독해 헤더 DOM이 준비된 뒤에만 동작. `window.Api.get('/auth/me')` 호출 → 성공 시 `applyLoggedInState(member)`가 `#header-auth-guest`를 숨기고(`hidden = true`) `#header-auth-user`를 노출(`hidden = false`), `#header-auth-user-name`에 `member.name + '님'`을 `textContent`로만 대입, `.site-header__nav a[data-role]` 중 로그인한 `member.role`과 일치하는 링크에만 `nav-link--role-active` 클래스 추가(링크 자체는 숨기지 않음). 실패(401 등)는 `.catch`에서 아무 것도 하지 않아 기존 비로그인 마크업을 그대로 유지. `bindLogout()`이 `#header-auth-logout` 클릭 시 `Api.post('/auth/logout')` 후 성공하면 `window.location.reload()`(현재 페이지 새로고침), 실패는 콘솔 로그만.
    - `partials/header.html`: 로그인/회원가입 버튼 영역(`#header-auth-guest`, 기존 `site-header__auth` 클래스 유지, 기본 노출)과 신규 로그인 상태 영역(`#header-auth-user`, 동일 `site-header__auth` 클래스 재사용 + 기본 `hidden` 속성, 내부에 `#header-auth-user-name` span과 `#header-auth-logout` 버튼)으로 분리. nav 링크 중 "판매 물품 등록"/"판매자 마이페이지"에 `data-role="SELLER"`, "구매자 마이페이지"에 `data-role="BUYER"` 속성만 추가(마크업 순서·href·문구는 그대로, 숨김 처리 없음). 상단 주석을 "헤더 로그인 상태 미연동" 서술에서 실제 연동 방식(이벤트 구독→`/auth/me`→토글, nav는 강조만) 설명으로 교체.
    - `css/components.css`에 3개 규칙 추가: `.site-header__auth[hidden] { display: none; }`(layout.css가 `.site-header__auth`에 `display: flex`를 선언하고 있어 네이티브 `[hidden]`이 무시되는 것을 방지 — 이번 세션에서 반복된 `.btn[hidden]`/`.product-detail[hidden]`/`.revenue-cards[hidden]`과 동일한 보정 패턴), `.header-auth-user__name`(이름 표시 텍스트 스타일), `.site-header__nav a.nav-link--role-active`(로그인한 역할 nav 링크 강조 — `.site-header__nav a`보다 높은 특이도로 재정의). `tokens.css`/`base.css`/`layout.css`/`js/api.js`는 전혀 수정하지 않음.
    - 아래 10개 페이지에 `<script src="/js/header-auth.js"></script>`를 `api.js` 바로 다음, 페이지 전용 스크립트(있으면) 바로 앞에 추가: `index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html`(이 페이지는 페이지 전용 스크립트가 없어 `api.js` 다음에 추가). 각 페이지의 기존 전용 스크립트 파일(`main.js`/`login.js`/`signup.js`/`product.js`/`checkout.js`/`seller-product-new.js`/`seller-product-edit.js`/`seller-mypage.js`/`buyer-mypage.js`)은 내용을 전혀 수정하지 않음.
    - 서버가 내려주는 문자열(사용자 이름)은 `header-auth.js` 전체에서 `textContent`로만 대입, `innerHTML` 사용 없음(코드에 `innerHTML` 호출 자체가 없음).

  - `./gradlew compileJava` → `BUILD SUCCESSFUL`.
  - `./gradlew test`(도커 `gong9ri-main-mysql-1`/`gong9ri-main-redis-1` 기동 상태에서 실행) → `BUILD SUCCESSFUL`, `AuthControllerTest` 12개 테스트 전부 통과(`tests="12" failures="0" errors="0"`, 신규 `me_success`/`me_unauthorized` 포함).
  - 브라우저 수동 확인(`bootRun` 후 로그인/로그아웃/역할별 강조 실측)은 이번 Generate 단계에서 수행하지 않음 — Evaluate 단계 몫으로 남김.

- 결과: **PASS**.

- 원인(판정 근거):
  - 계산적 평가: 도커 `gong9ri-main-mysql-1`/`gong9ri-main-redis-1` 이미 기동 상태(healthy) 확인 후 별도 조치 없이 진행. `./gradlew compileJava` → `BUILD SUCCESSFUL`. `./gradlew test --rerun`(캐시 무시, 전체 스위트 강제 재실행) → `BUILD SUCCESSFUL`, 전체 15개 테스트 클래스 전부 `failures="0" errors="0"`(`AuthControllerTest` 12개 포함, 신규 `me_success`/`me_unauthorized` 통과).
  - 추론적 평가(파일 대조 결과, 모두 계획·서술 그대로):
    - `AuthController.java`: `GET /me`가 `@AuthenticationPrincipal MemberUserDetails principal` → `MemberResponse.from(principal.getMember())` 재사용, 신규 DTO/`ErrorCode` 없음. `git status`상 `SecurityConfig.java`는 미수정.
    - `AuthControllerTest.java`: `me_success`(로그인 세션으로 200 + `memberId`/`username`/`name`/`role` 검증), `me_unauthorized`(세션 없이 401 + `UNAUTHORIZED`) 존재, 기존 `signup()`/세션 추출/401 검증 패턴과 일치.
    - `js/include.js`: diff 확인 결과 기존 `loadInclude`/`includeAll`의 fetch·삽입·에러 처리 로직은 변경 없이, `includeAll()`의 `Promise.all(...)`에 `.then()`을 이어붙여 `document.dispatchEvent(new CustomEvent('gong9ri:includes-ready'))`만 추가.
    - `js/header-auth.js`: `gong9ri:includes-ready` 구독 → `Api.get('/auth/me')` → 성공 시 `#header-auth-guest`/`#header-auth-user`를 `hidden` 불리언 속성으로 토글(둘 다 같은 `.site-header__auth` 마크업, 실제 `display:none`/`innerHTML` 조작 없음), nav 링크는 `data-role` 일치 시 `nav-link--role-active` 클래스만 추가(링크 제거/숨김 없음 — 코드에 `remove()`/`style.display`/`hidden` 대입이 nav 링크에는 없음을 확인). 실패는 `.catch`에서 아무 동작 없음(비로그인 마크업 유지). 로그아웃은 `Api.post('/auth/logout')` 성공 후 `window.location.reload()`. 사용자 이름은 `nameEl.textContent = member.name + '님'`로만 대입, 파일 전체에 `innerHTML` 없음.
    - `partials/header.html`: `#header-auth-guest`(기본 노출)/`#header-auth-user`(기본 `hidden`)로 분리, nav 4개 링크(`메인` 제외 3개에 `data-role`)는 숨김 마크업 없이 항상 렌더링. 상단 주석이 "미연동" 서술에서 실제 연동 방식 설명으로 교체됨.
    - `[hidden]` 특이도 보정: `layout.css`에 `.site-header__auth { display: flex; ... }`(특이도 0,1,0)가 이미 있어 네이티브 `[hidden]`을 이긴다 — `components.css`에 `.site-header__auth[hidden] { display: none; }`(특이도 0,2,0, 속성 선택자로 클래스 선택자를 앞섬)를 추가해 실제로 이 특이도 함정을 보정했음을 직접 확인(이번 세션에서 반복된 `.btn[hidden]` 패턴과 동일).
    - 10개 페이지(`index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html`) 전부에서 `grep`으로 `<script src="/js/header-auth.js">`가 `api.js` 바로 다음·페이지 전용 스크립트(있는 경우) 바로 앞에 존재함을 확인. 빠진 페이지 없음.
    - 페이지 전용 JS(`main.js`/`product.js`/`checkout.js`/`seller-mypage.js`/`buyer-mypage.js`/`seller-product-new.js`/`seller-product-edit.js`), `js/api.js`, `css/tokens.css`, `css/base.css`, `css/layout.css`, `config/SecurityConfig.java`에 대해 `git diff --stat`이 모두 빈 출력 — 전혀 수정되지 않았음을 확인.
    - `login.html`/`signup.html`/`design-system.html` diff는 `<script src="/js/header-auth.js">` 한 줄 추가만 있고 다른 변경 없음.
    - `docs/api/auth.md`에 추가된 `GET /api/auth/me` 계약(응답 필드·401 `UNAUTHORIZED`)이 실제 컨트롤러·테스트 구현과 정확히 일치.

- 증거(API 샘플, `TEST-com.gong9ri.gong9ri.controller.AuthControllerTest.xml`의 system-out 발췌):
  ```
  GET /api/auth/me -> 200 (3ms)   # 로그인 세션 보유 상태 (me_success)
  GET /api/auth/me -> 401 (5ms)   # 세션 없음 상태 (me_unauthorized)
  ```
  전체 testsuite 요약: `tests="12" skipped="0" failures="0" errors="0"`(`AuthControllerTest`), 다른 14개 테스트 클래스도 전부 `failures="0" errors="0"`.

- 남은 범위(이 Evaluate가 다루지 않음, `docs/workflow/evaluate-guide.md`상 Evaluate 역할 밖): `./gradlew bootRun` 기동 후 브라우저로 로그인/로그아웃/역할별 강조 실측은 호출자(사용자) 몫으로 남김 — 계획 문서의 "평가(통과) 기준" 중 브라우저 실측 항목은 이 Evaluate에서 수행하지 않았다.

## Attempt 2 — 2026-08-10 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 도커 MySQL/Redis + `bootRun`으로 구매자(`hdrbuyer1`)/판매자(`hdrseller1`) 계정을 생성해 로그인/로그아웃 전 과정을 확인.
  - 비로그인 헤더 → BUYER 로그인 후 헤더 전환+강조 → 로그아웃(새로고침) → SELLER 로그인 후 헤더 전환+강조 → 서브디렉토리 페이지(`seller/mypage.html`)·`design-system.html`에서도 동일하게 동작하는지 → 모바일 뷰 순으로 확인. 확인 후 테스트 계정 2개 정리.
- 결과: ✅ **PASS** (버그 없음)
- 원인: (해당 없음)
- 증거:
  - **비로그인**: `#header-auth-guest` 표시(`display:flex`), `#header-auth-user` 숨김(`hidden=true`/`display:none` — `.site-header__auth[hidden]` 보정 규칙이 실제로 작동함을 확인).
  - **BUYER 로그인**: 로그인 성공 후 `/`로 리다이렉트된 페이지에서 즉시 `#header-auth-user` 표시, "HDR구매자1님" 이름 노출, "구매자 마이페이지" 링크에만 `nav-link--role-active` 클래스, 나머지 3개 nav 링크(메인/판매 물품 등록/판매자 마이페이지)는 클래스 없이 그대로 유지되면서도 전부 `display !== 'none'`(숨김 없음, 강조만 — 계획 원칙 준수).
  - **로그아웃**: 로그아웃 버튼 클릭 → 페이지 새로고침 → 헤더가 다시 비로그인 상태로 정상 복귀.
  - **SELLER 로그인**: "판매 물품 등록"/"판매자 마이페이지" 두 링크 모두 `nav-link--role-active`, "구매자 마이페이지"는 비활성 클래스 — 역할별 강조가 여러 링크에도 올바르게 적용됨.
  - **서브디렉토리/기타 페이지 동작**: `seller/mypage.html`(서브디렉토리)과 `design-system.html`에서도 각각 "HDR판매자1님" 이름이 정상 표시됨 — 10개 페이지 전부에 스크립트가 실제로 동작함을 대표 샘플로 확인.
  - **콘솔**: 이번 헤더 로그인 상태 전환 자체로 인한 새 에러 없음(남아있던 401/403 로그는 이전 seller-mypage/buyer-mypage 테스트 세션의 잔여 메시지, `/auth/me`가 비로그인 시 401을 받는 것은 의도된 동작).
  - **모바일(375×812)**: `scrollWidth === clientWidth`(가로 스크롤 없음).
  - 평가 종료 후 테스트 계정(`hdrbuyer1`, `hdrseller1`) 정리 완료.
