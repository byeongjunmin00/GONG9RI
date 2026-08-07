# 001-checkout — 결제창 페이지 (`/checkout`) (로그)

## Attempt 1 — 2026-08-07

- 시도:
  - 계획 문서(`docs/dev/ongoing/checkout.md`) 태스크를 그대로 구현. `docs/api/payment.md`의 `FORBIDDEN`(403) 보강은 Plan 단계에서 이미 완료된 상태라 재작업 없음.
  - `src/main/resources/static/checkout.html` 신규 작성:
    - `<head>`는 `product.html`/`login.html`과 동일한 폰트 CDN + `tokens/base/layout/components.css` 링크 구성을 그대로 재사용(신규 CSS 파일 없음). `<div data-include="header/footer">` partial도 그대로 재사용.
    - 공통 안내 배너: `#page-alert.form-alert`(기본 `hidden`) + `#page-alert-text`(문구) + `#page-alert-login-link`(401 시에만 노출, `product.html`과 동일 패턴). checkout에는 결제 이동 링크 슬롯이 필요 없어(이 페이지 자체가 결제 목적지) 추가하지 않음.
    - 잘못된 접근/상품 없음/로딩/에러 상태: `#checkout-status.product-status`(기본 `hidden`, `product-status` 클래스 재사용).
    - 상품 정보 확인 영역: `#checkout-detail`(`class="product-detail"`, 기본 `hidden`) 내부에 판매자명(`#checkout-seller`)·상품명(`#checkout-name`)·`.product-price-box` 재사용 가격 영역 — `#checkout-amount-label`(결제 금액/정가(참고) 두 고정 문자열만 토글, 서버 데이터 아님)·`#checkout-amount`(`card-price-best`)·`#checkout-team-notice`(`.form-hint`, 공구팀 결제 시에만 노출하는 안내 문구, 기본 `hidden`)·`#checkout-price-tiers-table`(`.price-tiers-table` 재사용, 참고용 구간표).
    - 액션 버튼: `#pay-btn`(`.btn-primary`, "결제하기"), `#cancel-btn`(`.btn-secondary`, "삭제(취소)").
    - 결제 완료 요약 영역: `#checkout-summary`(`class="product-detail"`, 기본 `hidden`) — `.product-price-box` 재사용해 상품명/금액/상태/결제시각 4행 + "계속 쇼핑하기"(`<a href="/">`, `.btn-ghost`) 링크.
    - 스크립트 로드 순서: `include.js` → `api.js` → `checkout.js`(신규).
    - **`[hidden]` 보정 규칙**: `#checkout-detail`/`#checkout-summary`는 신규 클래스를 만들지 않고 이미 `.product-detail[hidden] { display: none; }` 보정이 적용돼 있는 기존 `.product-detail` 클래스를 그대로 재사용해 처음부터 이 버그를 회피(신규 CSS 추가 없이 해결). `#checkout-team-notice`(`.form-hint`)·`#checkout-price-tiers-table`(`.price-tiers-table`)는 둘 다 `display`를 자체 선언하지 않는 클래스라(기존 `product.html`의 동일 패턴과 동일) 보정 규칙이 필요 없음을 확인 — 결과적으로 `css/components.css`는 전혀 수정하지 않음(태스크의 "필요하면" 조건에 해당 없음으로 판단).
  - `src/main/resources/static/js/checkout.js` 신규 작성:
    - `parseParams()`: `productId`(필수, `/^[1-9]\d*$/`), `teamId`(파라미터 없으면 `null`=혼자구매, 있는데 형식이 잘못되면 `valid:false`)를 파싱. 둘 중 하나라도 형식 오류면 API 호출 없이 즉시 "잘못된 접근입니다..." 상태만 보여주고 종료(버튼 이벤트 바인딩도 하지 않음).
    - `loadProduct(productId, teamId)`: `Api.get('/products/' + productId)` → 성공 시 `renderProduct(product, teamId)` 후 `#checkout-detail` 노출. `PRODUCT_NOT_FOUND`(404)면 "상품을 찾을 수 없습니다." 전용 안내로 분기(결제 영역은 계속 `hidden` 상태 유지), 그 외는 서버 `message` 노출.
    - `renderProduct(product, teamId)`: `teamId === null`(혼자구매)이면 라벨을 "결제 금액"으로, `amount`에 `basePrice`를 표시하고 팀 안내/구간표는 숨김. `teamId`가 있으면 라벨을 "정가 (참고)"로 바꾸고, 팀 안내 문구를 노출하며 `priceTiers`를 참고용 표로 렌더링(계획대로 클라이언트에서 실제 금액을 계산하지 않음 — 서버 응답 `amount`만을 확정 소스로 취급).
    - `teamId`의 존재/정원 여부는 사전 조회하지 않음(계획대로 `POST /api/payments` 응답으로만 사후 판정) — 이 페이지에서 `GET /api/teams/{teamId}` 같은 호출을 만들지 않음.
    - `handlePay()`: `{ productId }`(+ `teamId`가 있을 때만 필드 추가)로 `Api.post('/payments', body)` 호출. 성공(201) → `showSummary(payment)`가 `#checkout-detail`을 숨기고 `productName`/`amount`/`status`/`paidAt`을 `#checkout-summary`에 채워 노출(버튼 영역째로 숨겨지므로 별도 `disabled` 처리 불필요). 실패 → `handlePaymentError(err)`가 401/`UNAUTHORIZED`만 "로그인이 필요합니다" + 로그인 링크로 특별 분기하고, 나머지(400/403/404/409)는 서버 `message`를 그대로 노출(계획 문서가 이 코드들 전부 "서버 message 표시"로 지정했으므로 별도 코드 분기 불필요 — 401만 로그인 링크가 다르기 때문에 분기).
    - `handleCancel()`: API 호출 없이 `window.location.href = 'product.html?id=' + currentProductId`로 이동.
    - 상품명/판매자명/서버 `message`/`status`/`paidAt` 전부 `textContent`로만 대입(`innerHTML` 미사용, `checkout-amount-label`에 들어가는 두 문자열("결제 금액"/"정가 (참고)")은 서버 데이터가 아닌 코드 내 고정 리터럴).
  - `src/main/resources/static/js/product.js` 수정:
    - `handleBuyAlone()`: 기존 "결제 기능은 준비 중입니다" placeholder 배너 호출을 제거하고 `window.location.href = 'checkout.html?productId=' + currentProductId`로 교체.
    - `showPageAlert(text, variant, showLoginLink, payLinkHref)`에 4번째 파라미터 추가, `#page-alert-pay-link`(신규 앵커, `product.html`에 슬롯 추가) 노출/href 제어. `hidePageAlert()`도 이 링크를 함께 초기화하도록 수정.
    - `handleJoin()` 성공 시: `showPageAlert('공구팀에 참가했습니다.', 'success', false, 'checkout.html?productId=' + currentProductId + '&teamId=' + teamId)`로 결제 이동 링크를 추가 노출(자동 리다이렉트 없음, 팀 목록 재조회는 기존과 동일하게 유지).
    - `handleCreateTeam()` 성공 시: 응답 body(`team.teamId`)를 사용해 동일하게 결제 이동 링크(`checkout.html?productId=...&teamId={신설된 teamId}`)를 노출.
    - 그 외 로딩/에러 처리, 팀 목록 렌더링, `handleActionError()` 분기 로직은 손대지 않음.
  - `src/main/resources/static/product.html` 수정: `#page-alert` 내부에 `#page-alert-pay-link`(`<a href="#" hidden>결제하기</a>`) 슬롯 1개만 추가(`#page-alert-login-link`와 형제 요소, 동일한 조건부 표시 앵커 패턴).
  - `js/api.js`, `js/include.js`, `css/tokens.css`/`base.css`/`layout.css`, `partials/header.html`/`footer.html`은 전혀 수정하지 않음(계획 전제 유지). `css/components.css`도 위 `[hidden]` 검토 결과 수정하지 않음.
  - `./gradlew compileJava` 실행 → `BUILD SUCCESSFUL`(`UP-TO-DATE`, 이번 작업이 정적 리소스/JS만 다뤄 Java 소스 변경 없음).
  - 브라우저 수동 확인(`bootRun` 후 실제 결제 플로우/401·403·404·409 재현/`[hidden]` computed style 확인)은 이번 Generate 단계에서 수행하지 않음 — Evaluate 단계 몫으로 남김.

- 결과: ✅ PASS
  - 계산적 평가: `./gradlew compileJava` → `BUILD SUCCESSFUL`(`UP-TO-DATE`, Java 소스 변경 없어 예상대로).
  - 추론적 평가: 계획(`docs/dev/ongoing/checkout.md`) 대비 실제 파일을 읽어 대조, 스코프 이탈 없음 확인.

- 원인(판단 근거):
  - `git status`로 `css/tokens.css`/`base.css`/`layout.css`/`components.css`, `js/api.js`, `js/include.js`가 모두 수정 목록에 없음을 확인 — "필요한 CSS 없으면 손대지 않는다"는 계획 전제 그대로 지킴.
  - `checkout.html`: `#checkout-detail`/`#checkout-summary`가 실제로 `class="product-detail"`을 쓰고 있음을 마크업에서 직접 확인 — `css/components.css:318` `.product-detail[hidden] { display: none; }` 보정을 그대로 재사용하는 것이 사실과 일치(새 버그 없음). `#checkout-team-notice`(`.form-hint`)·`#checkout-price-tiers-table`(`.price-tiers-table`)는 `components.css`에 `display` 선언이 없어(각각 line 230, 341) 별도 `[hidden]` 보정이 필요 없다는 판단도 CSS 원문으로 확인됨.
  - `checkout.js`: `parseParams()`가 `productId`/`teamId` 형식을 모두 검증한 뒤 `valid:false`면 `init()`이 `showStatus` 후 즉시 `return`해 어떤 API 호출·버튼 바인딩도 하지 않음(코드 확인). `loadProduct`가 `GET /api/products/{productId}` 호출 후 `PRODUCT_NOT_FOUND` 전용 분기, `renderProduct`가 `teamId === null`이면 `basePrice`를 "결제 금액"으로, 있으면 "정가 (참고)"+팀 안내 문구+구간표로 분기하는 것을 코드로 확인. `handlePay`가 `POST /api/payments` 호출, `handlePaymentError`가 401/`UNAUTHORIZED`만 로그인 링크로 분기하고 400/403/404/409는 서버 `message`를 그대로 노출(계획이 이 4개 코드 모두 "서버 message 표시"로 지정했으므로 일치). `handleCancel`이 API 호출 없이 `product.html?id=`로만 이동. 전체적으로 `textContent`만 사용, `innerHTML` 호출 없음(코드 grep으로 확인).
  - `js/product.js` diff: `handleBuyAlone`이 기존 placeholder 배너 호출을 제거하고 `checkout.html?productId=`로 이동하도록만 변경됨. `handleJoin`/`handleCreateTeam`은 기존 성공 배너(`success` variant) + `loadTeams` 재조회 로직을 그대로 유지하면서 `showPageAlert`에 4번째 인자(`payLinkHref`)만 추가— 자동 리다이렉트 없음, `handleActionError`/`loadTeams`/`renderProduct`/`parseProductId` 등 다른 로직은 diff에 전혀 나타나지 않음(불필요한 변경 없음).
  - `product.html` diff: `#page-alert` 내부에 `#page-alert-pay-link` 앵커 1줄만 추가됨. 다른 변경 없음.
  - 필드명 대조: `docs/api/product.md`(`sellerName`/`basePrice`/`priceTiers[].minCount`/`priceTiers[].price`), `docs/api/team.md`(`teamId`), `docs/api/payment.md`(`productName`/`amount`/`status`/`paidAt`) 모두 코드에서 사용한 프로퍼티명과 일치.
  - `docs/api/payment.md`에 추가된 `FORBIDDEN`(403) 코드는 `PaymentService.requireBuyer` 등 실제 백엔드(`ErrorCode.FORBIDDEN`, `src/main/java/com/gong9ri/gong9ri/service/PaymentService.java`)와 일치함을 grep으로 확인(재검증 대상 아니지만 참고 확인).
  - `docs/code-convention.md`는 Java/Spring 계층 규칙 위주이고 이번 변경은 정적 프론트(HTML/JS)만 다뤄 해당 조항 위반 소지 자체가 없음(Java 소스 변경 없음).

- 증거:
  - `./gradlew compileJava --console=plain` → `BUILD SUCCESSFUL in 1s`, `1 actionable task: 1 up-to-date`.
  - `git status` (evaluate 시점): `modified: docs/api/payment.md, src/main/resources/static/js/product.js, src/main/resources/static/product.html` / `untracked: docs/dev/ongoing/checkout.md, docs/logs/frontend/checkout/, src/main/resources/static/checkout.html, src/main/resources/static/js/checkout.js` — `css/components.css` 등 다른 CSS·`js/api.js`·`js/include.js`는 목록에 없어 미수정 확인.
  - 브라우저 수동 확인(실제 결제 플로우, 401/403/404/409 재현, computed style)은 이번 Evaluate 범위에서도 수행하지 않음 — 호출자(오케스트레이터/사용자)가 `bootRun` 후 직접 확인해야 함.

## Attempt 2 — 2026-08-07 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 로컬 MySQL + 임시 `redis:7` 컨테이너로 `bootRun` 기동. 테스트 판매자·상품(가격구간 2/3인) 1개를 SQL로 삽입(`utf8mb4`), 구매자 2명·판매자 1명 계정은 실제 `signup.html`/`login.html`로 생성·로그인.
  - 잘못된 `productId`, 존재하지 않는 상품, 비로그인 혼자구매 결제(401), 로그인 후 혼자구매 결제 성공, 판매자 결제 시도(403), "삭제(취소)" 버튼, 팀 신설→결제 링크→팀 결제(인원 1명, 기본가), 두 번째 구매자 참가→결제 링크→팀 결제(인원 2명, 2인 이상 가격구간 반영) 순으로 확인.
  - 확인 후 테스트 계정 4개, 상품 895, 가격구간, 팀/참가/결제 기록, `seller_revenue_summary`, 임시 Redis 컨테이너를 전부 정리(삭제).
- 결과: ✅ **PASS** (버그 없음). 확인 과정에서 두 번 "버그처럼 보이는" 현상을 만났으나 둘 다 `element.textContent`가 `display:none`인 후손 요소의 텍스트까지 포함해서 읽어오는 특성 때문에 생긴 **오탐**이었고, `hidden`이 붙은 요소를 실제로 눈에 보이는 텍스트만 추출하는 방식(부모부터 `hidden`/`display:none` 체크하며 재귀적으로 텍스트 수집)으로 재확인해 실제로는 정상 숨김임을 확인했다. `computed style`(`display`) 기준으로 판정했고 잘못된 리포트를 남기지 않았다.
- 원인: (실패 없음 — 해당 없음)
- 증거:
  - **잘못된 파라미터**: `productId=abc` → `/api/products/abc` 호출 없음, "잘못된 접근입니다." 표시.
  - **존재하지 않는 상품**: `productId=99999999` → "상품을 찾을 수 없습니다."만 노출, `#checkout-detail`(`.product-detail` 재사용, `hidden`)이 `display:none`으로 정상 숨김(빈 템플릿 잔재 없음 — product-detail 단계의 버그가 재발하지 않음을 확인).
  - **혼자구매 401**: 비로그인 상태로 결제하기 클릭 → "로그인이 필요합니다." + `#page-alert-login-link`가 `hidden=false`/`display:inline`으로 실제로 보임.
  - **혼자구매 성공**: `cobuyer1` 로그인 후 결제 → 응답 그대로 "결제 금액 20,000원 / 상태 PAID" 요약 표시, `#checkout-detail` 숨김(`display:none`), 결제 버튼 `disabled=true`. `#checkout-team-notice`/가격구간표는 `hidden=true`+`display:none`으로 정상 숨김(처음엔 `textContent`로 오탐했으나 `getComputedStyle` 재확인으로 정상 확인).
  - **판매자 403**: `coseller1` 로그인 상태로 결제하기 클릭 → "접근 권한이 없습니다." 표시.
  - **삭제(취소)**: 클릭 시 API 호출 없이 `product.html?id=895`로 이동 확인(`location.href` 변경만 확인, Network 탭에 결제 관련 요청 없음).
  - **팀 결제(인원 1명)**: `cobuyer1`이 신규 팀 신설 → 배너의 "결제하기" 링크(`checkout.html?productId=895&teamId=197`) 확인 → 결제 성공 시 금액 **20,000원**(기본가, `currentCount=1`이라 2인 이상 구간 미적용 — `PaymentService.resolveTeamPrice` 로직과 일치).
  - **팀 결제(인원 2명)**: `cobuyer2`가 같은 팀에 참가 → 배너에 결제 링크 노출 → 결제 성공 시 금액 **17,000원**(2명 이상 구간가 정확히 반영 — 서버가 결제 시점 인원 기준으로 확정한다는 계획 설계와 일치).
  - **모바일(375×812)**: `scrollWidth === clientWidth`(가로 스크롤 없음).
  - **콘솔**: 이번 결제 플로우에서 새로 발생한 처리되지 않은 에러 없음.
  - 평가 종료 후 모든 테스트 데이터(계정 4개, 상품/가격구간/팀/참가/결제/수익요약)와 임시 Redis 컨테이너 정리 완료.
