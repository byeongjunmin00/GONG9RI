# 003-sharp-ui — 각진 UI + 여백/그리드 조정 (2단계, 무신사 톤 참고) (로그)

## Attempt 1 — 2026-08-18

- 시도: 승인된 계획(`docs/dev/ongoing/design-system-sharp-ui.md`)의 "이번 범위"·"태스크"대로 구현.
  - **`css/tokens.css`**: `--radius-sm`/`--radius-md`/`--radius-lg`를 8/16/24px → **4/8/12px**로 축소(눈에 띄는 각짐, `--radius-full`은 대상 아님 — 원형 아이콘/핀 형태 유지). `--container-max`를 1200px → **1320px**로 확대(넓은 화면에서 콘텐츠 폭을 넓게).
  - **`css/layout.css`**(이번엔 명시적으로 수정 허용): `.container`에 `min-width: 1024px` 미디어쿼리를 신설해 좌우 패딩을 `--space-7`(48px)로 한 단계 더 키움(컨테이너 폭 확대분에 맞춰 "여백은 적당히" 유지, 기존 768px 이하 패딩 규칙은 그대로 둠). `.grid-cards`의 `gap`을 16/24px → **12/16px**로 축소(더 촘촘하게). **컬럼 수(`repeat(2/3/4, 1fr)`)·브레이크포인트(480/768/1024px)·헤더/푸터 단수 구조는 값 그대로 두고 gap/padding만 수정**(diff로 확인).
  - **`css/components.css`**:
    - `.btn`(primary/secondary/ghost 공통)의 `border-radius`를 `--radius-full`(알약형)에서 `--radius-sm`(4px, 각진 사각형)로 변경 — "AI 냄새 난다"는 피드백의 핵심 대상.
    - `.search-input`(외곽 캡슐)을 `--radius-full` → `--radius-md`(8px)로, 내부 `.search-input__btn`(원형 아이콘 버튼)을 `--radius-full` → `--radius-sm`으로 변경해 통일감 유지.
    - `.card`/`.form-input`/`.form-textarea`/`.form-select`/`.product-price-box`/`.product-detail-image`/`.role-option`/`.team-item`/`.mypage-list-item`/`.revenue-card` 등은 전부 `--radius-md`/`--radius-lg` 토큰을 그대로 참조하고 있어, 토큰 값 변경만으로 자동으로 각져 보임을 확인(개별 오버라이드 불필요). `.badge`/`.wishlist-btn`/`.chat-widget__button`은 `--radius-full`(핀/원형)을 쓰고 있어 계획 범위(버튼/카드/검색창) 밖이라 그대로 둠.
    - **검색 팝업 패널 신규**: `.site-header__search-toggle`(헤더 검색 아이콘 버튼) + `.search-popup`/`.search-popup__inner`/`.search-popup__field`/`.search-popup__close` 신설. `.site-header`가 `layout.css`에서 이미 `position: sticky`라 이를 포지셔닝 컨텍스트로 삼아 `.search-popup`을 `position: absolute; top: 100%; left/right: 0;`으로 헤더 바로 아래 전체 폭에 붙임(무신사류 구조 패턴만 참고, 콘텐츠는 검색 입력창 1개뿐 — "최근 검색어" 등 섹션 없음).
  - **`partials/header.html`**: `.site-header__inner` 안 nav와 auth 영역 사이에 검색 토글 버튼(`#search-toggle-btn`, `aria-expanded`/`aria-controls`)을 추가하고, `<header>` 하위에 `#search-popup-panel`(기본 `hidden`, 검색 입력창 `#search-popup-field` + 닫기 버튼 `#search-popup-close`)을 추가. `header-auth.js`가 참조하는 기존 id(`header-auth-guest`/`header-auth-user`/`header-auth-user-name`/`header-auth-logout`)와 nav `[data-role]` 속성은 손대지 않음(diff로 확인, 기존 요소는 위치·속성 그대로).
  - **신규 `js/search-popup.js`**: `js/header-auth.js`/`js/chat-widget.js`와 동일하게 `document.addEventListener('gong9ri:includes-ready', init)` 패턴으로 헤더 삽입 완료 후 초기화. `#search-toggle-btn` 클릭 시 `#search-popup-panel`의 `hidden` 토글 + `aria-expanded`/`.is-active` 클래스 갱신, 열릴 때 입력창에 포커스. `#search-popup-close` 클릭 시 닫기. **제출 핸들러, 검색 실행, fetch 호출 없음** — `<form>` 태그 자체를 쓰지 않아 submit 이벤트가 발생할 여지도 없앰.
  - **스크립트 태그 추가**: `js/header-auth.js`/`js/chat-widget.js`가 로드되는 모든 정적 페이지(`design-system.html`, `index.html`, `login.html`, `product.html`, `signup.html`, `checkout.html`, `reset-password.html`, `forgot-password.html`, `buyer/mypage.html`, `seller/mypage.html`, `seller/products/new.html`, `seller/products/edit.html`, 총 12개)에 `chat-widget.js` 바로 다음 줄로 `<script src="/js/search-popup.js"></script>`를 추가(기존 로드 순서 컨벤션 유지: include.js → api.js → header-auth.js → chat-widget.js → search-popup.js → 페이지 전용 스크립트).
  - **`design-system.html`**: 기존 "검색 입력창" 섹션 제목을 "검색 입력창 (인라인)"으로 명확히 하고, 그 아래 "검색 팝업 패널" 섹션을 신설 — 이 페이지가 이미 `data-include="header"`로 실제 헤더(검색 버튼+팝업 포함)를 로드하므로, 별도로 마크업을 복제하지 않고 "헤더 우측 돋보기 버튼을 눌러보라"는 안내 문구로 실제 인터랙션을 그대로 쇼케이스로 활용(중복 id 충돌 방지 + 실제 동작 그대로 시연).
- 결과:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`(UP-TO-DATE, 이번 변경이 전부 정적 리소스라 Java 컴파일 대상 없음 — 정상).
  - `./gradlew test` → `BUILD SUCCESSFUL in 53s`(5 actionable tasks: 2 executed, 3 up-to-date). 전체 통과, 실패 없음.
  - `components.css` 중괄호 개수(`{`/`}`) 143/143으로 균형 확인(문법 깨짐 없음 — Gradle 테스트가 CSS 문법을 검증하진 않으므로 별도 점검).
  - `git status --porcelain`으로 이번 세션에서 수정한 파일 목록 확인: `css/tokens.css`, `css/layout.css`, `css/components.css`, `partials/header.html`, `design-system.html`, `index.html`, `login.html`, `product.html`, `signup.html`, `checkout.html`, `reset-password.html`, `forgot-password.html`, `buyer/mypage.html`, `seller/mypage.html`, `seller/products/new.html`, `seller/products/edit.html`(스크립트 태그 1줄 추가), 신규 `js/search-popup.js`.
    - 참고: `git status`에 `base.css`/`SecurityConfig.java`/`docs/dev/frontend/design-system/design.md` 등도 modified로 함께 표시되는데, 이는 **1단계(002-showcase-rebrand)에서 이미 만들어진 뒤 아직 커밋되지 않은 변경**이고 이번 세션에서 건드리지 않았다(diff 내용 확인, 이번 Attempt는 순수히 그 위에 얹은 것).
- **미해결/참고 사항 (사람 확인 필요)**:
  - `./gradlew bootRun`으로 실제 브라우저에서 헤더 검색 버튼 클릭 → 팝업 열림/닫힘, 각진 버튼/카드/그리드 밀도, 페이지 좌우 여백을 육안으로 확인하지는 못했다(이 세션엔 브라우저 프리뷰 도구를 사용하지 않음). Evaluate 단계에서 시각 확인이 필요하다.
  - 헤더 `.site-header__inner`는 `layout.css`에서 `justify-content: space-between`인데, 검색 토글 버튼을 nav와 auth 사이의 4번째 flex 아이템으로 추가해 간격 분배가 기존(로고/네비/auth 3개) 대비 달라진다. `layout.css` 자체(구조/컬럼/브레이크포인트)는 건드리지 않았지만, 이 flex item 개수 변화로 인한 시각적 간격 재배치는 브라우저로 확인이 필요하다.
- 다음: Evaluate 단계로 진행. 브라우저 시각 검증(팝업 열기/닫기, 각짐 정도, 그리드 밀도, 컨테이너 여백, 콘솔 에러 유무)을 evaluator가 수행 권장.
