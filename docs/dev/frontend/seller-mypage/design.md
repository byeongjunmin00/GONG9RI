# 판매자 마이페이지 (frontend/seller-mypage) — Design

## 개요

판매자가 자신의 등록 상품 목록/수정·삭제/수익 현황/공구 참여 현황을 한 페이지(`/seller/mypage.html`)에서 확인·관리한다. 상품 수정은 별도 페이지(`/seller/products/edit.html?id={productId}`)에서 처리한다. `seller/products/new.html`(등록, 완료된 별개 기능)은 이번 작업에서 건드리지 않는다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── seller/
│   ├── mypage.html               # 마이페이지 본체(상품 목록/수익 현황/공구 참여 현황)
│   └── products/
│       └── edit.html             # 상품 수정 폼
└── js/
    ├── seller-mypage.js          # 3개 API 호출·렌더링, 삭제, 수정 페이지 링크
    └── seller-product-edit.js    # 기존 값 로드, 가격구간 프리필, PUT 제출
```

- `css/components.css`에 `.mypage-section`/`.mypage-list`/`.mypage-list-item`(+`__info`/`__title`/`__meta`/`__actions`)/`.revenue-cards`/`.revenue-card`(+`__label`/`__value`) 추가. `.revenue-cards`는 `display: grid` author 규칙이라 `.revenue-cards[hidden] { display: none; }` 보정 규칙을 `.product-detail[hidden]`과 같은 패턴으로 함께 추가(다른 신규 `hidden` 토글 요소는 참조 클래스에 `display` 선언이 없어 네이티브 `[hidden]`이 그대로 동작, 보정 불필요).
- `partials/header.html`: nav("판매 물품 등록" 옆)에 "판매자 마이페이지"(`/seller/mypage.html`) 링크 추가, 로그인 여부/역할과 무관하게 항상 노출.
- `SecurityConfig.java`는 변경 없음(이미 `/**/*.html` permitAll 매처가 서브디렉토리 html을 허용).

## 데이터 연동

- `seller/mypage.html` 로드 시 `GET /api/seller/mypage/{products,revenue,teams}` 3개를 각각 호출(로그인 사전 확인 없음). 401(UNAUTHORIZED)이면 공통 배너(`#page-alert`)로 로그인 안내+링크를 띄우고 세 섹션(`#mypage-sections`) 전체를 숨긴다. 403(FORBIDDEN, 구매자 계정)/기타 에러는 해당 섹션의 상태 영역에만 표시하고 다른 섹션은 독립적으로 계속 렌더링된다.
- 상품 목록: "수정" 링크는 `/seller/products/edit.html?id={productId}`(API 호출 없음). "삭제"는 `window.confirm` 확인 후 `DELETE /api/products/{productId}` 호출 → 성공(204) 시 해당 항목만 DOM에서 제거, 실패(403/404 등)는 목록 유지 + 상태 영역 안내.
- 수익 현황: `revenue` 응답 3개 필드(`totalRevenue`/`paidCount`/`refundedCount`)를 추가 계산 없이 그대로 표시.
- 공구 참여 현황: `teams` 응답을 `status`(`RECRUITING`/`SUCCESS`/`FAILED`)별로 뱃지/라벨 구분 표시. 뱃지/라벨 매핑은 `js/product.js`의 `statusToBadgeClass`/`statusToLabel`과 동일 로직을 `seller-mypage.js` 내부에 복제(모듈 공유 구조가 없어 IIFE 내부 함수를 가져올 수 없음).
- `seller/products/edit.html`: 쿼리 `id`를 양의 정수 정규식으로 파싱(형식 오류 시 API 호출 없이 "잘못된 접근" 상태 표시). `GET /api/products/{id}`로 기존 값(name/description/basePrice/maxParticipants/priceTiers)을 불러와 폼을 채운다(가격구간 행도 기존 tier로 프리필). 제출 시 `PUT /api/products/{id}`(요청 body는 `POST /api/products`와 동일한 전체 교체 형식). 성공(200) → `/seller/mypage.html`(절대경로)로 이동. 실패: 401(로그인 링크 노출)/403(서버 message, 본인 상품 아님·구매자 계정)/404(PRODUCT_NOT_FOUND)/400(VALIDATION_FAILED). 본인 소유 여부는 클라이언트에서 사전 판정하지 않는다(현재 로그인 사용자 조회 API가 없음) — 최종 판정은 `PUT` 응답.
- 가격구간(`priceTiers`) 행 추가/삭제·클라이언트 가드레일(오름차순/중복 `minCount` 금지/`2 ≤ minCount ≤ maxParticipants` 권장)은 `seller-product-new.js`와 같은 방향이지만 `seller-product-edit.js` 안에 독립적으로 구현(완료된 등록 페이지 파일은 건드리지 않는다). 서버가 이 규칙을 강제하지 않으므로 SSOT는 여전히 서버 응답.
- 서버 응답 문자열(에러 message, 상품명 등)은 전부 `textContent`로만 대입(XSS 방지, `innerHTML` 미사용).

## 규칙 / 검증

- 서브디렉토리 페이지(`seller/mypage.html`, `seller/products/edit.html`)의 CSS/JS/partial/페이지 이동 참조는 반드시 절대경로.
- 삭제는 되돌릴 수 없으므로 `window.confirm` 확인 절차를 거친 뒤에만 `DELETE` 호출.
- 삭제된 상품이 이미 결제/공구팀과 연결돼 있는 경우의 서버 동작(차단/연쇄 삭제 등)은 이번 작업에서 확인하지 않았다 — 서버가 막지 않으면 orphan 데이터가 남을 가능성이 있음(백엔드 정책 확인 필요, 프론트 범위 밖).
- 가격구간 정합성은 서버가 강제하지 않는다(seller-product-new 단계에서 이미 확인된 사실) — 수정 폼도 등록 폼과 동일하게 UX 가드레일만 두고 서버 강제 로직 추가는 범위 밖.

## 관련 코드 위치

- `src/main/resources/static/seller/mypage.html`, `js/seller-mypage.js` — 신규
- `src/main/resources/static/seller/products/edit.html`, `js/seller-product-edit.js` — 신규
- `src/main/resources/static/css/components.css` — 마이페이지 목록/수익 카드 UI 규칙 추가
- `src/main/resources/static/partials/header.html` — "판매자 마이페이지" 링크 추가
- 경위: `docs/dev/frontend/seller-mypage/changes/001-seller-mypage.md`, 실행 로그: `docs/logs/frontend/seller-mypage/001-seller-mypage.md`
