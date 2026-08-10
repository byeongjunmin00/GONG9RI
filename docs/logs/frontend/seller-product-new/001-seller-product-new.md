# 001-seller-product-new — 판매 물품 등록 페이지 (로그)

## Attempt 1 — 2026-08-10

- 시도: `docs/dev/ongoing/seller-product-new.md`에 승인된 계획대로 아래를 구현했다.
  - `src/main/resources/static/seller/products/new.html` 신규 작성: 헤더/푸터 include(절대경로), CSS/JS 참조 전부 절대경로(`/css/...`, `/js/...`, `/partials/...`), 폼 필드(상품명 필수/설명 선택 textarea/정가 필수/최대인원 필수), 가격구간 입력 영역(`#price-tier-rows` 컨테이너 + "행 추가" 버튼 + `#price-tiers-error`), 공통 에러 배너(`#form-alert` + `#form-alert-text` + `#form-alert-login-link`, checkout.html의 `page-alert` 패턴을 재사용), "등록하기"(submit)/"취소하기"(정적 `<a href="/">`, product-detail의 "계속 쇼핑하기"와 동일 패턴 — API 호출 불필요이므로 JS 핸들러 대신 순수 링크로 구현).
  - `src/main/resources/static/js/seller-product-new.js` 신규 작성:
    - 가격구간 행: `createPriceTierRow()`로 `document.createElement` 기반 행(최소 인원/가격 입력 + 삭제 버튼) 생성, `addPriceTierRow()`/`removePriceTierRow()`로 추가/삭제, `updateRemoveButtonsVisibility()`로 행이 1개뿐이면 그 행 삭제 버튼을 `hidden`(삭제 버튼은 기존 `.btn` 클래스라 `components.css`의 `.btn[hidden] { display: none; }` 규칙을 그대로 재사용 — 신규 `[hidden]` 보정 규칙 불필요). 초기화 시 `addPriceTierRow()` 1회 호출로 최소 1행 보장.
    - 클라이언트 가드레일 검증(`validatePriceTiersGuardrail`): 중복 `minCount` 금지, 오름차순 강제, `minCount >= 2`, `minCount <= maxParticipants` — 위반 시 `#price-tiers-error`에 안내, 서버 제출은 막되 SSOT는 서버 응답이라는 주석을 명시.
    - 제출: `POST /api/products` 호출, 성공(201) 시 `window.location.href = '/product.html?id=' + product.productId`로 이동(절대경로 — 서브디렉토리 페이지라 상대경로 `product.html`을 쓰면 `/seller/products/product.html`로 잘못 풀리는 점을 주석으로 명시하고 회피). 실패 처리: `status===401 || code==='UNAUTHORIZED'` → "로그인이 필요합니다." + 로그인 링크 노출, 그 외(400 VALIDATION_FAILED/403 FORBIDDEN 등)는 서버 `message`를 공통 배너에 `textContent`로만 표시(innerHTML 미사용).
  - `src/main/resources/static/css/components.css`에 `.price-tier-rows`/`.price-tier-row`/`.price-tier-row__field`/`.price-tier-row__remove` 규칙 추가. `hidden`을 쓰는 신규 요소(가격구간 삭제 버튼)는 기존 `.btn` 클래스를 그대로 쓰므로 기존 `.btn[hidden]` 규칙으로 커버되고, 별도 커스텀 `display`를 선언하지 않아 새 버그를 만들지 않도록 했다. `#form-alert`/`#price-tiers-error`도 기존 `.form-alert`/`.form-error`처럼 자체 `display`를 선언하지 않는 패턴을 그대로 따랐다.
  - `src/main/java/com/gong9ri/gong9ri/config/SecurityConfig.java`의 정적 리소스 permitAll 매처에 `"/**/*.html"`을 기존 `"/*.html"`에 나란히 추가(계획의 "확인 필요 2번" 결정대로 일반화). 기존 `"/*.html"`, `/api/**` 관련 규칙(`HttpMethod.POST /api/auth/**`, `HttpMethod.GET /api/products/**`, `anyRequest().authenticated()`)은 손대지 않았다.
  - `src/main/resources/static/partials/header.html` 상단 주석 갱신: "판매 물품 등록"이 이제 자리표시자가 아니라 실제 페이지이며, 서브디렉토리 경로라 SecurityConfig의 `"/**/*.html"` 매처가 필요하다는 내용으로 교체.
- 컴파일 확인: `./gradlew compileJava` → `BUILD SUCCESSFUL`.
- 테스트 확인: `./gradlew test` 실행 결과 89개 중 87개 FAIL. 실패 원인을 스택트레이스로 직접 확인한 결과 전부 `com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure` → `Connection refused: getsockopt`로, `ApplicationContext` 로딩 단계에서 로컬 MySQL에 연결하지 못해 발생한 것이다(`AuthControllerTest`처럼 이번 변경과 무관한 테스트도 전부 동일 원인으로 실패). 이 머신에는 MySQL 서비스 자체가 없음을 `Get-Service '*mysql*'`로 확인했다(빈 결과). 즉 이번 SecurityConfig/정적 리소스 변경으로 인한 회귀인지 여부는 **이 환경에서는 검증하지 못했다** — 로컬 MySQL이 있는 환경에서 `./gradlew test` 재실행이 필요하다.

## Attempt 2 — 2026-08-10 (계산적 재검증 + 평가 기준의 브라우저 수동 확인)

- 시도:
  - Generate 단계에서 로컬 네이티브 MySQL이 아예 꺼져 있어(`Get-Service`/`netstat` 모두 빈 결과 — 세션 도중 어느 시점에 종료된 것으로 보임) `./gradlew test`를 검증하지 못했던 문제를, 저장소에 이미 있는 `docker-compose.yml`의 `mysql`/`redis` 서비스만 띄워서(`docker compose up -d mysql redis`, Docker Desktop 재기동 포함) 해결했다. 이후 이 방식(도커 컴포즈의 MySQL/Redis + 로컬 `gradlew bootRun`)을 계속 재사용.
  - `./gradlew test` 재실행 → `BUILD SUCCESSFUL`(전체 통과) — SecurityConfig 변경(`"/**/*.html"` 추가)이 기존 인증/보안 테스트를 깨지 않음을 실제로 확인.
  - `bootRun` 기동 후 `/seller/products/new.html`을 비로그인/구매자/판매자 세 가지 인증 상태로 접속·제출, 가격구간 행 추가/삭제, 필수값 누락, 등록 성공 후 이동까지 확인. 확인 후 테스트 계정(`spnbuyer1`/`spnseller1`)과 등록된 테스트 상품(가격구간 포함) 정리.
- 결과: ✅ **PASS** (버그 없음).
- 원인: (실패 없음 — 해당 없음)
- 증거:
  - **계산적 재검증**: `./gradlew test` → `BUILD SUCCESSFUL in 24s`, `5 actionable tasks: 1 executed, 4 up-to-date` — 89개 테스트 전부 통과(도커 MySQL/Redis 기준). SecurityConfig 변경으로 인한 회귀 없음이 최종 확인됨.
  - **비로그인 접근**: `GET /seller/products/new.html` → `200`(SecurityConfig의 `"/**/*.html"` 매처가 서브디렉토리 페이지를 정상 permitAll 처리). 같은 페이지가 로드하는 `css/*.css`/`js/include.js`/`js/api.js`/`js/seller-product-new.js`/`partials/*.html` 전부 절대경로로 `200` 확인 — 서브디렉토리에서도 참조가 깨지지 않음(계획의 사전 확인 사실과 일치).
  - **가격구간 행**: "행 추가" 클릭 2회 → 3행, 전부 삭제 버튼 노출(`hidden=false`). 2행 제거 → 1행만 남고 그 행의 삭제 버튼이 `hidden=true`/`getComputedStyle().display === "none"`으로 정상 숨김(기존 `.btn[hidden]` 규칙 재사용이 실제로 작동함을 확인, 새 CSS 버그 없음).
  - **401(비로그인 제출)**: "로그인이 필요합니다." + 로그인 링크가 `hidden=false`로 실제 노출.
  - **403(구매자 계정)**: `spnbuyer1`(BUYER)로 로그인 후 제출 → "접근 권한이 없습니다." 노출.
  - **등록 성공(판매자 계정)**: `spnseller1`(SELLER)로 로그인 후 상품명/설명/정가 25,000/최대인원 5/가격구간(2명 이상→20,000원) 입력 후 제출 → `product.html?id=117`로 리다이렉트, 상세 페이지에 판매자명/상품명/설명/정가/최대인원/가격구간표가 입력값과 정확히 일치해 렌더링, 팀 목록은 "아직 모집 중인 공구팀이 없습니다"(빈 상태, 에러 아님)로 정상 처리 — 계획 리스크 섹션에서 우려했던 "신규 등록 상품의 빈 팀 목록 처리"도 문제없음을 확인.
  - **400(필수값 누락)**: 상품명을 비운 채 제출 → 클라이언트 가드레일이 서버 호출 전에 "상품명, 정가, 팀 최대 인원을 모두 입력해주세요."로 차단(다른 폼들과 동일한 "클라이언트에서 막든 서버 응답으로 막든" 기준 충족).
  - **모바일(375×812)**: `scrollWidth === clientWidth`(가로 스크롤 없음).
  - **콘솔**: 이번 등록 성공 플로우에서 새로 발생한 처리되지 않은 에러 없음(남아있던 401/403 에러 로그는 앞선 의도된 실패 케이스 테스트에서 나온 것).
  - 평가 종료 후 테스트 계정 2개와 등록 상품(가격구간 포함) 정리 완료.
