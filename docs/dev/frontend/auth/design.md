# 로그인 페이지 + 회원가입 페이지 (frontend/auth) — Design

## 개요

GONG9RI의 로그인(`login.html`)·회원가입(`signup.html`) 페이지다. 두 페이지는 API 계약과 폼 컴포넌트를 공유하고 서로 강하게 이동 연결(로그인 실패 안내에 회원가입 링크, 회원가입 성공 시 로그인 페이지로 리다이렉트)되어 있어 하나의 기능(`frontend/auth`)으로 묶여 있다. 공통 디자인 시스템(`docs/dev/frontend/design-system/design.md`) 위에서 동작하며, React/Vue 등 프레임워크 없이 정적 HTML/CSS/JS로 작성됐다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── login.html               # 로그인 폼 (아이디/비밀번호) + 공통 에러·안내 영역
├── signup.html              # 회원가입 폼 (아이디/비밀번호/이름/이메일/회원유형) + 필드 에러 + 공통 에러 영역
└── js/
    ├── login.js              # POST /api/auth/login 호출, 성공 시 '/'로 리다이렉트(이후 `?redirect=` 파라미터 지원 추가)
    └── signup.js             # POST /api/auth/signup 호출, 성공 시 login.html?signup=success로 리다이렉트
```

- `css/components.css`에 `.form-alert`/`.form-alert--error`/`.form-alert--success`(공통 에러·안내 배너), `.role-select`/`.role-option`(회원 유형 토글 UI) 추가.
- `partials/header.html` 상단 주석만 갱신(로그인/회원가입이 실제 페이지임을 반영). `href="/login.html"`/`href="/signup.html"` 값은 design-system 단계부터 이미 맞아 있었다.
- 신규 CSS 파일 없음 — `css/tokens.css`, `base.css`, `layout.css`, `js/api.js`, `js/include.js`, 헤더/푸터 partial을 그대로 재사용.

## API 연동

- `POST /api/auth/signup` (`docs/api/auth.md`): body `{username, password, name, email, role}`(`role`: `BUYER`|`SELLER`, 기본 선택 `BUYER`). 성공(`201`) → `login.html?signup=success`. 실패: `DUPLICATE_USERNAME`(409)은 아이디 필드 에러(`#username-error`)에, `VALIDATION_FAILED`(400)는 공통 에러 영역(`#form-alert`)에 표시.
- `POST /api/auth/login`: body `{username, password}`. 성공(`200`, `Set-Cookie: JSESSIONID`) → 기본은 `/`로 리다이렉트하되, `?redirect=` 쿼리 파라미터(같은 출처 상대경로일 때만, 오픈 리다이렉트 방지)가 있으면 그 경로로 이동한다(이후 `feat(frontend): 로그인 후 원래 페이지로 복귀` 커밋에서 `login.js`의 `resolveRedirectTarget()`으로 추가됨). 실패: `VALIDATION_FAILED`/`LOGIN_FAILED`(401) 모두 공통 에러 영역에 서버 `message` 그대로 표시(필드별 구분 없음 — API가 원인 필드를 구분해 주지 않음).
- 두 페이지 모두 제출 전 최소한의 빈 값 체크만 클라이언트에서 하고, 실제 검증 기준(SSOT)은 서버 응답이다(문서화된 비밀번호/아이디 형식 정책이 없어 클라이언트가 규칙을 추측해 재구현하지 않는다).
- 사용자 입력값과 서버 응답 `message`는 전부 `textContent`로만 DOM에 대입해 XSS를 방지한다(`innerHTML` 미사용).

## 규칙 / 검증

- **헤더 로그인 상태 연동(이후 추가됨)**: 이 작업 시점엔 "현재 로그인한 사용자 조회" API가 없어(design-system 단계부터의 제약) 새로고침 후 로그인 상태를 재현할 방법이 없었고, 로그인 성공 후에도 헤더는 비로그인 고정 마크업을 그대로 보여줬으며 로그아웃 버튼/호출도 없었다. 이후 `GET /api/auth/me`와 `frontend/header-auth` 작업으로 실제 로그인 상태 연동 + 로그아웃 버튼이 추가됐다 — 상세: `docs/dev/frontend/header-auth/design.md`.
- **백엔드 로그아웃(이후 구현됨)**: 이 작업 시점엔 `docs/api/auth.md`에 `POST /api/auth/logout`이 문서화만 되어 있고 실제 백엔드 구현이 없었다(호출 시 500). 이후 `auth/logout` 작업에서 실제로 구현됐다 — 상세: `docs/dev/auth/logout/design.md`. 이 프론트 작업 자체는 로그아웃을 호출하지 않아 영향받지 않았다.
- **CSS `hidden` 속성 패턴**: `.form-alert`/`.form-error`는 의도적으로 자체 `display` 값을 선언하지 않아, `hidden` 속성의 브라우저 기본 동작(`display:none`)이 클래스에 덮이지 않는다(main-page 단계에서 겪은 `.btn[hidden]` specificity 버그의 재발 방지 패턴).

## 관련 코드 위치

- `src/main/resources/static/login.html`, `signup.html`, `js/login.js`, `js/signup.js` — 신규
- `src/main/resources/static/css/components.css` — `.form-alert*`/`.role-select`/`.role-option` 규칙 추가
- `src/main/resources/static/partials/header.html` — 상단 주석 갱신
- 경위: `docs/dev/frontend/auth/changes/001-auth.md`, 실행 로그: `docs/logs/frontend/auth/001-auth.md`
