# 001-product-image — 상품 이미지 표시/입력 (프론트, 로그)

## Attempt 1 — 2026-08-10
- 시도: 계획 문서(`docs/dev/ongoing/product-image.md`) 그대로 프론트 4곳을 구현.
  - `js/main.js`의 `createProductCard()`: 기존 `.card-image` div 생성 지점에서 `product.imageUrl`이 있으면 `<img>`를 만들어 `img.src = product.imageUrl`, `img.alt = product.name || ''`(속성 대입만, `innerHTML` 미사용)로 채운 뒤 `.card-image`에 append. 없으면 기존처럼 빈 div로 두어 CSS 그라디언트 placeholder를 유지.
  - `product.html`: `#product-detail` 안, `.section__head`(이름/설명) 앞에 `<div id="product-image" class="card-image product-detail-image">`를 신규로 추가. 기존 `.card-image`/`.card-image img` 규칙(aspect-ratio 1:1, object-fit:cover)을 그대로 재사용하고, 상세페이지 폭 제한용으로 `.product-detail-image { max-width: 400px; border-radius: var(--radius-lg); }`만 `css/components.css`에 최소 추가. `hidden` 속성은 이 요소에 쓰지 않음(내용 유무만 토글) — 이번 세션에서 겪은 `[hidden]` specificity 문제를 피하기 위해 의도적으로 회피.
  - `js/product.js`: `renderProduct()` 시작 지점에서 `#product-image`를 `clearChildren()`으로 비운 뒤, `product.imageUrl`이 있으면 `<img>`(`src`/`alt` 속성 대입)를 추가. 없으면 빈 상태로 두어 그라디언트 placeholder 노출(메인페이지와 동일 패턴).
  - `seller/products/new.html` + `js/seller-product-new.js`: "상품 이미지 URL" 텍스트 입력(선택, placeholder에 Pexels 예시 URL) 추가. 제출 시 `POST /api/products` body에 `imageUrl: imageUrl || null`로 포함(빈 문자열이면 `null`로 정규화해 서버로 전달).
  - `seller/products/edit.html` + `js/seller-product-edit.js`: 동일한 입력 필드 추가. `fillForm()`에서 `imageUrlInput.value = product.imageUrl || ''`로 프리필. `PUT` 제출 시 동일하게 `imageUrl: imageUrl || null` 포함.
- XSS 대응 확인: 4곳 모두 `img.src =`/`img.alt =` 속성 대입만 사용, `innerHTML` 문자열 조합 없음.
- 범위 확인: `seller/mypage.html` 등 마이페이지 목록에는 이미지 추가 안 함(계획 범위 밖 유지). 실제 파일 업로드는 구현하지 않음(URL 문자열 저장/표시만).
- 결과: 프론트는 정적 리소스라 별도 컴파일 단계 없음 — 문법 오류 여부는 `./gradlew compileJava`/`test`(백엔드 회귀만 대상)로는 검증되지 않는다. 브라우저 실측(등록 폼 입력→상세/메인 카드 렌더링, 이미지 없는 기존 상품 하위호환, 수정 폼 프리필)은 Evaluate 단계에서 진행 예정.

### Evaluate — 2026-08-10  ✅ PASS (코드 리뷰 기준)
- 범위 고지: 브라우저 수동 확인(등록→상세/메인 카드 렌더링, 하위호환, 수정 폼 프리필)은 Evaluate 역할 범위 밖이라 진행하지 않았다 — 호출자가 직접 수행 필요. 아래는 전부 `git diff` 코드 리뷰로 확인한 결과.
- 추론적 평가(코드 대조):
  - `js/main.js`: `product.imageUrl`이 있을 때만 `document.createElement('img')` 생성 후 `imgEl.src`/`imgEl.alt` **속성 대입**(`innerHTML` 미사용)으로 `.card-image`에 `appendChild` — 계획과 일치. 없으면 기존처럼 빈 `div`만 남아 CSS 그라디언트 placeholder 유지(하위호환).
  - `product.html`/`js/product.js`: `#product-detail` 안, `.section__head`(이름/설명) **앞**에 `<div id="product-image" class="card-image product-detail-image">` 신규 추가. 이 요소 자체에 `hidden` 속성 미사용 확인(주변 `#product-status`/`#product-detail`/`#price-tiers-table`/`#team-status`는 상태 토글용 `hidden`을 그대로 쓰지만 이미지 영역은 내용 유무만으로 토글). `renderProduct()`에서 `clearChildren(imageEl)` 후 이미지 유무에 따라 `<img>`(`src`/`alt` 속성 대입) 추가 — 메인페이지와 동일 패턴, `innerHTML` 미사용.
  - `css/components.css`: 기존 `.card-image`(89~95행)/`.card-image img`(97~101행) 규칙이 이미 `object-fit:cover` 등을 갖추고 있어 그대로 재사용됨을 확인. `.product-detail-image`(max-width:400px, border-radius만) 최소 추가 — 계획과 일치, 중복 규칙 없음.
  - `seller/products/new.html`+`js/seller-product-new.js`, `seller/products/edit.html`+`js/seller-product-edit.js`: "상품 이미지 URL" 텍스트 입력(선택, placeholder에 Pexels 예시 URL) 추가. 등록/수정 제출 시 `imageUrl: imageUrl || null`로 body 포함. 수정 폼 `fillForm()`에서 `imageUrlInput.value = product.imageUrl || ''`로 프리필 — 계획과 일치.
  - 스코프 확인: `seller/mypage.html`, `SecurityConfig.java`, `js/api.js`, `js/include.js`, `css/tokens.css`, `css/base.css`, `css/layout.css` 전부 `git diff` 결과 없음(미수정) — 계획 범위(마이페이지 제외, 백엔드 인가/유틸 미변경) 그대로 지켜짐.
  - `innerHTML` 전체 grep(`static/` 하위): 매치는 `js/include.js` 1건뿐이며 이 파일은 이번 작업에서 `git diff` 결과가 없는 기존 코드(공통 include 유틸, 이번 기능과 무관) — 이번 변경분에 신규 `innerHTML` 사용 없음 확인.
- 원인: 해당 없음(PASS).
- 판정: PASS(코드 리뷰 기준). 브라우저 렌더링 실측은 별도로 호출자가 확인 필요.

## Attempt 2 — 2026-08-10 (평가 기준의 브라우저 수동 확인)

- 시도:
  - 도커 MySQL/Redis + `bootRun`으로 판매자 계정(`imgseller1`) 생성 → 이미지 URL 포함 상품(감귤, Pexels)과 이미지 없는 상품 2개를 실제 등록 폼으로 등록 → 상세/메인페이지에서 렌더링 확인 → 수정 폼에서 이미지 URL을 다른 값(크루아상)으로 변경·저장 → 반영 확인. 확인 후 테스트 계정·상품 정리.
- 결과: ✅ **PASS** (버그 없음)
- 원인: (해당 없음)
- 증거:
  - **이미지 있는 상품**: 등록 후 `product.html?id=934`에서 `#product-image img`가 `src="https://images.pexels.com/photos/2294477/..."`, `alt="제주 감귤 이미지테스트"`로 정확히 삽입되고 `naturalWidth: 800`(실제 이미지 데이터 로드 확인, 깨진 이미지 아님). 같은 상품이 메인페이지 카드(`#product-grid .card-image img`)에서도 동일 URL로 렌더링됨을 확인.
  - **이미지 없는 상품**: 등록 후 상세 페이지의 `#product-image`가 `<div id="product-image" class="card-image product-detail-image"></div>`로 `<img>` 없이 비어 있음(하위호환 — 그라디언트 placeholder만 표시, 깨짐 없음).
  - **수정 폼 프리필**: `seller/products/edit.html?id=934` 진입 시 `#imageUrl` 입력값이 기존 등록값과 정확히 일치.
  - **수정 반영**: 이미지 URL을 크루아상 사진으로 변경 후 저장 → 상세 페이지 재방문 시 `#product-image img`가 새 URL로 교체되고 `naturalWidth: 800`으로 정상 로드 확인.
  - **모바일(375×812)**: `scrollWidth === clientWidth`(가로 스크롤 없음).
  - 평가 종료 후 테스트 계정(`imgseller1`)과 상품 2개(가격구간 포함) 정리 완료.
