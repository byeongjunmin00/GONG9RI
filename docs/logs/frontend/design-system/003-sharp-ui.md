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

## Attempt 1 후속 — 2026-08-18 ✅ 브라우저 검증 + 버그 수정

- 시도: `./gradlew bootRun`으로 Browser pane에서 `design-system.html` 직접 확인.
- 결과: Attempt 1이 미해결로 남긴 우려가 실제로 발생 — 헤더에서 검색 토글 버튼이 로고/네비/auth 사이 어중간한 위치(거의 중앙)에 떠 보임. `justify-content: space-between`에 4번째 flex item이 추가되며 간격 분배가 깨진 것.
- 조치: `partials/header.html`에서 `<nav>`, 검색 토글 버튼, 두 `.site-header__auth` div를 새 래퍼 `<div class="site-header__right">`로 묶음. `layout.css`에 `.site-header__right { display: flex; align-items: center; gap: var(--space-5); flex-wrap: wrap; }` 신설. 이제 `.site-header__inner`는 로고 + `.site-header__right` 2개 항목만 가져서 `space-between`이 의도대로 "로고 왼쪽 / 나머지 전부 오른쪽"으로 동작함. 모바일 미디어쿼리의 `.site-header__nav { order: 3; width: 100%; }`는 새 부모(`.site-header__right`) 기준으로도 동일하게 동작(자식 재배치이므로 문제 없음).
- 검증: 재시작 후 스크린샷으로 확인 — 로고(좌) / 검색 아이콘+로그인+회원가입(우) 정상 정렬. 검색 아이콘 클릭 → 헤더 바로 아래 전체 폭 팝업 패널 열림(입력창 포커스 자동, 닫기 버튼 있음) → 재클릭/닫기 버튼으로 정상 닫힘. 버튼/카드 radius 축소, 카드 그리드 gap 축소 육안 확인. 콘솔 에러는 `/api/auth/me` 401(비로그인 상태의 기존 의도된 동작)만 있고 그 외 없음.
- 결과: `./gradlew test` 재확인 → `BUILD SUCCESSFUL`.

## 병합(merge) 시도 — 2026-08-18 ⚠️ 진행 중 (사람 확인 대기)

- 시도: 사용자 지시로 현재까지의 작업(1단계+2단계, 커밋 `wip(design-system): ...`)을 WIP 커밋한 뒤 `git pull` 실행.
- 결과: 팀원의 `feat(product/list-enhancements): 상품 카테고리·정렬·참여 진행바 + 메인 페이지 배너 추가`(커밋 `25f94cc`)와 `css/components.css`에서 병합 충돌 발생. 다른 파일(`index.html`, `seller/products/{new,edit}.html` 등)은 자동 병합됨.
- 분석:
  - **구조적 충돌**: 내 쪽(검색입력창/찜하기버튼 스타일)과 팀원 쪽(프로모배너/카테고리바 스타일)이 `components.css`의 같은 삽입 지점(파일 끝 부근)에 각자 새 규칙을 추가해서 git이 자동 병합 못 함 — 실제 내용은 겹치지 않아 **둘 다 유지**하면 되는 충돌.
  - **진짜 문제(의미적 충돌)**: 팀원이 새로 만든 3개 기능이 이번 리브랜드에서 완전히 삭제한 색상 변수(`--color-orange`, `--color-pink`, `--gradient-brand`)를 참조 중 — `.card-progress-label b`/`.card-progress-fill`(카드 참여 진행바, 병합충돌 구간 밖이라 자동병합됐지만 조용히 깨짐), `.promo-bar-slide__text b`/`.promo-bar-cta:hover`(메인 프로모 배너), `.category-pill:hover`/`.category-pill.active`(카테고리 필터바) 총 6곳.
  - `tokens.css`는 이번 병합에서 충돌 없이 깨끗하게 병합됐고(팀원이 안 건드림), 삭제된 변수들은 실제로 파일에 없음을 `grep`으로 확인.
- 조치: 병합을 완료하지 않고 사용자에게 6곳 각각의 대체 색상 값을 확인받기 위해 대기 중(`--color-brand`로 통일 제안했으나 프로모배너 2곳은 배경이 어두운 톤이라 재검토 필요하다고 안내). `git status`는 현재 `git merge --abort` 전 unmerged 상태(`components.css`만 conflict, 나머지는 staged).
- 다음: 사용자 확인 받은 색상 값으로 `components.css` 충돌 구간 수동 해결 → `git add` → merge 커밋 → 전체 재검증(`./gradlew test` + 브라우저 육안 확인, 특히 팀원의 새 기능 3개가 정상 렌더링되는지) → Evaluate.

## 병합 완료 — 2026-08-18 ✅ PASS

- 시도: 사용자가 시각 미리보기(3가지 프로모 배너 강조색 옵션)를 보고 C안(흰색+밑줄) 선택. 이를 반영해 6곳 색상 치환 완료:
  - `.card-progress-label b`, `.card-progress-fill`, `.category-pill:hover`, `.category-pill.active` → `--color-brand`
  - `.promo-bar-slide__text b`, `.promo-bar-cta:hover` → `--color-text-on-brand`(흰색) + `text-decoration: underline`(배경이 어두운 톤이라 브랜드 그린은 대비 약함, 흰색+밑줄로 대체)
  - 겸사겸사 `layout.css`에 남아있던 구 참조 2곳(`.site-header__nav a:hover`, `.site-footer__links a:hover`, 둘 다 `--color-pink`)도 `--color-brand`로 정리(헤더 쪽은 어차피 components.css가 흰색으로 덮어써서 실질 영향 없었지만, 소스 정확성을 위해 정리).
- 결과:
  - `grep`으로 `src/main/resources/static/css/` 전체 재확인 → `--color-orange`/`--color-pink`/`--color-coral`/`--color-purple`/`--gradient-brand` 참조 0건.
  - `./gradlew test` → `BUILD SUCCESSFUL in 1m 6s`.
  - `git commit`으로 병합 완료(커밋 `b8849b3`).
  - `./gradlew bootRun` + Browser pane으로 `index.html` 실사용 확인: 헤더(로고/검색버튼/로그인·회원가입 정렬 정상) + 프로모 배너(흰색 밑줄 강조, 자동 슬라이드+점 네비게이션 정상) + 카테고리 필터(전체/식품 등 pill, 클릭 시 활성 상태가 다크 세이지로 정상 전환, 클릭 시 배너도 갱신) 모두 정상 렌더링. 네트워크 요청 확인 — `/api/products?sort=POPULAR&size=1`, `?sort=LATEST`(팀원 신규 API) 200 OK, `/js/search-popup.js` 200 OK. 콘솔 에러는 `/api/auth/me` 401(비로그인 기존 의도된 동작)만 있고 그 외 없음.
- 다음: Evaluate 단계로 진행 권장(이번 병합 포함 전체 diff 대상).

## 후속 수정 — 2026-08-18 ✅ PASS (검색 버튼 아이콘 이모지 → SVG 교체)

- 시도: 사용자 피드백 — 헤더 검색 토글 버튼의 🔍 이모지가 "짜쳐 보인다"(플랫폼별 렌더링 불일치로 로고의 라인아트 톤과 안 어울림). `partials/header.html`의 `<span aria-hidden="true">🔍</span>`를 로고와 같은 선 굵기(stroke-width 2, round cap)의 인라인 SVG 돋보기 아이콘(`stroke="currentColor"`)으로 교체해 헤더 색 상속·hover 색 전환이 그대로 적용되게 함(`.site-header__search-toggle`의 `color`/`:hover` 규칙 재사용, CSS 변경 없음).
  - 중간에 "돋보기 옆에 '검색' 글씨도 넣어달라"는 요청으로 `<span>검색</span>` 추가 + `.site-header__search-toggle`을 고정 40x40 정사각형에서 `padding`+`gap` 기반 가변 폭으로 변경했으나, 사용자가 곧바로 텍스트 라벨을 취소해 아이콘 전용 40x40 정사각형 버튼으로 원복(마크업·CSS 둘 다).
- 결과: `./gradlew bootRun` + Browser pane으로 `index.html` 확인 — 아이콘이 로고와 통일된 라인 스타일로 렌더링, 헤더 좌우 정렬(로고/검색/로그인/회원가입) 정상. (참고: 확인 도중 사용자가 "간격이 이상하다"고 했으나, 원인은 화면이 아니라 CSS가 하나도 로드되지 않는 `file:///.../partials/header.html` raw 파일 미리보기 탭 — 편집 직후 훅이 자동으로 띄운 탭 — 을 보고 계셨던 것으로 확인됨. 실제 `localhost:8080` 페이지로 재확인 후 정상 확인받음.)
- 다음: 이 변경(`partials/header.html`)은 아직 커밋 여부 사용자 확인 대기 중.
