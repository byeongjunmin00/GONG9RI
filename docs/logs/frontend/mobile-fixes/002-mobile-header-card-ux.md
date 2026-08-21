# 002-mobile-header-card-ux — 모바일 헤더/카드 UX 정리 (로그)

## Attempt 1 — 2026-08-21

- 시도: 계획(`docs/dev/ongoing/mobile-header-card-ux.md`) 태스크 5개 중 CSS만으로 해결되는 4개 구현.
  1. **헤더 모바일 배치 순서 재구성** (`css/layout.css`, `css/components.css`) — `.site-header__logo`(order:1) / `.site-header__search`(order:2, 기존 3에서 변경) / `.site-header__right`(order:3, 신규 명시)로 최상위 3개 자식의 순서를 한 곳에서 일관되게 고정. 이전에는 검색창의 `order:3`이 nav/알림/로그인 묶음(`.site-header__right`)과 다른 flex 컨테이너 소속이라 서로 무관하게 작동해 검색창이 그 묶음보다도 더 아래로 밀렸었다.
  2. **헤더 컨트롤 높이 통일** (`css/layout.css`) — 모바일(`≤767px`)에서 알림벨/찜 아이콘을 32→40px로, 로그인·로그아웃 버튼(`.site-header__auth .btn`)에 `min-height:40px`을 줘서 같은 줄에 놓일 때 높이를 맞춤. 동시에 아이콘 터치 타겟도 40px로 개선.
  3. **터치 sticky-hover 방지** (`css/components.css`) — `.card:hover`, `.card-wishlist-btn:hover`, `.btn-primary:hover`, `.btn-secondary:hover`의 transform/shadow 리프트 효과를 `@media (hover: hover)`로 감싸 마우스 등 실제 hover 가능한 입력 장치에서만 적용되게 함. `.btn-ghost:hover`(transform 없이 색상만 변경)는 sticky 되어도 거슬림이 적어 범위에서 제외.
  4. **카드 찜 버튼 터치 타겟** (`css/components.css`) — `.card-wishlist-btn`을 모바일에서 32→36px로 확대(헤더 아이콘 40px보다는 작게 — 2열 카드 안이라 여유가 적음).
  - 태스크 5개 중 "상품 카드 2열 모바일 레이아웃 실기기 확인 후 필요 시 조정"은 계획대로 실기기(브라우저 모바일 뷰포트) 확인 후 문제가 실제로 보일 때만 조정하기로 하고 Evaluate에서 진행.
- 접근 근거: 세 문제 모두 코드(CSS 수치·구조)를 직접 읽어 원인을 특정한 뒤 계획서에 근거로 남겼고(`docs/dev/ongoing/mobile-header-card-ux.md`의 "원인 분석" 절), 이번 시도는 그 계획의 "설계" 절 방향을 그대로 구현한 것 — 계획에 없던 범위(컬럼 수, 브랜드 톤 등)는 건드리지 않음.
- 검증: `grep -c "{"`/`"}"` 로 두 CSS 파일 중괄호 균형 확인(layout.css 46:46, components.css 365:365). 실제 브라우저 렌더링 확인은 Evaluate에서 진행.

## Evaluate — 2026-08-21  ✅ PASS

- 방법: 이미 다른 세션이 8080에 bootRun을 띄워둔 상태였는데, 정적 리소스는 `classpath:/static/`에서 서빙돼(`WebMvcConfig`) 그 프로세스를 재시작하지 않는 한 새 CSS가 반영되지 않는다. 다른 세션 작업을 건드리지 않기 위해 Gradle/Spring Boot와 무관한 독립 Java 정적 서버(`com.sun.net.httpserver.HttpServer`, 임시 스크립트)를 5500 포트에 띄워 `src/main/resources/static`을 그대로 서빙해 검증하고, 확인 후 종료했다. 상품 API(`/api/products`)는 이 서버엔 없어 404가 나므로(정상 — 백엔드 미기동), 카드 검증은 실제 클래스와 동일한 마크업을 `product-grid`에 직접 주입해 진행했다.
- 결과(모바일, 375px):
  - 헤더 자식 순서(측정된 `top` 좌표) — 로고(top 8) → 검색창(top 62) → nav/알림/로그인 묶음(top 130). 의도한 "로고→검색→나머지" 순서로 확인됨.
  - 로그인 버튼 `min-height: 40px` 적용 확인(실제 렌더 높이 40px). 알림벨·찜 아이콘 `getComputedStyle` 40×40px 확인.
  - `matchMedia('(hover: hover)')` → `false` (모바일 에뮬레이션) — `.card:hover`/`.btn-primary:hover`/`.card-wishlist-btn:hover` 등이 `@media (hover: hover)` 안에 있어 이 조건에서 규칙 자체가 적용되지 않음을 확인.
  - `.card-wishlist-btn` computed size 36×36px 확인.
  - 실감형 목업 카드(판매자명 9자+신뢰배지, 5자리 가격 2종, 진행률)를 2열 그리드(카드 폭 165.5px)에 주입 → `card-body` 하위 요소 중 `scrollWidth > clientWidth`인 요소는 `card-title`뿐(의도된 `ellipsis` 말줄임, 버그 아님). `card-seller-row`/`card-price-row`는 `flex-wrap: nowrap`이고 자식 rect가 실제로 한 줄에 나란히 배치됨(겹침·줄바꿈 없음) — 확인만 하고 추가 조정 없음(계획의 "실기기 확인 후 필요 시 조정" 태스크, 조정 불필요로 판정).
- 결과(데스크톱, 1280px — 회귀 확인):
  - `.site-header__inner` `display: grid` 그대로 유지(모바일 flex 규칙 미적용).
  - 알림벨·찜 아이콘 32×32px 그대로(모바일 40px 오버라이드 미적용).
  - 로그인 버튼 `min-height: auto`(모바일 오버라이드 미적용).
  - `matchMedia('(hover: hover)')` → `true` — 데스크톱에서는 카드/버튼 hover 리프트가 그대로 동작(회귀 없음).
- 계획·컨벤션 준수: 계획서(`docs/dev/ongoing/mobile-header-card-ux.md`)의 태스크 5개 중 4개를 그대로 구현, 5번째(카드 밀집도)는 실측 후 "조정 불필요"로 판정 — 계획 범위를 벗어난 변경 없음(컬럼 수·톤앤매너 등 미변경). 이 작업은 순수 정적 프론트엔드(CSS)라 `docs/code-convention.md`(Java/Spring 대상)는 해당 사항 없음.
- 계산적 평가: Java/백엔드 변경이 없어 `./gradlew test`/`compileJava` 스코프 대상이 없음(생략 근거를 명시적으로 밝힘 — 생략이 곧 회피가 아니라 변경 범위가 정적 리소스뿐이라는 사실에 근거).
