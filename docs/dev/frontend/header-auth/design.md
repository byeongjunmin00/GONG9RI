# 헤더 로그인 상태 연동 (frontend/header-auth) — Design

## 개요

공통 헤더(`partials/header.html`)가 실제 로그인 상태(로그인 여부 + 역할 `BUYER`/`SELLER`)에 따라 다르게 보이도록 연동한다. 비로그인 시 기존과 동일하게 로그인/회원가입 버튼을 보여주고, 로그인 시 사용자 이름 + 로그아웃 버튼으로 전환한다. nav 링크(판매 물품 등록/판매자 마이페이지/구매자 마이페이지)는 로그인 여부·역할과 무관하게 항상 전부 노출하는 기존 프로젝트 원칙을 유지하고, 로그인한 역할에 해당하는 링크에만 시각적 강조를 준다.

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
- **역할별 nav 강조**: nav 링크는 숨기지 않는다(항상 노출, 기존 401/403 사후 판정 원칙 유지). `.site-header__nav a[data-role]` 중 로그인한 `member.role`과 일치하는 링크에만 `nav-link--role-active` 클래스를 추가한다.
- **로그아웃**: `#header-auth-logout` 클릭 시 `Api.post('/auth/logout')` 성공 후 `window.location.reload()`(현재 페이지 새로고침). 실패는 콘솔 로그만 남기고 별도 UI 처리 없음.

## 규칙 / 검증

- **`[hidden]` 특이도 보정**: `layout.css`가 `.site-header__auth { display: flex; }`(특이도 0,1,0)를 선언하고 있어 네이티브 `[hidden]`이 무시된다. `components.css`에 `.site-header__auth[hidden] { display: none; }`(특이도 0,2,0)를 추가해 보정한다(`.btn[hidden]` 등 기존 패턴과 동일).
- **서버 문자열은 `textContent`로만 대입** — `header-auth.js`에 `innerHTML` 사용 없음.
- **역할 무관 nav 원칙 유지** — 로그인/역할과 무관하게 링크는 항상 클릭 가능하고 최종 판정은 서버 401/403(기존 페이지들과 동일).

## 관련 코드 위치

- `src/main/resources/static/js/header-auth.js` — 신규
- `src/main/resources/static/js/include.js` — 삽입 완료 이벤트 발행 추가
- `src/main/resources/static/partials/header.html` — 로그인 상태 토글 마크업
- `src/main/resources/static/css/components.css` — 헤더 로그인 상태 스타일 3개 규칙
- 위 10개 정적 HTML 페이지 — `<script>` 태그 추가
- 경위: `docs/dev/frontend/header-auth/changes/001-header-auth.md`, 실행 로그: `docs/logs/frontend/header-auth/001-header-auth.md`
