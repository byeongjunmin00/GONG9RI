# 001-auth — 로그인 페이지 + 회원가입 페이지 (로그)

## Attempt 1 — 2026-08-07

- 시도:
  - 계획 문서(`docs/dev/ongoing/frontend-auth.md`) 태스크를 그대로 구현. 새 CSS 파일/새 JS 유틸 파일은 만들지 않고 기존 `css/tokens.css`·`base.css`·`layout.css`·`js/api.js`·`js/include.js`를 그대로 재사용, `css/components.css`에만 최소 보강.
  - `src/main/resources/static/login.html` 신규 작성:
    - `<head>`는 `index.html`/`design-system.html`과 동일한 폰트 CDN(Poppins/Pretendard) + `tokens/base/layout/components.css` 링크 구성을 그대로 재사용.
    - `<div data-include="header"></div>` / `<div data-include="footer"></div>`로 공통 헤더/푸터 partial 재사용.
    - 폼: `#login-form` 안에 `.form-group`/`.form-label`/`.form-input`으로 아이디(`#username`)·비밀번호(`#password`, `type="password"`) 입력, `.btn.btn-primary.btn-block` 제출 버튼(`#login-submit`), 회원가입 페이지로 가는 링크.
    - 폼 위에 공통 에러/안내 영역 `#form-alert.form-alert`(기본 `hidden`, `role="alert"`) 배치 — 로그인 실패(`VALIDATION_FAILED`/`LOGIN_FAILED`) 메시지와 회원가입 완료 안내(`?signup=success`)를 여기 표시.
    - 스크립트 로드 순서: `include.js` → `api.js` → `login.js`(신규).
  - `src/main/resources/static/signup.html` 신규 작성:
    - `login.html`과 동일한 `<head>`/헤더·푸터 재사용 구조.
    - 폼: 아이디(`#username`)·비밀번호(`#password`)·이름(`#name`)·이메일(`#email`, `type="email"`) 입력 + 회원 유형 선택(`.role-select` 안에 `role="radiogroup"`, `input[name="role"][value="BUYER"|"SELLER"]` 라디오 2개, 기본 `BUYER` 선택) + `.btn.btn-primary.btn-block` 가입 버튼(`#signup-submit`) + 로그인 페이지로 가는 링크.
    - 아이디 입력 필드 아래 `#username-error.form-error`(기본 `hidden`) — `DUPLICATE_USERNAME` 전용 표시 영역. 폼 위에 공통 에러 영역 `#form-alert.form-alert`(기본 `hidden`) — `VALIDATION_FAILED` 등 필드 특정 불가능한 에러 표시 영역.
    - 스크립트 로드 순서: `include.js` → `api.js` → `signup.js`(신규).
  - `src/main/resources/static/js/login.js` 신규 작성:
    - `#login-form` submit 시 `event.preventDefault()` 후, `#username`/`#password` 값이 비어 있으면(trim 후) UX 보조용으로 즉시 `form-alert--error`를 보여주고 API를 호출하지 않음(서버 규칙을 추측해 재구현하지 않고 "빈 값 제출 방지" 정도만).
    - 값이 있으면 `window.Api.post('/auth/login', { username, password })` 호출. 성공 시 `window.location.href = '/'`로 리다이렉트. 실패 시 `catch(err)`에서 `err.message`를 그대로 `#form-alert`에 `textContent`로 대입(코드별 분기 없음 — 계획대로 `VALIDATION_FAILED`/`LOGIN_FAILED` 둘 다 공통 영역에 서버 message 그대로 노출).
    - 페이지 로드 시 `URLSearchParams(window.location.search)`로 `signup=success` 쿼리를 확인해 있으면 `form-alert--success`로 가입 완료 안내를 미리 띄움(회원가입 성공 시 리다이렉트 대상과 연결).
    - 제출 중 중복 클릭 방지를 위해 `submitBtn.disabled = true` → 실패 시에만 다시 `false`로 되돌림(성공 시에는 페이지를 이동하므로 되돌릴 필요 없음).
  - `src/main/resources/static/js/signup.js` 신규 작성:
    - `#signup-form` submit 시 5개 값(`username`/`password`/`name`/`email`/`role`, `role`은 `form.querySelector('input[name="role"]:checked')`로 조회) 중 하나라도 비어 있으면 `#form-alert`에 안내(로그인과 동일한 최소 UX 체크 수준).
    - `window.Api.post('/auth/signup', { username, password, name, email, role })` 호출. 성공 시 `window.location.href = '/login.html?signup=success'`로 리다이렉트.
    - 실패 시 `err.code === 'DUPLICATE_USERNAME'`이면 `#username-error`에 `err.message`를 `textContent`로 표시하고 종료, 그 외(`VALIDATION_FAILED` 등)는 `#form-alert`에 표시 — 계획의 "에러 코드별 표시 위치 매핑"을 그대로 구현.
  - `src/main/resources/static/css/components.css`에 최소 보강 2블록 추가(기존 `.btn`/`.card`/`.badge`/`.form-*`/`.product-status`/`.load-more-wrap` 규칙은 수정하지 않음):
    - `.form-alert`/`.form-alert--error`/`.form-alert--success` — 공통 에러·안내 배너. `display` 속성은 의도적으로 선언하지 않아(배경색/패딩/폰트만 지정) `<div>`의 기본 `display: block`에 맡기고, `hidden` 속성이 브라우저 기본 `[hidden]{display:none}` 규칙과 충돌하지 않게 함 — main-page 단계에서 겪은 `.btn[hidden]` specificity 버그(클래스가 `display`를 직접 선언하면 `[hidden]`을 이겨버리는 문제)를 이번엔 애초에 `display`를 선언하지 않는 방식으로 재발 방지. 색상은 기존 토큰(`--color-coral`/`--color-success`/`--color-success-bg`)만 사용, `--color-danger`는 "미성사(중립)" 시맨틱이라 에러 배너 용도로 재사용하지 않고 코럴 계열(`.form-error`와 동일 색상 계열)로 통일.
    - `.role-select`(2열 그리드) / `.role-option`(라디오+라벨을 감싸는 토글형 카드, `accent-color`로 라디오 자체 색상만 브랜드 핑크로) / `.role-option:has(input:checked)`(선택된 옵션 강조 — `:has()` 미지원 브라우저에서도 기본 라디오 UI로 선택 자체는 항상 가능하고 강조 스타일만 빠지는 정도의 그레이스풀 디그레이드).
  - `partials/header.html` 상단 주석만 갱신 — 로그인/회원가입이 이제 실제 페이지임을 반영하고, 헤더 상태 미연동 이유("현재 로그인 사용자 조회" API 부재)를 명시. `href` 값 자체는 변경하지 않음(계획대로).
  - `js/api.js`, `js/include.js`, `css/tokens.css`, `css/base.css`, `css/layout.css`는 전혀 수정하지 않음(계획 전제 유지). 헤더 로그인 상태 연동 로직·로그아웃 버튼/호출은 추가하지 않음(계획 4항/리스크 섹션 준수).
  - `./gradlew compileJava` 실행 → `BUILD SUCCESSFUL`(`UP-TO-DATE`, 이번 작업에서 자바 소스 변경 없음).
  - 브라우저 수동 확인(`bootRun` 후 실제 회원가입/로그인 성공·실패 플로우, Network 탭 `Set-Cookie` 확인, 콘솔 에러 확인)은 이번 Generate 단계에서 수행하지 않음 — Evaluate 단계 몫으로 남김.

- 결과: **PASS** (계산적 평가 통과, 코드 레벨 추론적 평가 통과 — 브라우저 수동 확인은 평가 범위 밖으로 별도 확인 필요)

- 원인 / 상세:
  - **계산적 평가**
    - `./gradlew compileJava` → `BUILD SUCCESSFUL` (`UP-TO-DATE`, 자바 소스 변경 없음).
    - `./gradlew test` → `BUILD SUCCESSFUL` (MySQL 가동 중, 회귀 없음. Hikari 여러 풀 정상 종료 로그만 있고 실패 케이스 없음).
  - **추론적 평가 — `git status`/`git diff` 기준 변경 파일이 계획 태스크와 일치**
    - 신규: `login.html`, `signup.html`, `js/login.js`, `js/signup.js`, `docs/dev/ongoing/frontend-auth.md`, `docs/logs/frontend/auth/`.
    - 수정: `css/components.css`(+53줄, 신규 블록만 추가), `partials/header.html`(+7/-2, 상단 주석만).
    - `js/api.js`, `js/include.js`, `css/tokens.css`, `css/base.css`, `css/layout.css`는 `git status`에 전혀 나타나지 않음 -> 손대지 않았음이 확인됨.
  - **login.html / login.js**
    - `window.Api.post('/auth/login', { username, password })` 호출 확인.
    - 성공 시 `window.location.href = '/'` 리다이렉트 확인.
    - 실패 시 `catch(err)`에서 `err.message`(서버 `message`, `api.js`가 실패 응답의 `code`/`message`를 `Error`에 실어 던짐 — 계약과 일치)를 필드별 매핑 없이 공통 영역 `#form-alert`에 표시 — 계획대로 `VALIDATION_FAILED`/`LOGIN_FAILED` 구분 없음.
  - **signup.html / signup.js**
    - `window.Api.post('/auth/signup', { username, password, name, email, role })` 호출 확인 — 필드명이 `docs/api/auth.md` 요청 body와 정확히 일치.
    - `role` 선택 UI: `role="radiogroup"` 안에 `input[name="role"][value="BUYER"|"SELLER"]` 라디오 2개, 기본 `BUYER` 선택 — 존재 확인.
    - 성공 시 `window.location.href = '/login.html?signup=success'`로 쿼리스트링 포함 리다이렉트 확인.
    - `err.code === 'DUPLICATE_USERNAME'`이면 `#username-error`(아이디 필드 에러 영역)에, 그 외(`VALIDATION_FAILED` 등)는 `#form-alert`(공통 에러 영역)에 매핑 — 계획과 일치.
  - **XSS 방지**
    - `login.js`의 `showAlert()`, `signup.js`의 `showAlert()`/`showUsernameError()` 모두 `alertEl.textContent = text` / `usernameErrorEl.textContent = text` 형태로 대입 — `innerHTML` 문자열 조립 없음. 서버 `message`, 클라이언트 안내 문구 모두 `textContent` 경로로만 렌더링됨.
  - **헤더 로그인 상태 연동 없음**
    - `login.js`/`signup.js` 전체를 확인한 결과 헤더 DOM(`data-include="header"` 하위 요소)을 참조하거나 로그아웃 호출을 추가한 코드가 없음 — 계획 4항("헤더 연동 없음") 그대로 준수.
  - **`partials/header.html`**
    - `git diff` 확인 결과 변경은 상단 HTML 주석(로그인/회원가입이 이제 실제 페이지라는 설명, 헤더 미연동 이유 설명)뿐이고, `href="/login.html"` / `href="/signup.html"` 값 자체는 변경되지 않음.
  - **범위 이탈 없음**: `js/api.js`, `js/include.js`, `css/tokens.css`, `css/base.css`, `css/layout.css` 미수정 확인(위 `git status` 근거).
  - **`docs/code-convention.md` 준수 여부**: 해당 문서는 Java/Spring 계층(controller/service/repository 등) 기준 규칙이라 이번 정적 프론트엔드 전용 변경에는 대부분 해당 사항 없음(자바 코드 변경 자체가 없음). 위반 사항 없음.
  - **`docs/api/auth.md` 계약 일치**: signup 요청 필드(`username`/`password`/`name`/`email`/`role`)와 에러 코드(`VALIDATION_FAILED`/`DUPLICATE_USERNAME`), login 요청 필드(`username`/`password`)와 에러 코드(`VALIDATION_FAILED`/`LOGIN_FAILED`) 모두 코드와 정확히 일치.

- 증거:
  - `compileJava` 출력: `BUILD SUCCESSFUL in 1s`, `1 actionable task: 1 up-to-date`.
  - `test` 출력: `BUILD SUCCESSFUL in 19s`, `5 actionable tasks: 2 executed, 3 up-to-date` (실패 케이스 0건).
  - `git status --porcelain`:
    ```
     M src/main/resources/static/css/components.css
     M src/main/resources/static/partials/header.html
    ?? docs/dev/ongoing/frontend-auth.md
    ?? docs/logs/frontend/auth/
    ?? src/main/resources/static/js/login.js
    ?? src/main/resources/static/js/signup.js
    ?? src/main/resources/static/login.html
    ?? src/main/resources/static/signup.html
    ```
  - `login.js` 핵심부:
    ```js
    window.Api.post('/auth/login', { username: username, password: password })
      .then(function () { window.location.href = '/'; })
      .catch(function (err) {
        submitBtn.disabled = false;
        var message = (err && err.message) || '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.';
        showAlert(message, 'error'); // showAlert 내부: alertEl.textContent = text (innerHTML 미사용)
      });
    ```
  - `signup.js` 핵심부:
    ```js
    window.Api.post('/auth/signup', { username, password, name, email, role })
      .then(function () { window.location.href = '/login.html?signup=success'; })
      .catch(function (err) {
        if (err && err.code === 'DUPLICATE_USERNAME') { showUsernameError(message); return; }
        showAlert(message); // 둘 다 textContent 대입
      });
    ```
  - `partials/header.html` diff: `href` 라인은 diff에 나타나지 않음(주석 블록만 `+7/-2`).
  - **브라우저 수동 확인(부트런 후 실제 회원가입/로그인 성공·실패 플로우, `Set-Cookie` 헤더, 콘솔 에러 없음)은 이 Evaluate 단계의 역할 밖 — 호출자가 별도로 수행해야 함.**

## Attempt 2 — 2026-08-07 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 로컬 MySQL(기존 가동 중) + 임시 `redis:7` 컨테이너(`gong9ri-eval-redis`)로 `bootRun` 기동 후 `signup.html`/`login.html`을 실제로 조작.
  - 회원가입: `evaltester1`(SELLER) 정상 가입 → 같은 아이디로 재가입 시도(중복) → 비밀번호를 비운 채 제출(필수값 누락).
  - 로그인: 방금 가입한 계정으로 틀린 비밀번호 → 올바른 비밀번호 → 아이디만 입력하고 비밀번호를 비운 채 제출.
  - 로그인 성공 후 세션이 실제로 유효한지, `document.cookie`가 아닌 방법으로 확인(아래 "증거" 참고).
  - 확인 후 테스트 계정(`evaltester1`, `evaltester3`)과 Redis 컨테이너를 정리(삭제).
- 결과: ✅ **PASS** (버그 없음, 계획 문서의 "평가(통과) 기준" 항목 전부 충족). 이번 작업과 무관한 백엔드 기존 결함 1건을 확인 과정에서 발견(아래 "참고" 참고, 이번 작업 범위 밖이라 수정하지 않음).
- 원인: (실패 없음 — 해당 없음)
- 증거:
  - **회원가입 성공**: `POST /api/auth/signup` → `201`. `login.html?signup=success`로 리다이렉트, "회원가입이 완료됐습니다. 로그인해주세요." 안내(`.form-alert--success`) 표시.
  - **회원가입 실패 - 중복 아이디**: 같은 아이디로 재가입 → `POST /api/auth/signup` → `409`. `#username-error`에 "이미 존재하는 아이디입니다." 표시(`display: block`, `.form-alert`는 계속 `hidden`/`display:none` 유지 — 필드별 매핑이 공통 영역과 섞이지 않음 확인).
  - **회원가입 실패 - 필수값 누락**(비밀번호 비움): 클라이언트 최소 체크가 서버 호출 전에 막아 `#form-alert`에 "모든 항목을 입력해주세요." 표시. Network 탭 확인 결과 이 케이스는 서버에 요청이 전송되지 않음(직전 두 건만 `signup` 요청으로 기록됨) — "클라이언트에서 막든 서버 응답으로 막든" 기준 충족.
  - **로그인 실패 - 비밀번호 불일치**: `POST /api/auth/login` → `401`. `#form-alert`에 "아이디 또는 비밀번호가 일치하지 않습니다." 표시.
  - **로그인 성공**: `POST /api/auth/login` → `200` 직후 `/`로 리다이렉트. `document.cookie`에서 `JSESSIONID`가 보이지 않는 것은 **정상**(HttpOnly 쿠키라 JS로 읽을 수 없음, 오히려 보안상 올바른 동작) — 대신 세션 인증이 필요한 `GET /api/seller/mypage/products`(SELLER 전용, `docs/api/mypage.md`)를 같은 브라우저 세션에서 호출해 `200 {"success":true,"data":[]}`을 받아 **세션 쿠키가 실제로 발급·인식되고 있음을 간접 확인**.
  - **로그인 실패 - 필수값 누락**(비밀번호 비움): `#form-alert`에 "아이디와 비밀번호를 모두 입력해주세요." 표시, 클라이언트 단계에서 차단.
  - **모바일 뷰(375×812)**: `login.html`/`signup.html` 둘 다 `scrollWidth === clientWidth`(가로 스크롤 없음), `signup.html`의 `.role-select` 2열 그리드가 `165.5px 165.5px`로 정상 축소.
  - **콘솔**: 새로 발생한 에러 없음(남아있던 콘솔 메시지는 이전 세션의 main-page Redis 미기동 시점 것으로, 이번 auth 플로우 중 신규 에러 아님).
  - **참고(이번 작업 범위 밖, 별도 이슈로만 기록)**: 세션 확인 과정에서 `POST /api/auth/logout`을 호출했더니 `500`(`NoResourceFoundException: No static resource api/auth/logout`)이 발생 — `docs/api/auth.md`에는 문서화돼 있으나 실제로는 `src/main/java`에 해당 엔드포인트가 구현돼 있지 않음(코드 그레이프 결과 `logout` 매핑 없음). 이번 login/signup 작업은 로그아웃 버튼/호출을 만들지 않아(계획 4항) 이 결함의 영향을 받지 않지만, 문서-코드 불일치이므로 다음에 로그아웃 관련 작업을 할 때 참고할 것.
  - 평가 종료 후 테스트 계정(`evaltester1`, `evaltester3`)과 임시 Redis 컨테이너 정리 완료.
