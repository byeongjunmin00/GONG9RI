# 헤더 로그인 상태 연동 (frontend/header-auth) — Design

## 개요

공통 헤더(`partials/header.html`)가 실제 로그인 상태(로그인 여부 + 역할 `BUYER`/`SELLER`)에 따라 다르게 보이도록 연동한다. 비로그인 시 기존과 동일하게 로그인/회원가입 버튼을 보여주고, 로그인 시 사용자 이름 + 로그아웃 버튼으로 전환한다. nav 링크(판매 물품 등록/판매자 마이페이지/구매자 마이페이지)는 **로그인한 역할과 일치할 때만 노출**하고, 비로그인이거나 역할이 다르면 숨긴다(`changes/002-nav-visibility.md`에서 "역할 무관 항상 노출·강조만" 방침을 뒤집은 결정 — 아래 "역할별 nav 표시" 참고). 이 헤더 표시 여부와 무관하게, 각 페이지 자체의 서버 401/403 사후 판정(URL 직접 접근 시)은 기존과 동일하게 유지된다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── partials/header.html      # #header-auth-guest / #header-auth-user 토글 구조, nav data-role 속성
├── js/
│   ├── include.js             # 삽입 완료 시 'gong9ri:includes-ready' 커스텀 이벤트 발행 (기존 로직은 불변)
│   └── header-auth.js         # 신규 — 이벤트 구독 → GET /api/auth/me → 헤더 갱신 / 로그아웃 처리
└── css/components.css         # 헤더 로그인 상태 관련 3개 규칙 추가
```

- 아래 10개 페이지 전부에 `<script src="/js/header-auth.js"></script>`를 `api.js` 다음·페이지 전용 스크립트(있으면) 앞에 추가: `index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html`.
- 백엔드 의존: `GET /api/auth/me`(`docs/dev/auth/me/design.md`).

## 데이터 연동

- **실행 시점 보장**: `include.js`의 `includeAll()`이 모든 `data-include` 삽입을 끝낸 뒤 `document`에 `gong9ri:includes-ready`를 정확히 1회 발행한다. `header-auth.js`는 이 이벤트를 구독해야만 동작을 시작한다(헤더 DOM이 아직 없는 시점에 실행되는 경합 방지).
- **로그인 상태 판정**: `Api.get('/auth/me')` 성공(200) → 로그인 상태로 간주, 응답 실패(401 등) → `.catch`에서 아무 것도 하지 않아 기존 비로그인 마크업을 그대로 유지한다(신규 에러 분기 없음).
- **헤더 토글**: 로그인 시 `#header-auth-guest`에 `hidden = true`, `#header-auth-user`에 `hidden = false`를 대입. 두 영역 모두 동일한 `.site-header__auth` 클래스를 쓰는 마크업이며 `innerHTML` 조작은 없다. 사용자 이름은 `#header-auth-user-name.textContent = member.name + '님'`로만 대입(XSS 방지).
- **역할별 nav 표시**: `.site-header__nav a[data-role]`(3개: 판매 물품 등록/판매자 마이페이지=`SELLER`, 구매자 마이페이지=`BUYER`)는 마크업 기본값이 `hidden`이다(비로그인 상태를 기본으로 간주). 로그인한 `member.role`과 일치하는 링크만 `hidden = false`로 노출하고 `nav-link--role-active` 클래스도 함께 추가한다. 역할이 다른 링크는 손대지 않아 기본 `hidden`이 유지된다. 별도 "메인" nav 링크는 없다 — 로고 자체가 이미 `/`로 가는 링크라 중복이고, 역할 링크가 전부 숨겨진 상태(비로그인 등)에서 "메인" 하나만 덩그러니 남는 게 어색해 애초에 만들지 않았다(`partials/header.html` 상단 주석 참고).
- **로그아웃**: `#header-auth-logout` 클릭 시 `Api.post('/auth/logout')` 성공 후 `window.location.reload()`(현재 페이지 새로고침). 실패는 콘솔 로그만 남기고 별도 UI 처리 없음.
- **로그인 상태 재사용 이벤트(이후 추가됨)**: `GET /api/auth/me` 호출이 끝나면(성공/실패 모두) `document`에 `gong9ri:auth-resolved` 커스텀 이벤트를 `{ detail: { loggedIn, member } }` 형태로 발행한다(성공: `loggedIn:true, member`, 실패: `loggedIn:false, member:null`). 다른 스크립트가 로그인 상태·역할을 재사용하려고 `/auth/me`를 중복 호출하지 않도록 하기 위한 확장이며, 기존 헤더 토글/nav 표시/로그아웃 로직은 그대로다. 현재 이 이벤트의 유일한 구독자는 `js/chat-widget.js`(`docs/dev/frontend/buyer-chatbot/design.md`)다.

## 규칙 / 검증

- **`[hidden]` 특이도 보정**: `layout.css`가 `.site-header__auth { display: flex; }`(특이도 0,1,0)를 선언하고 있어 네이티브 `[hidden]`이 무시된다. `components.css`에 `.site-header__auth[hidden] { display: none; }`(특이도 0,2,0)를 추가해 보정한다(`.btn[hidden]` 등 기존 패턴과 동일). nav 링크(`<a>`)는 `base.css`가 `display`를 선언하지 않아 이 보정이 필요 없다(네이티브 `[hidden]` 동작 그대로 사용).
- **서버 문자열은 `textContent`로만 대입** — `header-auth.js`에 `innerHTML` 사용 없음.
- **역할 불일치 시 접근은 헤더가 아니라 서버가 막는다** — nav에서 안 보여도 URL 직접 접근은 가능하며, 그 경우 각 페이지가 기존처럼 서버 401/403 응답으로 사후 판정한다(헤더 표시는 UX 편의일 뿐 접근 제어 수단이 아님).

## 관련 코드 위치

- `src/main/resources/static/js/header-auth.js` — 신규
- `src/main/resources/static/js/include.js` — 삽입 완료 이벤트 발행 추가
- `src/main/resources/static/partials/header.html` — 로그인 상태 토글 마크업
- `src/main/resources/static/css/components.css` — 헤더 로그인 상태 스타일 3개 규칙
- 위 10개 정적 HTML 페이지 — `<script>` 태그 추가
- 경위: `docs/dev/frontend/header-auth/changes/001-header-auth.md`(초기 "강조만" 구현), `changes/002-nav-visibility.md`(숨김으로 전환), 실행 로그: `docs/logs/frontend/header-auth/001-header-auth.md`, `002-nav-visibility.md`
- `gong9ri:auth-resolved` 이벤트 발행 확장의 경위/실행 로그는 이 파일을 소유한 `frontend/buyer-chatbot` 쪽에 있다: `docs/dev/frontend/buyer-chatbot/changes/001-buyer-chatbot-frontend.md`, `docs/logs/frontend/buyer-chatbot/001-buyer-chatbot-frontend.md`.
