# 001-design-system — 프론트엔드 공통 레이아웃 & 디자인 시스템 (로그)

## Attempt 1 — 2026-08-07

- 시도:
  - 계획 문서(`docs/dev/ongoing/frontend-design-system.md`) 태스크를 그대로 구현.
  - `src/main/resources/static/`에 다음 파일 신규 작성:
    - `css/tokens.css`: 색상(오프화이트 뉴트럴 베이스 + 오렌지/코럴/핑크/퍼플 웜 그라디언트 `--gradient-brand`), 타이포(헤딩 Poppins / 본문 Pretendard) 스케일, 스페이싱, 라운드(8/16/24px), 그림자, 뱃지용 시맨틱 컬러(모집중=warning, 성사=success, 미성사=danger, `docs/db/group_buy_team.md`의 `RECRUITING/SUCCESS/FAILED`에 대응) 토큰 정의.
    - `css/base.css`: 리셋, 전역 타이포 적용, `.text-gradient` 유틸, 포커스 스타일.
    - `css/layout.css`: 모바일 우선(기본 → `min-width: 768px/1024px` 확장) 헤더/푸터/컨테이너/카드 그리드(`.grid-cards`, `.grid-auto`).
    - `css/components.css`: 버튼(primary/secondary/ghost, sm/block variant), 카드(상품카드: 이미지+뱃지+상품명+기본가+베스트공구가), 뱃지(recruiting/success/failed), 폼(input/textarea/label/hint/error).
  - `js/include.js`: `data-include="header|footer"` 속성이 달린 컨테이너를 찾아 `/partials/{name}.html`을 fetch로 가져와 `innerHTML`에 삽입하는 IIFE. fetch 실패 시 콘솔에 에러 로그(삼키지 않음).
  - `js/api.js`: `window.Api.get/post/put/patch/del` 공통 fetch 래퍼 뼈대. base path `/api`, `credentials: 'same-origin'`으로 세션 쿠키 포함, 공통 응답 포맷(`docs/api/README.md`: 성공 `{success:true,data}` / 실패 `{success:false,code,message}`)을 파싱해 실패 시 `code`/`status`를 담은 `Error`를 throw, 성공 시 `data`만 반환. 아직 실제로 호출하는 페이지 없음(뼈대 수준).
  - `partials/header.html`: 로고 + 내비게이션(메인/판매물품등록) + 로그인·회원가입 버튼. 비로그인 상태 고정 마크업(로그인 세션 연동은 계획 문서 4번 항목에 따라 스코프 제외). 개별 페이지(login.html/signup.html/seller/products/new.html)가 아직 없어 href는 placeholder.
  - `partials/footer.html`: 브랜드명, 링크(이용약관/개인정보처리방침/고객센터 — placeholder), 사업자 정보 placeholder, copyright.
  - `design-system.html`: 쇼케이스 페이지. Google Fonts(Poppins) + jsdelivr Pretendard CDN `<link>` 연결, `tokens/base/layout/components.css` 링크, `data-include="header"`/`data-include="footer"`로 partial 삽입 데모, 색상 스와치(bg/surface/orange/coral/pink/purple/gradient-brand) · 타이포 스케일(h1~caption) · 버튼 variant · 상품 카드 3종(모집중/성사/미성사 뱃지 각 1개) · 뱃지 3종 · 로그인/회원가입/상품등록 폼 샘플을 한 페이지에 배치.
  - `SecurityConfig.java`: `authorizeHttpRequests`에 `.requestMatchers("/", "/*.html", "/css/**", "/js/**", "/partials/**").permitAll()`를 기존 `/api/auth/**`, `GET /api/products/**` permitAll 규칙 **위에 추가**(기존 `/api/**` 규칙은 그대로 유지, 순서만 그 앞에 배치). 목적: 비로그인 상태에서 정적 프론트 리소스(`design-system.html` 포함 향후 모든 `*.html`, css/js/partials)를 열람 가능하게 함.
  - `./gradlew compileJava` 성공 확인.
- 결과: ✅ PASS
  - `./gradlew compileJava --rerun-tasks` → `BUILD SUCCESSFUL`.
  - `./gradlew test` → `BUILD SUCCESSFUL`, 15개 테스트 클래스 · 87개 테스트 케이스 전부 통과(failures=0, errors=0, skipped=0). `SecurityConfig` 관련 전용 테스트는 저장소에 존재하지 않음(`src/test`에 `Security` 관련 파일 없음) — 회귀 확인은 전체 스위트 통과로 커버.
  - DB/Redis 환경 이슈 없음: 테스트 실행 로그에 HikariCP 풀 정상 open/close만 보이고 연결 실패 로그 없음 — 이번 판정과 무관한 환경 이슈는 발생하지 않음.
- 원인: (실패 없음 — 해당 없음)
  - 계획 대비 파일 비교: `git status`로 확인한 신규 파일 9개(`css/tokens.css`, `css/base.css`, `css/layout.css`, `css/components.css`, `partials/header.html`, `partials/footer.html`, `js/include.js`, `js/api.js`, `design-system.html`)가 계획 문서 "태스크" 8개 체크박스 + `SecurityConfig` 항목과 1:1 대응, 누락/추가 없음.
  - `git diff -- src/main/java/com/gong9ri/gong9ri/config/SecurityConfig.java` 확인: `.requestMatchers("/", "/*.html", "/css/**", "/js/**", "/partials/**").permitAll()` 한 줄만 기존 `.requestMatchers("/api/auth/**").permitAll()` 규칙 위에 추가됨. 기존 `/api/auth/**`, `GET /api/products/**` permitAll 및 `anyRequest().authenticated()`는 그대로 유지 — 계획 문서 "리스크" 섹션에서 우려한 범위와 정확히 일치, `/api/**` 인가 규칙 변경 없음.
  - 코드 컨벤션: `SecurityConfig`는 기존 생성자 주입(`filterChain(HttpSecurity http, ...)` 파라미터 주입 방식)·기존 fluent 체이닝 스타일을 그대로 유지, 신규 필드나 `@Autowired` 도입 없음 — 위반 없음.
  - 스코프 확인: `partials/header.html`은 비로그인 상태 고정 마크업이고 세션 연동 로직 없음(계획 문서 "4. 헤더 로그인 상태" 경계 준수), `js/api.js`는 아직 어떤 페이지에서도 호출되지 않는 뼈대 상태, 개별 기능 페이지(`login.html`, `signup.html` 등 실제 파일)는 생성되지 않음(header 내 href는 placeholder일 뿐) — 계획에 없던 범위 확장 없음.
- 증거:
  - `./gradlew compileJava --rerun-tasks` → `BUILD SUCCESSFUL in 3s`, `1 actionable task: 1 executed`.
  - `./gradlew test` → `BUILD SUCCESSFUL in 17s`. `build/test-results/test/*.xml` 15개 파일의 `tests`/`failures`/`errors`/`skipped` 합계: tests=87, failures=0, errors=0, skipped=0.
  - `git diff --stat` 요약: `SecurityConfig.java` 1 line 추가(`+1`), 그 외 전부 신규(untracked) 정적 리소스 파일.

## Attempt 2 — 2026-08-07 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 계획 문서 "평가(통과) 기준"의 브라우저 수동 확인 항목을 실제로 수행: `.claude/launch.json`에 `gradlew.bat bootRun` 설정을 추가하고 로컬 MySQL(이미 3306에 기동 중)로 앱을 띄운 뒤, `http://localhost:8080/design-system.html`을 비로그인 상태로 접속해 확인.
- 결과: ❌ FAIL → 🔧 즉시 수정 → ✅ PASS
  - 비로그인 접속 자체는 정상(`SecurityConfig` permitAll 확인됨), 헤더/푸터 partial 삽입 정상, CSS/JS 전부 200 응답, 콘솔 에러 없음(단 `/`에 대한 500은 계획대로 `index.html`을 만들지 않아 발생하는 의도된 상태).
  - 다만 데스크톱/모바일 스크린샷에서 `.section.container`가 적용된 모든 섹션(히어로 타이틀, 색상 팔레트, 타이포그래피 등)의 좌우 여백이 헤더와 달리 화면 끝까지 붙어 있는 시각적 결함 발견.
- 원인:
  - `css/layout.css`에서 `.container { padding: 0 var(--space-4); }`와 `.section { padding: var(--space-7) 0; }`가 동일 specificity(단일 클래스)로 같은 엘리먼트(`class="section container"`)에 적용되는데, 소스 순서상 나중에 나오는 `.section`의 `padding` 축약 선언이 좌우 값(0)까지 포함해 `.container`가 설정한 좌우 패딩을 통째로 덮어씀.
  - `getComputedStyle`로 확인: 수정 전 `paddingLeft`/`paddingRight`가 `0px`(기대값 `16px`, `--space-4`).
- 수정 (같은 접근, 재승인 불필요):
  - `css/layout.css`의 `.section` 규칙을 축약 `padding: var(--space-7) 0;`에서 `padding-top`/`padding-bottom` 개별 선언으로 변경해 `.container`의 좌우 패딩을 더 이상 덮어쓰지 않게 함.
  - `static/` 리소스는 devtools 미도입 상태라 실행 중인 `bootRun`에 반영되지 않아 서버 재시작 후 재확인.
- 증거:
  - 수정 전: `getComputedStyle(document.querySelector('.section.container')).paddingLeft/Right` → `"0px"`/`"0px"`.
  - 수정 후(서버 재시작 후): 동일 조회 → `"16px"`/`"16px"` (`--space-4` 값과 일치).
  - 모바일 뷰포트(375×812)·데스크톱 뷰(758×394 캡처) 스크린샷으로 히어로/색상팔레트/타이포그래피/버튼/카드/뱃지/폼/푸터 전 구간 좌우 여백 정상 확인, `document.documentElement.scrollWidth === clientWidth`(가로 스크롤 없음) 확인.
  - 콘솔 에러: `/` 500(의도된 상태) 외 없음. `css/js/partials` 요청 전부 200.
  - (정성적) 오프화이트 배경 + 오렌지·코럴·핑크·퍼플 그라디언트 포인트, 라운드 카드, 여백감 있는 레이아웃 — "인스타그램 감성" 목표에 부합하는 것으로 판단.
