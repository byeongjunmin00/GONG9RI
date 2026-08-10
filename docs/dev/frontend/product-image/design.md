# 상품 이미지 표시/입력 (frontend/product-image) — Design

## 개요

메인 페이지 카드와 상품 상세 페이지가 `imageUrl`이 있으면 실제 이미지를, 없으면 기존 CSS 그라디언트 placeholder를 그대로 보여준다(하위 호환). 판매자 등록/수정 폼에는 "상품 이미지 URL" 선택 입력 필드가 추가돼, 등록/수정 요청에 `imageUrl`을 실어 보낸다.

## 인터페이스 / 산출물

- `js/main.js`: `createProductCard()`의 `.card-image` 생성 지점에서 `product.imageUrl`이 있으면 `<img>`(속성 대입: `src`/`alt`)를 조건부로 append.
- `product.html` + `js/product.js`: `#product-detail` 안, `.section__head` 앞에 `<div id="product-image" class="card-image product-detail-image">` 신규 영역. `renderProduct()`에서 `clearChildren` 후 이미지 유무에 따라 `<img>` 렌더링.
- `seller/products/new.html` + `js/seller-product-new.js`, `seller/products/edit.html` + `js/seller-product-edit.js`: "상품 이미지 URL" 텍스트 입력(선택) 추가. 등록/수정 제출 시 `imageUrl: imageUrl || null` 포함, 수정 폼은 기존 값 프리필.
- `css/components.css`: 기존 `.card-image`/`.card-image img`(object-fit:cover 등) 재사용. `.product-detail-image`(max-width:400px, border-radius)만 상세페이지 전용으로 최소 추가.

## 데이터 연동

- `GET /api/products`, `GET /api/products/{id}` 응답의 `imageUrl` 필드를 그대로 렌더링(상세: `docs/api/product.md`).
- `POST`/`PUT /api/products` 요청에 `imageUrl` 포함 — 입력값이 빈 문자열이면 `null`로 정규화해서 전송.

## 규칙 / 검증

- **XSS 방지**: `imageUrl`을 DOM에 넣을 때 4곳 모두 `img.src =`/`img.alt =` 속성 대입만 사용(`innerHTML` 문자열 조합 없음).
- **하위 호환**: `imageUrl`이 없는 기존 상품은 그라디언트 placeholder를 그대로 유지, 페이지가 깨지지 않는다.
- **범위 밖**: `seller/mypage.html`의 상품 목록은 이미지를 표시하지 않는다(텍스트 위주 유지). 실제 파일 업로드는 지원하지 않는다(URL 문자열 저장/표시만).
- 이미지 로드 실패(깨진 URL) 시 별도 폴백 로직 없음 — 브라우저 기본 깨진 이미지 아이콘으로 처리.

## 관련 코드 위치

- `js/main.js`, `product.html`, `js/product.js`, `seller/products/new.html`, `js/seller-product-new.js`, `seller/products/edit.html`, `js/seller-product-edit.js`
- `css/components.css` — `.product-detail-image` 규칙 추가
- 경위: `docs/dev/frontend/product-image/changes/001-product-image.md`, 실행 로그: `docs/logs/frontend/product-image/001-product-image.md`
