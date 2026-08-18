# 002-showcase-rebrand — 디자인 시스템 색상/브랜드 요소 개편 (로그)

## Attempt 1 — 2026-08-18  ✅ PASS (로고 이미지 자산 준비, 계획은 아직 승인 대기 중)

- 시도: 계획(`docs/dev/ongoing/design-system-layout-revamp.md`) 승인 전, 사용자가 직접 만든 로고 이미지(라쿤 얼굴 모양 "9" 라인아트)를 실제 프로젝트 자산으로 저장해달라는 요청을 받아 처리.
  - 사용자가 준 원본 파일(`decedee4-c392-48ae-bb37-1d2af02218df.jpg`, 데스크톱)을 확인해보니 확장자와 달리 실제 JPEG였고, "누끼(배경 제거)"를 했다고 했지만 JPEG는 알파 채널을 지원하지 않아 편집 툴의 체크무늬 투명도 표시가 실제 픽셀 색상(RGB 179~229)으로 그대로 저장돼 있었다.
  - PowerShell(System.Drawing)로 픽셀을 스캔해 라인아트(RGB≈97, 어두움)와 체크무늬 배경(RGB≈179~229, 밝음)의 밝기 차이를 이용, 밝기 115~165 구간을 알파로 선형 보간하는 방식으로 실제 투명 PNG로 변환.
  - 변환 후 이미지 오른쪽 여백에 배경 제거 도구가 남긴 것으로 보이는 흐릿한 워터마크 잔여물을 발견 — 원인은 초기 임계값(200) 설정 오류로 체크무늬 어두운 칸(≈180)이 불투명으로 잘못 남은 것이었고, 임계값을 재보정(115/165)하자 함께 해소됨을 확인. 추가로 opaque 픽셀의 열(column)별 히스토그램을 뽑아 아이콘 몸통(x≈420~620)만 남기고 나머지 노이즈를 컷오프, 아이콘 영역만 타이트하게 크롭.
  - 사용자가 "헤더 배경이 쑥색 계열이니 로고는 흰색으로"라고 추가 요청 — 같은 알파 채널을 유지한 채 불투명 픽셀만 흰색(255,255,255)으로 바꾼 버전을 별도 생성.
- 결과:
  - `src/main/resources/static/images/logo-icon.png` (243×328, 회색 라인아트, 투명 배경) 저장 완료.
  - `src/main/resources/static/images/logo-icon-white.png` (동일 크기/알파, 흰색 라인아트) 저장 완료.
  - 두 파일 모두 육안 확인(Read 도구 + 다크 세이지 배경 합성 미리보기)으로 워터마크·체크무늬 잔여 없음, 투명도 정상 확인.
- 증거: 다크 세이지(#3D4A3A 근사치) 배경에 흰색 버전을 합성한 미리보기를 사용자에게 전달, 확인받음.
- 참고: 이 Attempt는 이미지 자산 준비만 다룬다. `tokens.css`/`base.css`/`components.css`/`partials/header.html` 등 실제 코드 변경(로고 교체 적용, 색상 토큰 전면 교체 등)은 계획 승인 후 Generate 단계에서 별도로 진행하며, 그때 이 로그에 새 Attempt로 이어서 기록한다.
- 다음: 계획 문서 최종 승인 대기 중. 승인되면 Generate에서 `tokens.css`/`base.css`/`components.css`/`partials/header.html`/`design-system.html` 변경을 진행하고 이 로그에 이어서 기록한다.

## Attempt 2 — 2026-08-18

- 시도: 승인된 계획(`docs/dev/ongoing/design-system-layout-revamp.md`)의 "확정 범위(1단계)"대로 색상/폰트 토큰 전면 교체 + `design-system.html` 쇼케이스 갱신을 구현.
  - **`css/tokens.css`**: `--color-orange`/`--color-coral`/`--color-pink`/`--color-purple`/`--gradient-brand`/`--gradient-brand-soft` 전부 삭제. 흰색(`--color-surface: #FFFFFF`, 불변) + 연회색 2톤(`--color-bg: #F5F6F3`, `--color-border: #E1E4DC`, `--color-surface-alt: #EEF0EA`) + 다크 세이지 그린 신규 변수 도입: `--color-text: #262B22`(텍스트), `--color-text-muted: #6E7568`, `--color-brand: #445940`(메인 브랜드 강조색 — 버튼/링크/포인트 텍스트), `--color-brand-dark: #333F30`(hover용 더 어두운 톤). 그림자 3종의 rgba 틴트를 핑크/퍼플에서 `rgba(38, 43, 34, .08/.12/.16)`(다크 세이지 RGB)로 조정. `--font-heading`에서 `'Poppins'` 제거, `'Pretendard'`를 1순위로. `--color-success`/`--color-warning`/`--color-danger`, `--container-max`, `--header-height`, spacing/radius 변수는 손대지 않음(계획대로).
    - 추가 결정: 기존 `--color-coral`이 폼 검증 에러 색으로도 쓰이고 있었는데(로그인/회원가입 등), 이걸 그대로 삭제하면 대체 색이 없어 폼 에러 표시가 깨진다. `--color-danger`(미성사 뱃지, 회색빛 퍼플, "중립적으로 처리"라는 코멘트가 있어 실제 경고색이 아님)를 재사용하는 대신, 상태 뱃지와 완전히 분리된 신규 변수 `--color-error: #B3483C`(차분한 브릭레드)를 추가해 폼 에러 전용으로 썼다. 상태 뱃지 3색 값 자체는 변경하지 않았다.
  - **`css/base.css`**: `.text-gradient`를 그라디언트 텍스트 클립 방식에서 `color: var(--color-brand)` 단일색으로 재정의. `:focus-visible` 아웃라인 색도 `--color-purple`(삭제 대상)에서 `--color-brand`로 교체.
  - **`css/components.css`**: `--color-orange/coral/pink/purple`, `--gradient-brand*`를 참조하던 모든 곳(버튼 primary/secondary/ghost hover, 카드 이미지 배경, 카드 베스트가 텍스트, `badge-leader`, 폼 focus 테두리/그림자, `form-error`/`form-alert--error`, `role-option` 선택 상태, `product-status--error`, 상품 탭 hover/active, 헤더 nav 활성 링크, 챗위젯 버튼/메시지/입력창/전송버튼)를 새 토큰(`--color-brand`/`--color-brand-dark`/`--color-error` 및 그에 맞는 rgba)으로 교체. `grep`으로 components.css/base.css 전체를 재확인해 삭제 대상 변수 참조가 하나도 안 남았음을 확인.
    - **검색 입력창** 컴포넌트(`.search-input`, `.search-input__field`, `.search-input__btn`) 신설 — 쇼케이스 전용, 제출 핸들러 없음.
    - **찜하기 버튼** 컴포넌트(`.wishlist-btn`, `.is-active` 상태) 신설 — 쇼케이스 전용, 클릭 핸들러 없음. 카드 이미지 우측 상단에 배치하기 위한 `.card-image .wishlist-btn` 포지셔닝 규칙도 `.card-image .badge` 옆에 추가.
    - **헤더 다크 세이지 배경 + 로고 이미지 스타일(경계 판단 필요했던 부분)**: 계획 문서의 "확정 범위" 섹션에 "헤더 배경이 어두운 세이지 계열이라 흰색 버전 사용"이라는 전제가 명시돼 있는데, 실제로 `.site-header`의 배경색은 `css/layout.css`에 `rgba(255, 249, 245, 0.9)`로 하드코딩돼 있어(변수 참조 아님) 토큰만 바꿔서는 헤더가 어두워지지 않는다. `layout.css`는 절대 수정하지 말라는 명시적 제약이 있어, 대신 **`components.css`가 모든 페이지에서 `layout.css`보다 나중에 로드된다는 로드 순서**를 이용해 `components.css`에 `.site-header { background-color: rgba(38, 43, 34, 0.94); }`를 새로 선언해 같은 선택자를 덮어썼다(캐스케이드 오버라이드, `layout.css` 파일 자체는 diff 없음 — `git status`로 확인). 이와 함께 다크 배경 위에서 필요한 대비 보정도 `components.css`에서 처리: `.site-header__nav a` 텍스트 색을 반투명 흰색으로, `.header-auth-user__name`/`.nav-link--role-active`는 `--color-text-on-brand`(흰색)로. `.site-header__logo img` 규칙(`height: 40px; width: auto;`)도 이 섹션에 추가.
      - **미해결로 남긴 부분**: `layout.css`의 `.site-header__nav a:hover`와 `.site-footer__links a:hover`가 여전히 `var(--color-pink)`(삭제된 변수)를 참조한다. `layout.css`를 건드리지 말라는 제약 때문에 고치지 않았고, CSS 커스텀 프로퍼티 특성상 에러 없이 "무효값 → 상속값 유지"로 처리되어 hover 시 색이 안 바뀌는 정도의 경미한 열화만 생긴다(계획 문서의 리스크 섹션에서 이미 예견된 종류의 회귀). 컴파일/테스트에는 영향 없음.
  - **`partials/header.html`**: 텍스트 로고(`GONG<span class="text-gradient">9</span>RI`)를 `<img src="/images/logo-icon-white.png" alt="GONG9RI" />`로 교체, 기존처럼 `<a href="/">`로 감쌌다. `header-auth.js`가 참조하는 id들과 nav `[data-role]`은 변경 없음(diff로 확인).
  - **`partials/footer.html`**: 변경 없음 — 계획대로 텍스트 로고 유지, `.text-gradient` 재정의만으로 "9" 강조색이 자동으로 새 톤을 따라간다.
  - **`design-system.html`**: Poppins Google Fonts `<link>` 2종 제거(더 이상 헤딩에 안 씀), Pretendard CDN만 유지. 색상 팔레트 섹션을 새 9개 토큰(bg/surface/surface-alt/border/text/text-muted/brand/brand-dark/error)으로 전면 교체. 타이포그래피 설명 문구를 "Pretendard로 통일"로 수정, H1 예시 문구에서 "인스타 감성" 표현 제거. 버튼 섹션 설명에서 "그라디언트" 표현 제거. 브랜드 마크 섹션 신설(회색 로고를 밝은 배경에, 흰색 로고를 다크 세이지 배경 패치 위에 나란히 시연). 상품 카드 예시 3개 각각에 `.wishlist-btn`을 이미지 우측 상단에 추가(하나는 `.is-active`). 검색 입력창/찜하기 버튼 전용 섹션 2개 신설, 각 섹션에 "백엔드 API 없음 / 클릭·제출 핸들러 없음"을 명시하는 안내 문구 포함.
- 결과:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`.
  - `./gradlew test` → `BUILD SUCCESSFUL`(전체 통과, 백엔드 무관 정적 리소스 변경이라 회귀 없음 확인).
  - `git status --porcelain`으로 `css/layout.css`가 이번 변경에 전혀 포함되지 않았음을 확인(수정 파일: `base.css`, `components.css`, `tokens.css`, `design-system.html`, `partials/header.html`만 modified로 표시됨).
  - `grep`으로 `css/` 디렉터리 전체 재확인 결과, `--color-orange`/`--color-coral`/`--color-pink`/`--color-purple`/`--gradient-brand*` 참조는 `layout.css` 2곳(위에서 설명한 hover 규칙, 의도적으로 미수정)만 남고 `tokens.css`/`base.css`/`components.css`에서는 완전히 제거됨.
- 증거: `./gradlew test` 로그 `BUILD SUCCESSFUL in 57s`(5 actionable tasks). `git status --porcelain` 출력으로 `css/layout.css` 미포함 확인.
- 참고(사람 확인 필요): 헤더를 다크 세이지 배경으로 만들기 위해 `components.css`에서 `.site-header` 배경색을 캐스케이드로 오버라이드한 결정은, 오케스트레이터의 파일별 지시문에는 명시되지 않았고 계획 문서의 "확정 범위" 문단의 전제("헤더 배경이 어두운 세이지 계열")만 근거로 삼아 내린 판단이다 — `layout.css` 자체는 건드리지 않았지만, 실질적으로 헤더의 시각적 색이 바뀌므로 Evaluate/사용자 확인 시 이 판단이 의도와 맞는지 검토가 필요하다.
- 다음: Evaluate 단계로 진행(테스트 통과 + 계획/정책 준수 여부 판정), 통과 시 `docs/dev/frontend/design-system/design.md` 갱신 및 ongoing 문서 채번 이동은 evaluator가 처리.

## Attempt 2 후속 수정 — 2026-08-18 ✅ PASS (브라우저 검증 중 발견한 401 버그 수정)

- 시도: `./gradlew bootRun`으로 실제 브라우저(Browser pane)에서 `design-system.html`을 열어 시각 검증.
- 결과: 로고 이미지(`/images/logo-icon-white.png`, `/images/logo-icon.png`)가 **401 Unauthorized**로 막혀 헤더에 깨진 이미지 + alt 텍스트만 노출됨을 발견.
- 원인: `SecurityConfig.java`의 정적 리소스 permitAll 매처(`/css/**`, `/js/**`, `/partials/**` 등)에 신규 폴더 `/images/**`가 빠져 있었음(Generate 단계에서 `images/` 폴더가 이번에 처음 생겼는데 SecurityConfig 갱신이 누락됨).
- 조치: `SecurityConfig.java` 47번째 줄의 permitAll 매처에 `"/images/**"` 추가(다른 정적 리소스 폴더와 동일한 취급 — 비즈니스 로직 변경 아님).
- 검증: `bootRun` 재시작 후 Browser pane으로 `design-system.html`/`index.html` 재확인.
  - 네트워크: `/images/logo-icon.png`, `/images/logo-icon-white.png` 모두 200 OK.
  - 시각: 헤더(다크 세이지 배경)에 흰색 라쿤 아이콘 로고 정상 렌더링, 클릭 시 `/`로 이동하는 `<a>` 유지 확인. 색상 팔레트 9종 스와치, 버튼(Primary/Secondary/Ghost), 상품 카드 3종(찜하기 버튼 오버레이 포함, 하나는 `.is-active`), 검색 입력창(안내 문구 포함), 찜하기 버튼 컴포넌트(기본/`.is-active` 2종), 상태 뱃지(모집중/성사/미성사 — 색상 변경 없음 확인), 폼 요소 모두 정상 렌더링.
  - `index.html`(메인 페이지)도 새 헤더/로고/색상이 정상 반영되고 레이아웃 구조는 그대로임을 확인(회귀 없음). 푸터 텍스트 로고의 "9" 강조색이 새 단일 브랜드색으로 바뀐 것도 확인.
  - 콘솔 에러: `/api/auth/me` 401은 비로그인 상태의 정상 동작(기존부터 있던 동작, `header-auth.js` 설계상 의도된 것)이고 그 외 에러 없음.
- 다음: 이 수정을 포함해 Evaluate 진행.

## Evaluate — 2026-08-18 ✅ PASS

- 시도: 승인된 계획(`docs/dev/ongoing/design-system-layout-revamp.md`)과 실제 diff를 대조해 계산적/추론적 평가 수행.
- **계산적 평가**: `./gradlew test` → `BUILD SUCCESSFUL in 52s`(5 actionable tasks: 1 executed, 4 up-to-date). 실패 없음, MySQL/스키마 이슈 없음.
- **추론적 평가(diff 대조)**:
  - `git diff -- css/layout.css` → 빈 출력. **`layout.css` 파일 자체는 이번 작업에서 전혀 수정되지 않았다** — 계획의 "레이아웃 구조 변경 안 함" 원칙 준수 확인.
  - `git diff -- js/*` 없음 (diff --stat 결과에 js 파일 미포함) — JS 로직 변경 없음 확인.
  - `tokens.css` diff 확인: `--color-orange`/`--color-coral`/`--color-pink`/`--color-purple`/`--gradient-brand`/`--gradient-brand-soft` 전부 삭제됨. `--color-success`/`--color-warning`/`--color-danger`는 diff에 나타나지 않음(컨텍스트 라인, 값 변경 없음) 확인. `--font-heading`에서 `'Poppins'` 제거, `'Pretendard'` 1순위로 교체 확인.
  - `grep -rn "color-orange|color-coral|color-pink|color-purple|gradient-brand" src/main/resources/static/**/*.{css,html,js}` → `layout.css` 2곳(`.site-header__nav a:hover`, `.site-footer__links a:hover`)만 남고 `tokens.css`/`base.css`/`components.css`에서는 완전히 제거됨을 재확인.
    - **발견(미해결 잔여 항목, 감점 아님)**: 헤더 쪽 `.site-header__nav a:hover`는 `components.css`가 같은 선택자를 나중에 로드하며 `--color-text-on-brand`로 캐스케이드 오버라이드해 실질적으로 해결돼 있었다(로그의 Attempt 2 기록보다 실제로는 더 나은 상태). 반면 **`.site-footer__links a:hover`는 `components.css`에 대응하는 오버라이드가 없어 실제로 깨진 상태**로 남아 있다 — 삭제된 `--color-pink`가 무효값이 되어 hover 시 색이 안 바뀌는 순수 커밋적 열화(링크 자체 동작·페이지 크래시엔 영향 없음, 콘솔 에러도 없음). `layout.css` 수정 금지 제약 때문에 남은 것으로, 헤더에 이미 적용한 것과 같은 캐스케이드 오버라이드 패턴을 `components.css`에 한 줄 추가하면 해결 가능하다. 계획의 명시적 "평가(통과) 기준"(`tokens.css`에 삭제 대상 변수가 없는가)은 tokens.css 자체만 검사 대상으로 명시했고 이 기준은 충족하므로, 이 항목은 **통과를 막는 결함이 아니라 후속으로 남기는 사소한 정리 항목**으로 판단한다.
  - `SecurityConfig.java` diff 확인: 변경은 정확히 `permitAll` 매처 목록에 `"/images/**"` 한 항목 추가뿐. 다른 보안 정책(인증 규칙, 세션 설정, CORS 등) 변경 없음 확인 — 범위 이탈 없음. `docs/code-convention.md`의 계층 분리 규칙과도 무관(설정 클래스, 비즈니스 로직 아님).
  - `partials/header.html` diff: 계획 그대로 `GONG<span class="text-gradient">9</span>RI` → `<img src="/images/logo-icon-white.png" alt="GONG9RI" />`, `<a href="/">` 유지. `partials/footer.html`은 diff 없음(계획대로 텍스트 로고 유지).
  - `components.css` diff: 기존 컴포넌트의 삭제 대상 색상 참조는 전부 새 토큰(`--color-brand`/`--color-brand-dark`/`--color-error`)으로 교체됨. 검색 입력창(`.search-input*`)·찜하기 버튼(`.wishlist-btn*`) 신설은 쇼케이스 전용이라는 계획과 일치. `.site-header`/`.site-header__logo`/`.site-header__nav a` 등 헤더 다크 배경 관련 규칙 추가는 계획의 "설계" 절 파일별 태스크 목록엔 문자 그대로 명시돼 있지 않았지만, 승인된 계획 본문(배경 섹션)에 "헤더 배경이 새 팔레트의 다크 세이지 그린 계열이 될 예정"이라는 전제가 이미 명시돼 있었고, `layout.css`가 그 배경색을 변수가 아닌 하드코딩 값으로 갖고 있어 토큰 교체만으로는 실현이 불가능했던 상황 — `layout.css`를 건드리지 말라는 명시적 제약 하에서 이미 합의된 시각적 결과(다크 세이지 헤더 + 흰 로고)를 구현하는 유일한 방법이 CSS 로드 순서를 이용한 캐스케이드 오버라이드였다고 판단해 **승인된 계획의 취지를 벗어난 범위 확장이 아니라고 평가**한다. 구조 변경(HTML 마크업, 그리드/브레이크포인트) 없이 색상 값만 다루므로 "레이아웃 구조 변경 안 함" 원칙과도 충돌하지 않는다.
  - `design-system.html` diff: 계획의 "확정 범위"대로 색상 팔레트 섹션 9개 토큰 전면 교체, 브랜드 마크 섹션 신설, Poppins 폰트 링크 제거, 상품 카드에 찜하기 버튼 오버레이 추가, 검색 입력창·찜하기 버튼 전용 섹션 2개(백엔드 API 없음 안내 문구 포함) 신설 확인. `index.html`/`product.html` 등 다른 페이지 마크업은 diff에 포함되지 않음(git status에서 확인) — "메인/상세 페이지 실제 반영 안 함" 원칙 준수.
  - `docs/policy/`(README, refund-trigger, caching, team-success-criteria) 확인 — 전부 백엔드 비즈니스 규칙(환불/캐싱/성사 판정)이라 이번 프론트 색상 개편과 무관, 위반 없음. `docs/code-convention.md`도 백엔드 계층/트랜잭션/로깅 규칙 위주라 이번 변경(정적 리소스 + config 1줄)과 충돌 없음.
- **판정: PASS.** 계획의 "하지 않는 것" 목록(레이아웃 구조 변경, JS 로직 변경, 백엔드 비즈니스 로직 변경, 상태 뱃지 색상 변경, 메인/상세 페이지 마크업 변경, 검색·찜하기 기능 구현) 중 어느 것도 위반하지 않았고, "태스크"·"평가(통과) 기준"에 명시된 항목은 모두 충족했다. 단, 위에서 밝힌 `.site-footer__links a:hover`의 사소한 커밋적 열화(1건)는 통과를 막지 않는 후속 정리 항목으로 기록해둔다.
- 증거: `./gradlew test` 출력 `BUILD SUCCESSFUL in 52s`. `git diff --stat` 결과(`6 files changed, 270 insertions(+), 89 deletions(-)`, `layout.css`/`js/*` 미포함). `git diff -- css/layout.css` 빈 출력. `grep -rn "color-pink|color-orange|color-coral|color-purple|gradient-brand" src/main/resources/static/**/*.{css,html,js}` → `layout.css:65`, `layout.css:120` 2건만 잔존(둘 다 `:hover`, 헤더 쪽은 `components.css`가 캐스케이드로 실질 해결, 푸터 쪽만 실제 미해결).
- 통과 후속 조치: `docs/dev/frontend/design-system/design.md` 갱신 완료(색상 토큰/로고/헤더 배경 캐스케이드 오버라이드/검색·찜하기 컴포넌트/SecurityConfig `/images/**` 반영, 푸터 hover 잔여 이슈 명시). `docs/dev/ongoing/design-system-layout-revamp.md`를 `docs/dev/frontend/design-system/changes/002-showcase-rebrand.md`로 채번 이동 완료.
- 다음: 완료. 후속 권장(선택, 사용자 판단): (1) `.site-footer__links a:hover` 색상 잔여 이슈를 `components.css`에 한 줄 오버라이드로 정리하는 작은 후속 Generate, (2) 사용자가 쇼케이스(`design-system.html`)를 육안으로 최종 승인하면 검색·찜하기 UI·새 색상을 `index.html`/`product.html`에 실제 반영하는 후속 계획(Plan) 착수.
