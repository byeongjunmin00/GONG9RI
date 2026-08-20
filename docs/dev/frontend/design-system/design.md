# 프론트엔드 공통 레이아웃 & 디자인 시스템 (frontend/design-system) — Design

## 개요

GONG9RI 백엔드 REST API 위에 정적 HTML/CSS/JS 기반 프론트엔드를 얹기 위한 **공통 레이아웃 + 디자인 시스템**이다. React/Vue 등 프레임워크·빌드 도구는 쓰지 않고, `src/main/resources/static/` 하위에 정적 파일을 두어 Spring Boot 기본 정적 리소스 서빙을 그대로 사용한다. 개별 기능 페이지(메인/로그인/회원가입/상세/결제/마이페이지 등)는 이 산출물 위에서 별도 작업으로 진행하며, 이 문서는 그 개별 페이지가 재사용할 토큰·레이아웃·컴포넌트 스타일과 헤더/푸터 partial, 공통 fetch 래퍼 뼈대, 그리고 이를 눈으로 확인할 쇼케이스 페이지(`design-system.html`)를 다룬다.

- **톤앤무드(2단계 개편 완료)**: 최초(001)엔 오프화이트 배경 + 오렌지/코럴/핑크/퍼플 웜 그라디언트의 "인스타 인플루언서" 톤이었으나, 무신사(대형 패션 이커머스) 톤을 참고해 **화이트/라이트 그레이 무채색 베이스 + 다크 세이지 그린(어두운 쑥색) 브랜드 포인트**로 전면 개편했다(002). 웜 그라디언트 계열 색상은 전부 제거됐다.
- **레이아웃 구조는 002에서 변경하지 않았다** — 헤더 1단 구조, 기존 그리드 컬럼 수, 푸터 2블록, 상세 페이지 세로 1컬럼 구조가 그대로다. 헤더 2단 구조화·그리드 밀도 재정의·푸터 다단화·상세 페이지 2컬럼 배치 등은 아직 계획에 없다(향후 별도 후속 계획 대상).
- `index.html`은 아직 없다 — 와이어프레임상 `/`(메인 페이지) 자리이므로 이후 메인 페이지 작업에서 만든다.
- `js/api.js`는 아직 실제로 호출하는 개별 페이지가 없는 뼈대 상태다. 향후 개별 페이지가 그대로 재사용한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── design-system.html      # 색상 팔레트/브랜드 마크/타이포그래피/버튼/카드/폼/뱃지/검색창/찜하기버튼 쇼케이스 페이지
├── css/
│   ├── tokens.css           # 디자인 토큰(색상/타이포/스페이싱/라운드/그림자, 뱃지 시맨틱 컬러)
│   ├── base.css             # 리셋/전역 스타일, `.text-gradient` 유틸(현재는 단일 강조색), 포커스 스타일
│   ├── layout.css           # 모바일 우선 헤더/푸터/컨테이너/카드 그리드 (색상 개편 이후에도 미변경)
│   └── components.css       # 버튼(primary/secondary/ghost)/카드/뱃지/폼/검색 입력창/찜하기 버튼 컴포넌트,
│                             # + 헤더 다크 세이지 배경·로고 이미지 크기·nav 대비 보정(캐스케이드 오버라이드, 아래 참고)
├── images/
│   ├── logo-icon.png         # 라쿤 얼굴 모양 "9" 라인아트 로고, 회색 버전(밝은 배경용) — 투명 PNG, 쇼케이스 등에서 원본 그대로 사용
│   ├── logo-icon-inline.png  # 위 원본의 여백을 크롭한 버전(219×305) — 헤더 워드마크 "9" 자리에 마스킹해서 삽입할 때 사용
│   └── logo-icon-white.png   # 동일 로고, 알파 유지한 채 불투명 픽셀만 흰색으로 바꾼 버전 — 현재 미사용(예비 자산)
├── js/
│   ├── include.js           # `data-include="header|footer"` 컨테이너에 `/partials/{name}.html`을 fetch해 삽입하는 유틸
│   └── api.js                # `window.Api.get/post/put/patch/del` 공통 fetch 래퍼 (base `/api`, `credentials: 'same-origin'`, `docs/api/README.md` 공통 응답 포맷 파싱)
└── partials/
    ├── header.html           # 워드마크 "GONG9RI"의 "9" 자리에 로고 아이콘을 인라인 삽입(`<a href="/">`로 감싸 메인 이동) + 내비게이션 + 로그인/회원가입 버튼
    └── footer.html           # 브랜드 텍스트 로고(`.text-gradient`로 "9" 강조, 새 단일 브랜드색 자동 반영) + 링크 + 사업자 정보(placeholder) + copyright
```

## 색상 토큰 (현재 상태, `tokens.css`)

- 무채색 베이스: `--color-bg: #F5F6F3`(라이트 그레이), `--color-surface: #FFFFFF`, `--color-surface-alt: #EEF0EA`, `--color-border: #E1E4DC`.
- 텍스트: `--color-text: #262B22`(다크 세이지 그린 계열), `--color-text-muted: #6E7568`, `--color-text-on-brand: #FFFFFF`.
- 브랜드 강조: `--color-brand: #445940`(버튼/링크/포인트 텍스트), `--color-brand-dark: #333F30`(hover 등).
- 폼 에러 전용: `--color-error: #B3483C`(상태 뱃지 `--color-danger`와는 별개 — 뱃지는 의도적으로 중립색 유지).
- 상태 뱃지(시맨틱, 기능적 색상이라 톤 개편 대상에서 제외): `--color-success: #2FBF71`(성사), `--color-warning`, `--color-danger`(미성사) — **값 변경 없음**, `docs/db/group_buy_team.md`의 `RECRUITING/SUCCESS/FAILED` 상태값에 대응.
- 그림자 3종(`--shadow-sm/md/lg`)은 핑크/퍼플 rgba 틴트에서 다크 세이지 rgba(`rgba(38,43,34,.08/.12/.16)`) 틴트로 조정.
- 폰트: `--font-heading`은 `'Pretendard'` 1순위(이전엔 `'Poppins'` 1순위였으나 제거), `--font-body`는 계속 `'Pretendard'`.
- **완전히 제거된 변수**(001에 있었으나 002에서 삭제): `--color-orange`, `--color-coral`, `--color-pink`, `--color-purple`, `--gradient-brand`, `--gradient-brand-soft`. `tokens.css`/`base.css`/`components.css`에는 더 이상 참조가 없다.

## 로고

- 헤더(`partials/header.html`): 별도 아이콘 이미지를 텍스트 옆에 두던 방식(001~002)에서, 라쿤이 몸을 만 모양(=숫자 "9")인 아이콘을 워드마크 "GONG9RI"의 "9" 글자 자리에 그대로 인라인으로 끼우는 방식으로 바뀌었다(2026-08-20). `<a href="/">`로 감싸 클릭 시 메인 페이지로 이동하는 건 그대로다.
  - 아이콘은 `<img>`가 아니라 빈 `<span class="site-header__wordmark-mark">`이며, `background: var(--gradient-brand)` + `mask-image: url(/images/logo-icon-inline.png)`(여백을 크롭한 신규 자산, 219×305)로 아이콘 모양대로 브랜드 그라디언트를 노출한다. 원본이 얇은 라인아트라 인라인 크기(약 35px)에서 흐려 보이는 문제가 있어 `filter: drop-shadow(...)` 여러 겹으로 선을 굵게 보정했다(`components.css`).
  - 워드마크 전체 폰트 크기를 1.4rem → 1.6rem으로 키웠고, 아이콘은 그 폰트 크기의 1.35em로 텍스트보다 살짝 크게 잡아 귀 부분이 위로 살짝 튀어나온다(의도된 마스코트 포인트, 형태는 잘리지 않음).
  - 접근성: 아이콘 span은 `aria-hidden="true"`, 그 옆에 `.visually-hidden`(`base.css`) 처리된 텍스트 "9"를 둬서 스크린리더는 "GONG"+"9"+"RI"로 그대로 읽는다.
- 밝은 배경(쇼케이스 본문 등)에는 여전히 원본 회색 버전(`/images/logo-icon.png`)을 그대로 쓴다(크롭 전 원본, `design-system.html` 스와치 등). 푸터는 여전히 텍스트 로고(`GONG<span class="text-gradient">9</span>RI`)를 유지하며 이번 변경 대상이 아니다.
- 헤더 배경은 현재 밝은 오프화이트(`layout.css`의 `rgba(255, 249, 245, 0.9)`, 하드코딩) 그대로다 — 002에서 한때 다크 세이지로 캐스케이드 오버라이드됐었지만 이후(사용자 결정으로 색상 톤 전체를 원래 웜 그라디언트로 원복하면서, `docs/dev/ongoing/design-system-sharp-ui.md` Attempt 3) 다시 밝은 배경으로 돌아왔다 — 이 흰색 로고(`logo-icon-white.png`)는 그래서 현재 헤더에는 쓰이지 않는 예비 자산이다.

## 헤더 배경 (캐스케이드 오버라이드로 처리, `layout.css` 자체는 미변경)

- `layout.css`의 `.site-header`는 배경색을 `rgba(255, 249, 245, 0.9)`로 **하드코딩**(변수 참조 아님)하고 있어, `tokens.css`의 색상 토큰만 바꿔서는 헤더 배경이 바뀌지 않는다.
- `layout.css`는 이번 색상 개편 범위에서 수정 금지 대상이라, 대신 **모든 페이지에서 `components.css`가 `layout.css`보다 나중에 로드되는 순서**를 이용해 `components.css`에 같은 선택자(`.site-header`)로 `background-color: rgba(38, 43, 34, 0.94)`(다크 세이지)를 다시 선언해 캐스케이드로 덮어썼다. `layout.css` 파일 자체는 diff 없음(구조 불변).
- 같은 방식으로 `components.css`에서 다크 배경 대비 보정도 처리한다: `.site-header__nav a`(기본 상태, 반투명 흰색), `.site-header__nav a:hover`(완전 불투명 흰색 — `layout.css`의 `:hover` 규칙이 참조하는 삭제된 `--color-pink`를 캐스케이드로 덮어써 사실상 무력화함), `.header-auth-user__name`/`.nav-link--role-active`(`--color-text-on-brand`, 흰색+굵게로 구분).
- **알려진 미해결 잔여 항목**: `layout.css`의 `.site-footer__links a:hover`는 여전히 삭제된 `--color-pink`를 참조하고, `components.css`에 이를 덮어쓰는 규칙이 없어 **푸터 링크의 hover 시 색상 변화가 사라졌다**(CSS 커스텀 프로퍼티가 무효값이 되어 상속값 유지 — 링크 자체는 정상 동작, 에러 없음, 순수 커밋적 열화). `layout.css`를 건드리지 않는다는 제약 때문에 남겨둔 것으로, 헤더 nav와 동일한 캐스케이드 오버라이드 패턴을 `components.css`에 추가하면 간단히 고칠 수 있다(후속 작업 후보).

## 검색 입력창 / 찜하기 버튼 (쇼케이스 컴포넌트만, 실제 페이지 미반영)

- `components.css`에 `.search-input`/`.search-input__field`/`.search-input__btn`(검색 입력창), `.wishlist-btn`/`.is-active`(찜하기 버튼, `.card-image .wishlist-btn`로 카드 이미지 우측 상단 오버레이 포지셔닝) 스타일이 신설됐고, `design-system.html`에 각각 전용 쇼케이스 섹션과 카드 예시에 노출된다.
- 둘 다 **백엔드 API가 없어 순수 UI만** 존재한다 — submit 핸들러, 클릭 핸들러, 상태 저장, API 호출 없음. `index.html`(메인 그리드)·`product.html`(상세)에 실제로 넣는 것은 아직 하지 않았다(사용자가 쇼케이스를 승인한 뒤 별도 후속 계획으로 진행 예정).

## 데이터 모델

신규 테이블/엔티티 없음. 뱃지 시맨틱 컬러(모집중/성사/미성사)는 `docs/db/group_buy_team.md`의 `RECRUITING/SUCCESS/FAILED` 상태값에 대응하도록 `tokens.css`에서 이름만 맞췄을 뿐, 실제 상태 연동(데이터 바인딩)은 개별 페이지 작업 범위다.

## 규칙 / 검증

- **Spring Security**: `SecurityConfig`의 `authorizeHttpRequests`에 `.requestMatchers("/", "/*.html", "/**/*.html", "/css/**", "/js/**", "/partials/**", "/images/**").permitAll()`이 적용되어, 비로그인 상태에서도 정적 프론트 리소스(서브디렉토리 HTML 포함, 이미지 포함)를 열람할 수 있다. `/images/**`는 002 작업에서 로고 이미지 자산이 새로 생기며 추가됐다(누락 시 401로 이미지가 깨짐 — 실제로 한 번 발생했다 수정됨). 기존 `/api/auth/**`, `GET /api/products/**` permitAll 및 `anyRequest().authenticated()`는 변경 없이 유지된다.
- **헤더 로그인 상태 스코프**: 헤더 로그인 상태 연동은 `docs/dev/frontend/header-auth/design.md` 참고(이 문서 범위 밖). 002 작업으로 로고가 이미지로 바뀌었지만 `header-auth.js`가 참조하는 id/`[data-role]`은 변경되지 않았다.
- **partial 재사용**: 페이지는 `<div data-include="header"></div>` / `<div data-include="footer"></div>`를 두고 `js/include.js`를 로드하면 헤더/푸터가 자동 삽입된다. `file://`로 직접 열면 fetch가 실패할 수 있어, 반드시 `./gradlew bootRun` 기동 후 `http://localhost:8080/...`으로 접속해야 한다.
- **디자인 방향성(현재)**: 무신사(대형 패션 이커머스) 톤을 참고한 화이트·라이트 그레이 무채색 베이스 + 다크 세이지 그린 브랜드 포인트, Pretendard 헤딩+본문 통일, 카드 기반 그리드, 모바일 우선. 무신사의 로고·이미지·문구·CSS·HTML·폰트 파일은 참고하지 않았다(색 배합/폰트 스타일 같은 업계 통용 수준만 참고). 정성적 목표라 자동화 기준으로 완결 검증되지 않으며, 사용자 육안 확인을 거쳤다(`docs/logs/frontend/design-system/002-showcase-rebrand.md` 참고).
- **네이티브 `[hidden]`은 전역 안전장치로 항상 이긴다**(2026-08-20 추가, `base.css`). `display`를 선언하는 클래스(`.btn`의 `inline-flex` 등)를 가진 요소에 JS가 `hidden` 속성을 걸어도, 브라우저 기본 스타일시트의 `[hidden] { display: none }`은 명시도가 가장 낮아 밀려버린다. 이 함정을 여섯 번 겪고(`.btn` / `.product-detail` / `.site-header__auth` / `.chat-widget*` / `.card-seller-trust` / `.site-header__wishlist-link`) 매번 개별 `.클래스[hidden]` 규칙을 사후 추가해온 끝에, `base.css`에 `[hidden] { display: none !important; }`를 전역으로 두기로 했다.
  - `!important`가 정당한 근거: "`hidden` 속성이 걸린 요소는 예외 없이 숨겨진다"는 뒤집힐 이유가 없는 규칙이다. 도입 전 전수 확인한 것 — `style.display`를 직접 조작하는 JS 0건(인라인 스타일 충돌 없음), `hidden` 토글은 전부 `el.hidden = true/false`로 일관(`setAttribute`/`removeAttribute` 0건), `display`에 `!important`를 쓰는 다른 규칙 0건, `[hidden]` 요소를 다시 보이게 하려는 CSS 규칙 0건.
  - **새 컴포넌트를 만들 때 `.클래스[hidden]` 보정을 따로 넣을 필요가 없다.** 그동안 쌓여있던 개별 보정 규칙 17개는 전역 규칙이 프로덕션에서 실제로 동작하는 걸 확인한 뒤 별도 커밋으로 전부 제거했다(순삭제 81줄) — 도입과 제거를 한 번에 하면 문제 발생 시 원인 분리가 안 되므로 일부러 두 단계로 나눴다.
- **버튼 텍스트는 드래그 선택 대상에서 뺀다**(2026-08-20 추가, `base.css`의 `button { user-select: none }`). 인기검색어 티커(`<button>`)를 더블클릭하면 안쪽 글자가 파랗게 선택된 채 남는데, 사용자가 이걸 "입력창처럼 커서가 깜빡인다"로 인지해 버그로 리포트했다. 버튼은 읽는 텍스트가 아니라 누르는 컨트롤이라 선택되는 것 자체가 오해를 부른다. 티커 하나만 고치면 텍스트가 있는 다른 버튼(오버레이 "닫기", 카테고리 태그, 최근검색어 등)에서 반복되므로 위 `[hidden]`과 같은 이유로 처음부터 전역으로 뒀다.
- **레이아웃 구조는 001 이후 변경 없음**: 헤더 1단 구조, 그리드 컬럼 수, 푸터 2블록 구조, 상세 페이지 세로 1컬럼 구조가 그대로 유지된다. `css/layout.css`는 002 작업에서도 diff 없음(단, 위 "헤더 배경" 섹션에서 설명한 캐스케이드 오버라이드로 시각적 배경색만 실질적으로 바뀜 — 파일 자체는 미변경).

## 관련 코드 위치

- `src/main/resources/static/{css/tokens.css,css/base.css,css/layout.css,css/components.css,js/include.js,js/api.js,partials/header.html,partials/footer.html,design-system.html}`
- `src/main/resources/static/images/{logo-icon.png,logo-icon-white.png}` — 002에서 신규
- `src/main/resources/static/images/logo-icon-inline.png` — 004에서 신규(여백 크롭, 헤더 워드마크 인라인 삽입용)
- `src/main/java/com/gong9ri/gong9ri/config/SecurityConfig.java` — 정적 리소스 permitAll matcher에 `/images/**` 포함(총 1줄, 002에서 추가)
