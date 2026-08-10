# 001-seller-mypage — 판매자 마이페이지 (로그)

## Attempt 1 — 2026-08-10

- 시도: `docs/dev/ongoing/seller-mypage.md`(승인된 계획)를 그대로 구현.
  - 신규: `src/main/resources/static/seller/mypage.html` — 헤더/푸터 include, `#page-alert`(401 공통 배너,
    product.html/checkout.html의 page-alert 패턴 재사용) + 3개 섹션(등록 상품 목록/수익 현황/공구 참여 현황),
    각 섹션은 `.product-status`(로딩/빈 목록/에러) 상태 영역을 독립적으로 둠.
  - 신규: `src/main/resources/static/js/seller-mypage.js` — `GET /api/seller/mypage/{products,revenue,teams}`를
    병렬로 호출·렌더링. 401은 공통 배너로 세 섹션 전체(`#mypage-sections`)를 숨기고, 403/기타는 각 섹션
    상태 영역에 서버 message를 표시(다른 섹션은 독립적으로 계속 렌더링). 상품 항목에 "수정"(`<a>`,
    `/seller/products/edit.html?id={productId}`) / "삭제"(`window.confirm` → `DELETE /api/products/{id}` →
    성공 시 해당 `<li>`만 DOM에서 제거, 실패 시 목록 유지 + 상태 영역에 안내) 버튼을 둠. 공구 참여 현황의
    뱃지/라벨 매핑(`statusToBadgeClass`/`statusToLabel`)은 `js/product.js`의 동일 로직을 그대로 복제
    (모듈 공유 구조가 없어 IIFE 내부 함수를 못 가져오므로 로직만 재사용).
  - 신규: `src/main/resources/static/seller/products/edit.html` + `js/seller-product-edit.js` — 쿼리 `id`
    파싱은 `product.js`의 `parseProductId`(양의 정수 정규식)와 동일한 방향으로 구현. `GET /api/products/{id}`로
    기존 값을 불러와 폼을 채우고(가격구간 행은 `seller-product-new.js`의 행 추가/삭제·클라이언트 가드레일
    로직을 이 파일 안에 독립적으로 복제 + `createPriceTierRow`에 초기값 파라미터를 추가해 기존 tier로 미리
    채움), 제출 시 `PUT /api/products/{id}` 호출. 성공(200) → `/seller/mypage.html`(절대경로)로 이동.
    404(PRODUCT_NOT_FOUND)면 폼을 계속 숨기고 상태 영역에 안내. 본인 소유 여부는 클라이언트에서 사전
    판정하지 않음(현재 로그인 사용자를 조회할 API가 없어 비교 대상이 없음) — 최종 판정은 `PUT` 403 응답.
    `seller/products/new.html`/`js/seller-product-new.js`(완료된 별개 기능)는 전혀 건드리지 않음.
  - `css/components.css`: `.mypage-section`/`.mypage-list`/`.mypage-list-item`(+`__info`/`__title`/`__meta`/
    `__actions`)/`.revenue-cards`/`.revenue-card`(+`__label`/`__value`) 추가. `.revenue-cards`는 `hidden`
    속성으로 토글되는데 `display: grid`를 갖는 author 규칙이라 네이티브 `[hidden]`이 밀릴 수 있어(기존에
    여러 번 겪은 버그 패턴) `.revenue-cards[hidden] { display: none; }` 보정 규칙을 `.product-detail[hidden]`과
    같은 패턴으로 함께 추가. 나머지 신규 `hidden` 토글 요소(`#mypage-sections`, `#page-alert`, `#form-alert`,
    `#edit-status`, `#products-status` 등)는 별도 `display` 규칙이 없는 기본 block 요소라 네이티브 `[hidden]`이
    그대로 동작함(보정 불필요, `seller-product-new.js` 산출물에서 확인된 것과 동일한 원리).
  - `partials/header.html`: nav 영역(`.site-header__nav`, "판매 물품 등록" 링크 바로 옆)에 "판매자
    마이페이지"(`/seller/mypage.html`) 링크를 추가. 로그인/역할 여부와 무관하게 항상 노출(기존 "판매 물품
    등록"과 동일 원칙 — 헤더 로그인 상태 미연동). 상단 주석도 갱신.
  - `SecurityConfig.java`는 계획대로 건드리지 않음(이미 `/**/*.html` permitAll 매처가 일반화돼 있음, 코드
    재확인 없이 계획 문서 서술을 신뢰).
  - `js/api.js`/`js/include.js`/`css/tokens.css`/`css/base.css`/`css/layout.css`는 수정하지 않음.
  - 서버 응답 문자열(에러 message, 상품명 등)은 전부 `textContent`로만 대입(innerHTML 미사용).
- 컴파일 확인: `./gradlew compileJava` → `BUILD SUCCESSFUL`(Java 변경이 없어 `UP-TO-DATE`).
- `./gradlew test`는 스킵함 — 이번 작업은 정적 리소스(HTML/JS/CSS)만 변경했고 로컬에 MySQL/Redis가
  기동돼 있는지 확인하지 않았음. Evaluate 단계에서 재확인 필요.
- 신규 JS 2개 파일은 `node --check`로 문법 검증하려 했으나 이 환경에 Node.js가 설치돼 있지 않아
  (`node: command not found`) 실행하지 못함 — 대신 파일 전체를 다시 읽어 괄호/중괄호 짝과 함수 정의를
  수동으로 검토함.

- 결과: PASS
- 계산적 평가:
  - `docker compose ps` 결과 `gong9ri-main-mysql-1`/`gong9ri-main-redis-1`가 이미 healthy 상태로 기동 중
    (별도 기동 불필요, Docker Desktop도 이미 실행 중).
  - `./gradlew compileJava` -> `BUILD SUCCESSFUL in 1s`(`UP-TO-DATE`, Java 변경 없음 재확인).
  - `./gradlew test` -> `BUILD SUCCESSFUL in 22s`(`5 actionable tasks: 2 executed, 3 up-to-date`), 실패 0.
    Java 로직 변경이 없는 정적 리소스 작업이라 기존 테스트 스위트 결과에 변화 없음(신규/변경 테스트도 없음).
- 추론적 평가 (계획/컨벤션 대조):
  - `git status`/`git diff`로 변경분 전수 확인: 수정 2개(`css/components.css`, `partials/header.html`),
    신규 파일(`seller/mypage.html`, `seller/products/edit.html`, `js/seller-mypage.js`,
    `js/seller-product-edit.js`, `docs/dev/ongoing/seller-mypage.md`, `docs/logs/frontend/seller-mypage/`).
    `seller/products/new.html`/`js/seller-product-new.js`는 git status/diff 어디에도 등장하지 않음 - 계획대로
    완전 미수정 확인.
  - `git diff --stat -- '*.java'` 결과 없음 - Java 파일 전혀 변경되지 않음. `SecurityConfig.java`의 마지막
    수정 커밋도 이번 작업과 무관한 이전 커밋(`472bf21`, seller-product-new 작업)만 가리켜 재확인.
  - `js/api.js`/`js/include.js`/`css/tokens.css`/`css/base.css`/`css/layout.css` - git status에 등장하지
    않아 미수정 확인.
  - `seller/mypage.html`/`js/seller-mypage.js`: `GET /api/seller/mypage/{products,revenue,teams}` 3개를
    각각 호출하고 독립적으로 렌더링. 401은 `handleUnauthorized`가 공통 배너(`#page-alert`)로
    `#mypage-sections` 전체를 숨기고, 403/기타 에러는 섹션별 상태 영역에만 표시 - "한 섹션 실패가 페이지
    전체를 깨지 않는다"는 계획 요구를 충족. 삭제는 `window.confirm` 확인 후 `Api.del('/products/'+id)` 호출,
    성공 시 해당 `<li>`만 DOM에서 제거, 실패 시 목록 유지 + 상태 영역 안내 - 계획과 일치.
  - `seller/products/edit.html`/`js/seller-product-edit.js`: 쿼리 `id`를 양의 정수 정규식으로 파싱, 실패
    시 API 호출 없이 "잘못된 접근" 상태만 표시. `GET /api/products/{id}`로 기존 값을 불러와 폼을 채우고
    (가격구간 행까지 기존 값으로 프리필), 제출은 `Api.put('/products/'+currentProductId, {...})`. 성공 시
    `window.location.href = '/seller/mypage.html'`(절대경로)로 이동. 401(로그인 링크 노출)/403/404(서버
    message 그대로 표시) 각각 구분 처리 - 계획과 일치. 본인 소유 여부를 클라이언트에서 사전 판정하지 않는
    것은 계획 문서 "확인 필요" 항목에서 이미 Generate 단계 판단으로 위임된 부분이라 스코프 이탈 아님.
  - XSS: `seller-mypage.js`/`seller-product-edit.js` 전체를 읽고 확인한 결과 서버 응답 문자열(상품명,
    에러 message 등)은 전부 `textContent` 대입만 사용, `innerHTML` 사용처 없음.
  - `[hidden]` 보정: `.revenue-cards`는 `display: grid`를 갖는 author 규칙이라
    `.revenue-cards[hidden] { display: none; }` 보정 규칙이 추가돼 있음(`.product-detail[hidden]`과 동일
    패턴) - 필요한 곳에 정확히 적용됨. 이번에 새로 쓰인 다른 `hidden` 토글 요소(`#page-alert`, `#form-alert`,
    `#edit-status`, `#products-status`, `#revenue-status`, `#teams-status`, `#mypage-sections`,
    `#price-tiers-error`)가 참조하는 클래스(`.form-alert`, `.product-status`, 순수 id 셀렉터)는
    components.css에 `display` 선언이 없어 네이티브 `[hidden]`이 그대로 동작 - 보정 누락이 아님(components.css
    244-308번 줄을 직접 읽어 `display` 프로퍼티 부재 확인).
  - `partials/header.html`: "판매자 마이페이지"(`/seller/mypage.html`) 링크가 nav에 추가됐고, 기존
    "메인"/"판매 물품 등록"/로그인/회원가입 링크는 삭제·변경 없이 그대로 유지됨(diff로 확인).
- 원인: 해당 없음(실패 없음).
- 증거:
  - `./gradlew compileJava` -> `BUILD SUCCESSFUL in 1s`.
  - `./gradlew test` -> `BUILD SUCCESSFUL in 22s`(5 actionable tasks: 2 executed, 3 up-to-date).
  - `docker compose ps` -> `gong9ri-main-mysql-1`/`gong9ri-main-redis-1` 모두 `Up ... (healthy)`.
  - API 응답 실측 샘플(실제 로그인 세션 기반 200/401/403/404 응답 캡처)은 이번 Evaluate에서 수행하지
    않음 - 이번 작업은 서버 로직 변경이 없는 정적 프론트엔드 변경이라 계산적 평가는 `compileJava`/`test`로
    충분하다고 판단했고, 계획 문서 "평가(통과) 기준"에 명시된 브라우저 수동 시나리오(로그인 상태별
    렌더링, 실제 수정/삭제 동작 확인)는 호출자가 `bootRun` 후 직접 확인하는 영역으로 남겨둠(이 Evaluate의
    역할 범위 밖 - "브라우저 수동 확인은 Evaluate 역할이 아니다").

## Attempt 2 — 2026-08-10 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 도커 MySQL/Redis + `bootRun`으로 판매자(`smpseller1`)/구매자(`smpbuyer1`) 계정 생성, `seller/products/new.html`로 실제 상품 2개(A/B) 등록, A에 팀 신설+결제까지 진행해 수익/공구현황 데이터를 만든 뒤 판매자 마이페이지를 확인.
  - 헤더 링크 → 목록/수익/참여현황 렌더링 → 상품 수정(프리필 확인 후 가격 변경 저장) → 상품 삭제 → 로그아웃 후 401 → 구매자 계정 403 → 타인 상품 수정 시도 403(edit.html) 순으로 확인. 확인 후 테스트 계정 2개·상품 2개·팀/참가/결제/수익요약 전부 정리.
- 결과: ✅ **PASS** (버그 없음)
- 원인: (해당 없음)
- 증거:
  - **헤더 링크**: `/` 로그인 상태에서 "판매자 마이페이지" 링크 `href="/seller/mypage.html"` 확인.
  - **목록/수익/참여현황**: 등록한 상품 A(30,000원)/B(15,000원) 목록에 정확히 표시, 수익 현황 "총 매출 30,000원 / 결제 완료 1건 / 환불 0건"(실제 결제 1건과 일치), 공구 참여 현황에 A의 팀이 "1/4명, 마감 {deadline}, 모집중" 뱃지로 표시.
  - **수정**: `edit.html?id=234` 진입 시 name/description/basePrice/maxParticipants/가격구간(2명 이상→25,000원)이 기존 값 그대로 프리필됨 확인 → `basePrice`를 33,000으로 바꿔 저장 → `seller/mypage.html`로 리다이렉트, 목록에 33,000원으로 반영됨 확인.
  - **삭제**: 상품 B "삭제" 클릭(확인 다이얼로그 자동 승인) → 목록에서 즉시 사라짐, 상품 A는 그대로 유지.
  - **401**: `POST /api/auth/logout` → `204`(배경 작업으로 구현된 로그아웃 API가 정상 동작함도 함께 확인) → `seller/mypage.html` 재접속 시 "로그인이 필요합니다. 로그인하기"만 표시, 페이지 안 깨짐.
  - **403(마이페이지)**: `smpbuyer1`(BUYER) 로그인 후 접속 → 목록/수익/참여현황 세 섹션 각각 "접근 권한이 없습니다." 독립 표시(한 섹션 실패가 다른 섹션 렌더링을 막지 않음).
  - **403(수정, 타인 상품)**: `smpbuyer1`로 `edit.html?id=234`(본인 상품 아님) 접속 → 조회(`GET`, permitAll)는 되어 폼에 기존 값이 채워지지만, "저장하기" 클릭 시 `PUT` 응답이 403이라 "접근 권한이 없습니다." 표시 확인.
  - **모바일(375×812)**: `scrollWidth === clientWidth`(가로 스크롤 없음).
  - **콘솔**: 이번 플로우에서 새로 발생한 에러 없음.
  - 평가 종료 후 테스트 계정 2개, 상품 2개, 가격구간, 팀/참가/결제, `seller_revenue_summary` 전부 정리 완료.
