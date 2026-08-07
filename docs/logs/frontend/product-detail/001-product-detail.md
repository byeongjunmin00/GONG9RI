# 001-product-detail — 상품 상세조회 페이지 (`/products/{id}`) (로그)

## Attempt 1 — 2026-08-07 ✅ PASS (코드 리뷰 기준 — 브라우저 수동 확인은 별도)

- 시도:
  - 계획 문서(`docs/dev/ongoing/product-detail.md`) 태스크를 그대로 구현.
  - `src/main/resources/static/product.html` 신규 작성:
    - `<head>`는 `index.html`/`login.html`과 동일한 폰트 CDN 링크 + `tokens/base/layout/components.css` 링크 구성을 그대로 재사용(신규 CSS 파일 없음).
    - `<div data-include="header"></div>` / `<div data-include="footer"></div>`로 공통 헤더/푸터 partial 재사용. 헤더 로그인 상태 연동은 손대지 않음(partial 자체도 수정 없음).
    - 공통 안내 배너: `#page-alert.form-alert`(기본 `hidden`) 내부에 `#page-alert-text`(문구용 span)와 `#page-alert-login-link`(401 시에만 노출하는 로그인 페이지 링크, 정적 텍스트라 XSS 우려 없음)를 분리해 배치 — `login.js`/`signup.js`의 `.form-alert`/`.form-alert--error`/`.form-alert--success` 패턴 재사용.
    - 잘못된 접근/상품 없음/로딩/에러 상태: `#product-status.product-status`(기본 `hidden`, `main.js`의 상태 안내 패턴 재사용). 이 상태가 보이는 동안 `#product-detail`(기본 `hidden`)은 숨겨둔다.
    - 상품 정보 영역(`#product-detail` 내부): 판매자명(`#product-seller`)·상품명(`#product-name`, `h1`)·설명(`#product-description`)·기본가(`#product-base-price`, `.card-price-base` 재사용)·최대 인원(`#product-max-participants`)·가격구간표(`#price-tiers-table`, 기본 `hidden` — `priceTiers`가 비어 있을 가능성에 대비).
    - 액션 버튼 영역: "혼자 구매하기"(`#buy-alone-btn`, `.btn-secondary`), "신규 팀 신설하기"(`#create-team-btn`, `.btn-primary`), "계속 쇼핑하기"(`<a href="/">`, `.btn-ghost`).
    - 팀 목록 영역: `#team-status.product-status`(로딩/빈 목록/에러) + `#team-list`(`<ul>`, 항목은 `js`가 렌더링).
    - 스크립트 로드 순서: `include.js` → `api.js` → `product.js`(신규).
  - `src/main/resources/static/js/product.js` 신규 작성:
    - 쿼리 파라미터 파싱: `parseProductId()`가 `new URLSearchParams(location.search)`로 `id`를 읽어 `/^[1-9]\d*$/` 정규식으로 검증(없음/숫자아님/0/음수/소수 전부 거부) → 실패 시 API 호출 없이 `showStatus('잘못된 접근입니다...', 'error')`만 호출하고 종료.
    - `loadProduct(productId)`: `Api.get('/products/' + productId)` → 성공 시 `renderProduct()`로 렌더링 후 `detailEl.hidden = false`, 이어서 `loadTeams(productId)` 호출(순차 처리 — 상품이 없으면 팀 조회를 시도하지 않도록). 실패 시 `err.code === 'PRODUCT_NOT_FOUND'`면 "상품을 찾을 수 없습니다", 그 외는 `err.message`를 에러 상태로 노출.
    - `renderProduct(product)`: `sellerName`/`name`/`description`은 전부 `textContent`로만 대입. `basePrice`는 `toLocaleString('ko-KR')+'원'` 포맷(main.js의 `formatPrice`와 동일 패턴). `priceTiers` 배열을 순회해 `<tr><td>{minCount}명 이상</td><td>{price 포맷}</td></tr>` 행을 `createElement`+`textContent`로 구성(innerHTML 미사용), 배열이 비어 있으면 테이블 자체를 숨김.
    - `loadTeams(productId)`: `Api.get('/products/' + productId + '/teams')` → 빈 배열이면 "아직 모집 중인 공구팀이 없습니다"(에러 아닌 empty 상태), 아니면 각 팀을 `createTeamItem()`으로 렌더링. 팀 항목은 상태 뱃지(`statusToBadgeClass`/`statusToLabel`이 `RECRUITING→badge-recruiting/모집중`, `SUCCESS→badge-success/모집완료`, `FAILED→badge-failed/모집실패`로 일반화 매핑) + 현재/최대 인원(`currentCount / maxParticipants`) + "참가하기" 버튼(`btn btn-secondary btn-sm`)으로 구성.
    - "참가하기"(`handleJoin`): `Api.post('/teams/' + teamId + '/join')` → 성공 시 성공 배너 + `loadTeams()` 재조회. 실패 시 `handleActionError()`로 위임.
    - "신규 팀 신설하기"(`handleCreateTeam`): `Api.post('/products/' + id + '/teams')` → 성공 시 성공 배너 + `loadTeams()` 재조회(결제 페이지로의 이동/호출 없음 — 계획의 스코프 경계 그대로). 실패 시 `handleActionError()`로 위임. 성공/실패 어느 쪽이든 버튼은 다시 활성화.
    - `handleActionError(err, productId)`: 계획 문서의 매핑을 그대로 구현 — `401`/`UNAUTHORIZED` → "로그인이 필요합니다" + 로그인 링크 노출(`showPageAlert(..., true)`); `403`/`FORBIDDEN` → 서버 `message` 그대로 노출; `409`+(`TEAM_FULL`|`ALREADY_JOINED`) → 서버 `message` 노출 + `loadTeams()` 재조회; `404` → 서버 `message` 노출 + `loadTeams()` 재조회; 그 외는 서버 `message`(또는 fallback 문구)만 노출.
    - "혼자 구매하기"(`handleBuyAlone`): API 호출도 페이지 이동도 없이 `showPageAlert('결제 기능은 준비 중입니다.', 'success')`만 실행.
    - 신뢰할 수 없는 문자열(`sellerName`/`name`/`description`/서버 `message`) 전부 `textContent`로만 DOM에 대입 — 파일 전체에 `innerHTML` 사용 없음(직접 검색 확인).
  - `src/main/resources/static/css/components.css`에 상세 페이지 전용 최소 스타일 추가: `.product-detail`(세로 flex 간격), `.product-price-box`/`.product-price-row`(가격 정보 카드형 박스), `.price-tiers-table`(+`th`/`td`, `border-collapse` 테이블 — `display` 미선언이라 기존 `.btn[hidden]`류의 specificity 보정 불필요), `.product-actions`(버튼 가로 flex), `.team-list`/`.team-item`/`.team-item-info`/`.team-item-count`(팀 목록 항목 레이아웃). 기존 `.btn`/`.card`/`.badge`/`.form-alert`/`.product-status` 등 규칙은 수정하지 않음.
  - `src/main/resources/static/js/main.js`: `createProductCard()`의 카드 `href`를 `'#'` placeholder에서 `'product.html?id=' + product.productId`로 교체하고, `TODO(상세 페이지 작업 시 갱신)` 주석과 "(준비 중)" `aria-label` 문구를 제거. 이 외 로딩/빈 목록/에러 처리, "더 보기" 페이지네이션 로직은 손대지 않음.
  - `js/api.js`, `js/include.js`, `css/tokens.css`/`base.css`/`layout.css`, `partials/header.html`/`footer.html`은 전혀 수정하지 않음(계획 전제 유지).
  - `./gradlew compileJava` 실행 → `BUILD SUCCESSFUL`(`UP-TO-DATE`, 이번 작업이 정적 리소스/JS만 다뤄 자바 소스 변경 없음).
  - 브라우저 수동 확인(`bootRun` 후 실제 렌더링/401·403·409·404 재현/반응형 확인)은 이번 Generate 단계에서 수행하지 않음 — Evaluate 단계 몫으로 남김.

- 결과:
  - **계산적 평가**: `./gradlew compileJava` → `BUILD SUCCESSFUL`(`UP-TO-DATE`, 이번 작업은 정적 리소스/JS만 다뤄 Java 소스 변경 없음). `./gradlew test`는 이번 Evaluate에서 실행하지 않음(동시 진행 중인 `auth-logout` 작업이 `AuthController.java`/`AuthControllerTest.java`를 미완성 상태로 수정 중이라 무관한 실패가 섞일 수 있어 호출자 지시대로 제외).
  - **추론적 평가**: 대상 파일(`product.html`, `js/product.js` 신규, `js/main.js`/`css/components.css` 변경분)만 diff/전체 내용을 검토. 계획 문서(`docs/dev/ongoing/product-detail.md`)와 일치하며 스코프 이탈 없음. 세부:
    - `js/main.js` diff는 `createProductCard()`의 카드 `href`(`'#'` → `'product.html?id=' + product.productId`)와 `aria-label` 문구, 주석 1곳만 변경 — 로딩/빈 목록/에러 처리, "더 보기" 페이지네이션 등 다른 로직은 diff에 전혀 나타나지 않음(무변경 확인).
    - `css/components.css` diff는 파일 끝에 `.product-detail`/`.product-price-box`/`.product-price-row`/`.price-tiers-table`(+`th`/`td`)/`.product-actions`/`.team-list-section`/`.team-list`/`.team-item`/`.team-item-info`/`.team-item-count` 신규 규칙만 추가 — 기존 규칙(`.btn`/`.card`/`.badge`/`.form-alert`/`.product-status` 등) 수정 없음(무변경 확인).
    - `product.js`의 `parseProductId()`가 `/^[1-9]\d*$/`로 없음/비숫자/0/음수/소수를 모두 거부하고, 실패 시 `Api` 호출 없이 즉시 `showStatus(...)` 후 `return` — 계획의 "API 호출 없이 잘못된 접근 안내" 그대로 구현됨.
    - `GET /api/products/{id}` → 성공 시 렌더링 후 `GET /api/products/{id}/teams`를 순차 호출(상품이 없으면 팀 조회 시도 안 함), `PRODUCT_NOT_FOUND`(404)는 전용 안내로 분기, 팀 목록 빈 배열은 에러 아닌 empty 상태로 분기 — 계획과 일치.
    - `docs/api/product.md`/`docs/api/team.md`와 필드명 대조: `productId`/`sellerName`/`name`/`description`/`basePrice`/`maxParticipants`/`priceTiers[].minCount,price`(상품), `teamId`/`currentCount`/`maxParticipants`/`status`(팀) 전부 API 명세와 정확히 일치. 에러 코드도 `PRODUCT_NOT_FOUND`/`TEAM_NOT_FOUND`/`TEAM_FULL`/`ALREADY_JOINED`/`FORBIDDEN`/`UNAUTHORIZED`로 명세와 일치.
    - `js/api.js`(무수정, 기존 공통 래퍼)가 `{success,data}`/`{success:false,code,message}` 래핑을 이미 벗겨 `.status`/`.code`/`.message`를 담은 `Error`를 throw하므로, `handleActionError`의 `err.status`/`err.code`/`err.message` 참조와 실제 계약이 맞음. `docs/api/README.md`의 "예시는 data 안 내용만 표시(실제는 래핑됨)" 컨벤션과도 상충하지 않음(팀 목록 API의 문서 예시가 배열만 보여준 것도 이 컨벤션에 따른 축약 표기일 뿐, `api.js`가 정상적으로 배열을 벗겨냄을 코드로 확인).
    - 참가(`handleJoin`)/신설(`handleCreateTeam`) 모두 `Api.post`만 호출하고 성공 시 성공 배너 + `loadTeams()` 재조회, 결제 페이지로의 이동/호출 없음 — 계획의 "팀 신설 성공 후 결제로 이어가지 않는다" 스코프 경계 그대로.
    - `handleActionError()`가 401→로그인 안내+링크, 403→서버 message 그대로, 409(`TEAM_FULL`/`ALREADY_JOINED`)→message+재조회, 404→message+재조회, 그 외→message로 분기 — 계획의 "액션 처리 방향" 표와 1:1 대응.
    - "혼자 구매하기"(`handleBuyAlone`)는 `showPageAlert('결제 기능은 준비 중입니다.', 'success')` 한 줄뿐 — `fetch`/`Api.*` 호출도 `location.href` 이동도 없음(코드에 해당 호출 자체가 존재하지 않음, 직접 확인).
    - `product.js` 전체에 `innerHTML` 문자열이 전혀 없음(`grep innerHTML` 결과 0건). 서버발 신뢰 불가 문자열(`sellerName`/`name`/`description`/`message`)과 계산된 값 전부 `textContent` 대입 또는 `createElement`+`textContent` 조합으로만 DOM에 반영.
    - `product.html`은 `login.html`/`index.html`과 동일한 `<head>` CDN 링크 구성 + `data-include="header"/"footer"` partial 재사용 패턴을 그대로 따름. `partials/header.html`/`footer.html`, `js/api.js`, `js/include.js`, `css/tokens.css`/`base.css`/`layout.css`는 `git status`상 무변경 확인.
    - `docs/code-convention.md`는 Java/Spring 계층 규칙 위주라 이번 정적 프론트 변경분(HTML/CSS/JS)에 직접 적용되는 항목은 없음 — 위반 없음(해당 사항 없음).
  - **격리 확인**: `git status`상 `SecurityConfig.java`/`AuthController.java`/`AuthControllerTest.java`의 미커밋 변경은 동시 진행 중인 `auth-logout` 작업 소관으로 확인, 이번 평가에서 열람/판정 대상에서 완전히 제외.
- 원인: (해당 없음 — FAIL 아님)
- 증거(API 샘플, 명세 대조 기준):
  - `GET /api/products/{id}` 성공 예시(`docs/api/product.md`): `{"productId":1,"sellerName":"제주농장","name":"제주 감귤 5kg","basePrice":25000,"maxParticipants":10,"priceTiers":[{"minCount":2,"price":22000},...]}` → `product.js`의 `renderProduct()`가 이 필드들을 그대로 소비.
  - `GET /api/products/{id}/teams` 성공 예시: `[{"teamId":3,"productId":1,"leaderId":7,"currentCount":4,"maxParticipants":10,"status":"RECRUITING",...}]`(`RECRUITING`만 반환) → `loadTeams()`/`createTeamItem()`이 이 필드들을 소비.
  - `POST /api/teams/{teamId}/join` 실패 예시: `409 {"success":false,"code":"TEAM_FULL","message":"..."}`, `409 {"code":"ALREADY_JOINED",...}`, `403 {"code":"FORBIDDEN",...}`, `401 {"code":"UNAUTHORIZED",...}`, `404 {"code":"TEAM_NOT_FOUND",...}` → `handleActionError()`의 분기와 코드 매핑이 전부 일치.
  - `./gradlew compileJava` 실행 로그: `> Task :compileJava UP-TO-DATE` / `BUILD SUCCESSFUL in 1s`.
  - `js/main.js` diff(전체): 카드 `href`/`aria-label`/주석 1곳만 변경(그 외 0줄 변경) — `git diff -- src/main/resources/static/js/main.js` 결과로 확인.
  - `css/components.css` diff(전체): 파일 끝에 상세 페이지 전용 규칙 80줄 추가만 존재, 기존 규칙 삭제/수정 0줄 — `git diff -- src/main/resources/static/css/components.css` 결과로 확인.
- 다음: (없음 — 이번 Attempt로 코드 리뷰 기준 통과. 단, `docs/dev/ongoing/product-detail.md`의 "평가(통과) 기준"에 나열된 브라우저 수동 확인 항목들은 이 Evaluate 단계 범위 밖이며 호출자가 별도로 `bootRun` 후 확인해야 한다.)

## Attempt 2 — 2026-08-07 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 로컬 MySQL + 임시 `redis:7` 컨테이너로 `bootRun` 기동. 테스트용 판매자·상품 2개(팀 있음/없음)를 SQL로 삽입(`utf8mb4` charset 명시), 테스트용 구매자·판매자 계정은 실제 `signup.html`/`login.html`을 통해 생성·로그인(회원가입/로그인 자체도 재검증).
  - 존재하지 않는 id, 비숫자 id, 상품 있음(팀 없음), 상품 있음(팀 신설 후 있음), 중복 참가(`ALREADY_JOINED`), 비로그인 신설 시도(401), 판매자 계정 신설 시도(403), "혼자 구매하기", 메인페이지 카드 클릭 이동, 모바일 뷰를 각각 확인.
  - 401 재현을 위해 서버를 재기동해 인메모리 세션을 초기화(로그아웃 API가 처음엔 없다고 착각했으나, 확인 과정에서 동시 진행 중이던 `auth-logout` 배경 작업이 이미 `POST /api/auth/logout`을 구현·병합한 상태였음을 발견하고 그 API로도 재확인).
  - 확인 후 테스트 계정(`eval_seller_pd`/`pdbuyer1`/`pdseller1`)·상품(893/894)·가격구간·팀·참가 기록과 임시 Redis 컨테이너를 전부 정리(삭제).
- 결과: 부분 ❌ FAIL(버그 1건 발견) → 🔧 즉시 수정 → ✅ 전체 PASS
  - **`.product-detail`이 `hidden`인데도 항상 보임**: 상품 미존재(404) 상태에서 `#product-detail`이 `hidden` 속성을 갖는데도 가격 박스/액션 버튼/팀 목록 영역이 빈 값으로 그대로 노출됨. `css/components.css`의 `.product-detail { display: flex; }`가 클래스 선택자라 브라우저 기본 `[hidden]{display:none}`을 specificity로 이김 — design-system(`.btn[hidden]`) 단계에서 이미 겪은 것과 동일한 패턴이 세 번째로 재현.
- 원인: 위와 동일(클래스 선택자의 `display` 선언이 `[hidden]`의 낮은 기본 우선순위를 덮어씀).
- 수정 (같은 접근, 재승인 불필요): `css/components.css`에 `.product-detail[hidden] { display: none; }`을 `.product-detail` 규칙 앞에 추가. 서버 재시작(devtools 없음) 후 재확인 — 수정 전 `getComputedStyle().display === "flex"`, 수정 후 `"none"`.
- 증거:
  - **상품 없음/잘못된 id**: 수정 후 "상품을 찾을 수 없습니다."만 노출, 빈 가격/버튼 잔재 없음. `id=abc`는 API 호출 자체가 발생하지 않음(Network 탭에 `/api/products/abc` 없음).
  - **상품 있음(팀 없음)**: 판매자명/상품명/설명/기본가/가격구간표(2명 이상~10명 이상) 삽입 데이터와 정확히 일치해 렌더링. 팀 영역은 "아직 모집 중인 공구팀이 없습니다." 빈 상태(에러 아님).
  - **팀 신설 성공**: `pdbuyer1`(BUYER) 로그인 상태로 신설 → "신규 공구팀을 만들었습니다." + 목록에 "모집중 1/5명 참가하기" 즉시 반영.
  - **중복 참가**: 같은 팀에 재참가 시도 → "이미 참가한 공구팀입니다."(`ALREADY_JOINED`).
  - **401**: 세션 무효화 후 신설 시도 → "로그인이 필요합니다." + `#page-alert-login-link`가 `hidden=false`/`display:inline`으로 실제로 보이고 `href="/login.html"`.
  - **403**: `pdseller1`(SELLER) 로그인 상태로 신설 시도 → "접근 권한이 없습니다.", 이때는 로그인 링크가 `hidden=true`/`display:none`으로 정확히 숨겨짐(401/403 두 케이스가 같은 배너 엘리먼트를 쓰면서도 로그인 링크 표시 여부는 정확히 분기됨).
  - **혼자 구매하기**: 클릭해도 `location.href` 불변(이동 없음), "결제 기능은 준비 중입니다." 안내만 노출.
  - **메인페이지 연동**: `index.html`의 상품 카드 `href` 속성이 `product.html?id=893`으로 정확히 생성되고, 클릭 시 해당 상품 상세로 정상 이동.
  - **모바일(375×812)**: `document.documentElement.scrollWidth === clientWidth`(가로 스크롤 없음).
  - **콘솔**: 이번 플로우에서 새로 발생한 처리되지 않은 에러 없음(남아있던 메시지는 이전 세션 단계들의 것).
  - 평가 종료 후 모든 테스트 계정/상품/팀 데이터와 임시 Redis 컨테이너 정리 완료.
