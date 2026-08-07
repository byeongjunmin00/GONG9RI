# 001-main-page — 메인 페이지 (`/`) (로그)

## Attempt 1 — 2026-08-07

- 시도:
  - 계획 문서(`docs/dev/ongoing/frontend-main-page.md`) 태스크를 그대로 구현.
  - `src/main/resources/static/index.html` 신규 작성:
    - `<head>`는 `design-system.html`과 동일한 폰트 CDN 링크(Poppins/Pretendard) + `tokens/base/layout/components.css` 링크 구성을 그대로 재사용(신규 CSS 파일 없음).
    - `<div data-include="header"></div>` / `<div data-include="footer"></div>`로 공통 헤더/푸터 partial을 그대로 재사용.
    - `main` 안에 히어로 문구(`section container`) + 상태 안내 영역(`#product-status`, 기본 `hidden`) + 카드 그리드 컨테이너(`#product-grid.grid-cards`) + "더 보기" 버튼(`#load-more-btn`, 기본 `hidden`)을 배치.
    - 스크립트 로드 순서: `include.js` → `api.js` → `main.js`(신규).
  - `src/main/resources/static/js/main.js` 신규 작성 — `window.Api.get('/products')` 기반 렌더링 로직:
    - `createProductCard(product)`: `design-system.html`의 `.card`/`.card-image`/`.card-body`/`.card-seller`/`.card-title`/`.card-price-row`/`.card-price-base`/`.card-price-best`/`.card-price-label` 클래스 구조를 그대로 재사용하되, 최상위 엘리먼트를 `<a class="card" href="#">`로 만들어 "상세 페이지 placeholder 링크" 요구를 충족(주석으로 `TODO(상세 페이지 작업 시 갱신)` 명시). `card-image`는 실제 이미지 없이 `.card-image`의 기존 `--gradient-brand-soft` 배경 fallback만 노출(별도 배지 없음 — 목록 API에 공구 상태 필드 없음).
    - 상품명(`name`)·판매자명(`sellerName`)은 전부 `textContent`로만 대입(innerHTML 문자열 조립 없음)해 XSS를 방지. 가격(`basePrice`/`bestPrice`)은 `toLocaleString('ko-KR')` + "원"으로 포맷. `maxParticipants`는 "N인 모이면 1인당 최저가" 문구로 매핑.
    - 상태 처리: `page===0` 요청 시 로딩 문구 표시(`#product-status` + `product-status--loading`) → 성공 시 `content.length===0`이면 빈 상태 문구(`product-status--empty`)로 전환하고 더보기 버튼 숨김, 목록이 있으면 상태 숨기고 카드 렌더링. 실패(`Api.get`이 던지는 `Error`) 시 `err.message`를 `product-status--error`로 노출.
    - "더 보기": `state.page`/`state.loadedCount`/`state.totalElements`를 추적, 클릭 시 `Api.get('/products?page=' + (state.page+1))` 호출 후 응답 `content`를 기존 그리드 뒤에 append. `state.loadedCount >= state.totalElements`가 되면 버튼을 `hidden`. 더보기 요청 실패 시 이미 렌더링된 카드는 유지하고 버튼을 재활성화해 재시도 가능하게 처리(에러 문구만 노출).
  - `src/main/resources/static/css/components.css`에 최소 스타일 보강: `.product-status`(로딩/빈 목록 공통, 텍스트 중앙정렬·`--color-text-muted`)와 `.product-status--error`(`--color-coral` 강조), `.load-more-wrap`(버튼 중앙정렬용 flex 래퍼, `margin-top`만 개별 지정) 추가. 이전 작업에서 발견된 축약 `padding`/`margin` specificity 버그를 재발시키지 않도록, 새 규칙은 기존 클래스와 결합되는 지점에 축약 선언을 쓰지 않고 개별 side 프로퍼티(`margin-top`/`margin-bottom`)만 사용.
  - `js/api.js`, `js/include.js`, `css/tokens.css`/`base.css`/`layout.css`는 전혀 수정하지 않음(계획 전제 유지).
  - `./gradlew compileJava` 실행 → `BUILD SUCCESSFUL`(자바 소스 변경이 없어 `UP-TO-DATE`) 확인.
  - 브라우저 수동 확인(`bootRun` 기동 후 실제 접속 테스트)은 이번 Generate 단계에서 수행하지 않음 — Evaluate 단계 몫으로 남김.

- 결과: **PASS** (계산적 평가 통과 + 추론적 평가상 계획·정책 위반 없음. 단, 브라우저 수동 확인은 Evaluate 담당자 역할이 아니라 호출자 몫으로 남겨둠).

- 원인/근거:
  - **계산적 평가**:
    - `./gradlew compileJava` → `BUILD SUCCESSFUL`(`UP-TO-DATE`, 자바 소스 변경 없음 확인).
    - `./gradlew test` → `BUILD SUCCESSFUL`. `build/test-results/test/*.xml` 15개 스위트 합산 tests=87, failures=0, errors=0, skipped=0 — design-system 단계(직전 Attempt)와 동일한 수치로 회귀 없음 확인.
  - **`git status`/`git diff`로 실제 변경 범위 확인**:
    - `git status --porcelain`: `M src/main/java/.../SecurityConfig.java`(1줄 변경) + `?? src/main/resources/static/`(design-system 단계부터 미커밋 상태로 남아있던 정적 리소스 전체, 이번 태스크의 `index.html`/`js/main.js`/`css/components.css` 포함) + `?? docs/dev/frontend/`, `?? docs/logs/frontend/`(문서) + `?? .claude/launch.json`(무관한 로컬 툴링 파일, 이번 작업과 관계없음).
    - `SecurityConfig.java` 변경(`.requestMatchers("/", "/*.html", "/css/**", "/js/**", "/partials/**").permitAll()`)은 **이번 main-page Generate에서 새로 만든 것이 아니라 직전 design-system 단계에서 이미 만들고 평가·문서화된 변경**임을 확인했다(`docs/logs/frontend/design-system/001-design-system.md`, `docs/dev/frontend/design-system/design.md`에 동일 diff가 이미 기록돼 있음; `git diff` 결과도 그 문서의 diff와 정확히 일치). 파일 mtime 비교(`tokens.css`/`base.css`/`partials/*`/`include.js`/`api.js`/`design-system.html`/`layout.css`는 06:27~06:52, `index.html`/`main.js`/`components.css`는 07:08)로 이번 세션에서 실제로 건드린 파일은 계획된 3개뿐임을 확인 — 이번 태스크의 "자바 도메인 로직 변경 없음" 서술과 배치되지 않는다(자바 파일 자체는 이번 세션에서 수정하지 않았고, 남아있던 이전 단계의 미커밋 diff일 뿐).
  - **추론적 평가 — 계획 대비 일치 확인**:
    - `src/main/resources/static/index.html`: 헤더/푸터 partial include, `#product-status`(기본 `hidden`), `#product-grid.grid-cards`, `#load-more-btn`(기본 `hidden`) 구조 확인. 스크립트 로드 순서 `include.js` → `api.js` → `main.js` 확인. 신규 CSS 파일 링크 없음(`tokens/base/layout/components.css`만 재사용) — 계획 1항과 일치.
    - `src/main/resources/static/js/main.js`:
      - 카드 링크: `link.href = '#'`로 placeholder 확정, `TODO(상세 페이지 작업 시 갱신)` 주석 포함 → **실제 `/products/{id}` 이동 없음, 계획 3항 준수**.
      - 공구 상태 뱃지: `createProductCard`에 `.badge`/`badge-recruiting` 등 클래스 사용 없음 → **메인 카드에 상태 뱃지 미부착, 계획 3항 준수**.
      - 로그인 상태 연동: `main.js` 전체에 로그인/인증 관련 분기·API 호출 없음(오직 `/products` GET만 호출) → **로그인 상태 연동 로직 추가 없음, 계획 3항 준수**.
      - "더 보기" 로직: `fetchProducts(page)`가 `state.page`/`state.loadedCount`/`state.totalElements`를 추적, 클릭 시 `page+1` 요청 후 `renderProducts`로 append, `updateLoadMoreButton`이 `loadedCount >= totalElements`일 때 버튼 `hidden` 처리 → **계획 3항의 "더 보기" 요구사항 충족**.
      - XSS 방지: `product.sellerName`/`product.name`을 `sellerEl.textContent`/`titleEl.textContent`로만 대입, `innerHTML` 사용 없음(파일 전체에 `innerHTML` 문자열 미검출) → **계획 리스크 항목("사용자 입력 문자열 XSS 방지") 준수**.
      - `js/api.js` 확인: `API_BASE = '/api'` + `fetch(API_BASE + path, ...)` 구조이므로 `main.js`의 `PRODUCTS_PATH = '/products'` 호출은 실제로 `GET /api/products`를 호출한다 — `docs/api/product.md` 계약과 일치.
    - `src/main/resources/static/css/components.css`: `.product-status`/`.product-status--error`/`.load-more-wrap`만 신규 추가, 기존 규칙(`.btn`/`.card`/`.badge`/`.form-*`)은 수정 없음. 신규 규칙이 사용하는 토큰(`--space-5`, `--space-6`, `--color-coral`, `--fs-sm` 등)은 `tokens.css`에 이미 정의돼 있어 깨진 참조 없음.
    - `js/api.js`, `js/include.js`, `css/tokens.css`, `css/base.css`, `css/layout.css`: 파일 mtime상 이번 세션에서 수정되지 않았고 내용도 design-system 단계 산출물 그대로 확인 — **계획 리스크 항목("기존 동작 변경 없음") 준수**.
  - **코드 컨벤션(`docs/code-convention.md`)**: 이번 작업은 정적 프론트엔드 파일(HTML/CSS/JS)만 다루고 `docs/code-convention.md`는 Java/Spring 계층 규칙 위주라 직접 해당하는 항목이 없음. 위반 없음(해당 없음).

- 증거(API 샘플):
  - `docs/api/product.md`의 `GET /api/products` 응답 계약(요약): `content[].{productId, name, basePrice, bestPrice, maxParticipants, sellerName, createdAt}` + `page`/`size`/`totalElements` — `main.js`가 매핑하는 필드(`name`/`basePrice`/`bestPrice`/`sellerName`/`maxParticipants`) 전부 이 계약 안에 있고, 계약에 없는 이미지 URL·공구 상태 필드는 참조하지 않음(계획 3항과 일치).
  - `./gradlew test` 결과 스위트별 tests 합계: 1+2+8+7+13+12+11+13+2+5+1+5+1+1+5 = **87**, failures/errors/skipped 전부 0 (15개 테스트 클래스, design-system 단계 로그의 수치와 동일).

- 후속 조치(통과에 따른 문서화):
  - `docs/dev/frontend/main-page/design.md`(SSOT) 신규 작성 필요.
  - `docs/dev/ongoing/frontend-main-page.md` → `docs/dev/frontend/main-page/changes/001-main-page.md`로 채번 이동 필요.
  - (참고) 브라우저 수동 확인(계획 문서 "평가(통과) 기준" 항목: `bootRun` 후 실제 렌더링/Network/콘솔/반응형 확인)은 이 Evaluate 단계에서 수행하지 않았다 — 호출자가 직접 확인해야 계획 문서의 "평가(통과) 기준"이 완전히 충족된다.

## Attempt 2 — 2026-08-07 (평가 기준의 브라우저 수동 확인)

- 시도:
  - `.claude/launch.json`(design-system 단계에서 만든 `gradlew.bat bootRun` 설정)으로 앱을 띄워 `http://localhost:8080/`을 비로그인 상태로 접속.
  - 최초 접속 시 로컬 Redis 미기동으로 `GET /api/products`가 500(`RedisConnectionFailureException`) — 이는 이번 작업과 무관한 로컬 환경 이슈이므로, Docker Desktop을 띄우고 임시 `redis:7` 컨테이너(`gong9ri-eval-redis`, 포트 6379)를 실행해 정상 접속 가능한 상태로 만들었다.
  - 빈 목록 상태 확인 → 로컬 MySQL(기존 `docker run mysql:8 mysql` 클라이언트로 host.docker.internal:3306 접속)에 평가용 판매자/상품/가격구간 테스트 행을 임시로 넣어 "목록 있음" 상태와 "더 보기" 동작을 확인 → 확인 후 전부 삭제(정리).
- 결과: 부분 ❌ FAIL(버그 1건 발견) → 🔧 즉시 수정 → ✅ 전체 PASS
  - **"더 보기" 버튼이 `hidden`인데도 항상 보임**(실재 버그): 최초 접속 시(Redis 미기동으로 에러 상태) `#load-more-btn`이 `hidden` 속성(`class="btn btn-secondary" hidden`)을 가지고 있는데도 화면에 노출되는 것을 발견. `css/components.css`의 `.btn { display: inline-flex; ... }`가 클래스 선택자라 브라우저 기본 `[hidden] { display: none }`(UA 스타일, 매우 낮은 우선순위)보다 우선순위가 높아 `hidden`이 무력화됨. `getComputedStyle`로 확인: 수정 전 `btn.hidden === true`인데 `display: "flex"`(보임).
  - (`.section.container` 좌우 패딩은 이번 세션에서 별도 문제 없이 처음부터 `16px`로 정상 계산됨을 확인했다 — design-system 단계에서 이미 고친 `.section`의 개별 side 패딩 선언이 main-page에도 그대로 적용돼 있어 재발 없음.)
- 원인:
  - design-system 단계에서 겪은 것과 **동일한 패턴**(클래스 선택자의 `display` 값이 속성 선택자(`[hidden]`)의 브라우저 기본값을 specificity로 덮어씀)이 이번엔 `.btn` 컴포넌트에서 재현.
- 수정 (같은 접근, 재승인 불필요):
  - `css/components.css`에 `.btn[hidden] { display: none; }` 규칙을 `.btn` 규칙 **앞에** 추가(클래스+속성 선택자로 specificity를 `.btn` 단독보다 높여 `hidden`이 항상 이기게 함).
  - 서버 재시작(devtools 미도입이라 정적 리소스 변경은 재시작해야 반영됨) 후 재확인.
- 증거:
  - 수정 전: 에러 상태(`btn.hidden === true`)에서 `getComputedStyle(loadMoreBtn).display` → `"flex"`(보임, 버그).
  - 수정 후: 동일 에러 상태에서 `display: "none"` 확인. 빈 목록 상태(`hidden=true`)에서도 `display: "none"` 확인.
  - **정상 렌더링 확인**(로컬 임시 테스트 데이터, 이후 삭제): 상품 1건 등록 후 카드에 `상품명`/`판매자명`/`기본가 25,000원`/`베스트가 15,000원`/`"10인 모이면 1인당 최저가"` 라벨이 API 응답 필드(`name`/`sellerName`/`basePrice`/`bestPrice`/`maxParticipants`)와 정확히 일치해 렌더링됨을 확인. (최초 1회 셸에서 직접 INSERT 시 클라이언트 charset을 안 맞춰 한글이 깨진 적이 있었는데, 이는 순수 테스트 데이터 입력 실수였고 `--default-character-set=utf8mb4`로 재입력 후 정상 확인됨 — 프론트/백엔드 코드 문제 아님.)
  - **"더 보기" 동작 확인**: 상품 25건(`totalElements=25`, `size=20` 기본값)으로 첫 로드 시 카드 20개 + 버튼 노출(`display:flex`) 확인 → 버튼 클릭 → `GET /api/products?page=1` → `200` → 카드 25개로 증가 + 버튼 자동 숨김(`display:none`) 확인.
  - **Network/콘솔**: `GET /api/products` 최신 요청들은 전부 `200`(Redis 기동 이후). 콘솔에는 Redis 미기동 시점의 과거 500 에러 로그만 남아있고, 이후 신규 에러 없음.
  - **모바일 뷰(375×812)**: `document.documentElement.scrollWidth === clientWidth`(가로 스크롤 없음), `.section.container` 좌우 패딩 `16px` 확인, `#product-grid`의 `grid-template-columns`가 2열(`163.5px 163.5px`)로 축소 확인.
  - 평가 종료 후 임시 Redis 컨테이너(`gong9ri-eval-redis`)와 MySQL 테스트 행(판매자/상품 26건/가격구간)을 모두 정리(삭제)함 — 로컬 DB에 잔여 데이터 없음.
