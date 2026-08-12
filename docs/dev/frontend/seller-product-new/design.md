# 판매 물품 등록 페이지 (frontend/seller-product-new) — Design

## 개요

판매자가 공동구매 상품을 등록하는 페이지(`/seller/products/new`). 상품명/설명/정가/최대인원과 동적으로 추가·삭제 가능한 가격구간(공구 인원별 할인가)을 입력받아 `POST /api/products`로 등록한다. 지금까지의 프론트 페이지와 달리 최초로 `static/` 서브디렉토리(`seller/products/`)에 위치한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── seller/products/new.html    # 등록 폼 (헤더/푸터 include, 절대경로 참조)
└── js/
    └── seller-product-new.js    # 가격구간 행 관리, 클라이언트 가드레일, POST /api/products
```

- `css/components.css`에 `.price-tier-rows`/`.price-tier-row`/`.price-tier-row__field`/`.price-tier-row__remove` 추가. 삭제 버튼은 기존 `.btn` 클래스를 그대로 써서 이미 있는 `.btn[hidden]` 보정 규칙을 재사용(신규 CSS 버그 없음).
- **`SecurityConfig.java` 변경**: 정적 리소스 permitAll 매처에 `"/**/*.html"`을 기존 `"/*.html"`에 추가(서브디렉토리 html도 비로그인 열람 가능하게 일반화 — 향후 `/seller/mypage`, `/buyer/mypage` 등에도 재사용 가능). `/api/**` 인가 규칙은 변경 없음.
- `partials/header.html`: "판매 물품 등록" 링크(`/seller/products/new.html`)의 상단 주석을 실제 페이지로 갱신.

## 데이터 연동

- 폼 로드 시 API 호출 없음(로그인 상태 사전 확인 안 함 — 다른 페이지와 동일 원칙).
- **이미지 입력 필드(이후 추가됨)**: 이 작업 시점엔 이미지 입력 필드가 없었고 `POST /api/products`도 `imageUrl`을 받지 않았다. 이후 `frontend/product-image` 작업에서 "상품 이미지 URL" 입력 필드(`#imageUrl`)가 추가되고 `POST /api/products` 요청 본문에도 `imageUrl`이 포함되도록 백엔드(`ProductRegisterRequest.imageUrl`)까지 함께 확장됐다 — 상세: `docs/dev/frontend/product-image/design.md`.
- 가격구간: 최소 1행 유지, "행 추가"로 무제한 추가 가능. 클라이언트 가드레일(오름차순, 중복 `minCount` 금지, `2 ≤ minCount ≤ maxParticipants` 권장)은 UX 보조일 뿐 SSOT가 아니다 — 서버가 이 규칙들을 강제하지 않으므로(코드 확인) 최종 판정은 여전히 서버 `400 VALIDATION_FAILED` 응답.
- 제출: `POST /api/products {name, description, basePrice, maxParticipants, priceTiers}`. 성공(201) → `product.html?id={새 productId}`(절대경로)로 이동. 실패: 401(로그인 필요+링크)/403(서버 message)/400(서버 message, 공통 배너).
- "취소하기"는 API 호출 없이 `/`로 이동하는 정적 링크.
- 서버 응답 문자열은 `textContent`로만 대입(XSS 방지).

## 규칙 / 검증

- **서브디렉토리 페이지의 CSS/JS/partial 참조는 절대경로여야 한다**(`/css/...`, `/js/...`, `/partials/...`) — 상대경로를 쓰면 `/seller/products/` 기준으로 잘못 풀린다. `product.html`로의 이동도 절대경로(`/product.html?id=...`)로 해야 한다(상대경로면 `/seller/products/product.html`로 잘못됨).
- **가격구간 정합성은 서버가 강제하지 않는다**: 오름차순/중복/범위는 서버 검증이 없어(코드 확인), API를 직접 호출하면 클라이언트 가드레일을 우회할 수 있다. 서버 측 강제가 필요하면 별도 백엔드 작업.
- **권한 사전 확인 없음**: 헤더 로그인 상태 미연동 원칙과 동일하게, 비로그인/구매자 상태에서도 폼은 항상 노출되고 결과는 서버 응답(401/403)으로만 판정한다.

## 관련 코드 위치

- `src/main/resources/static/seller/products/new.html`, `js/seller-product-new.js` — 신규
- `src/main/resources/static/css/components.css` — 가격구간 입력 UI 규칙 추가
- `src/main/java/com/gong9ri/gong9ri/config/SecurityConfig.java` — `"/**/*.html"` permitAll 매처 추가
- `src/main/resources/static/partials/header.html` — 상단 주석 갱신
- 경위: `docs/dev/frontend/seller-product-new/changes/001-seller-product-new.md`, 실행 로그: `docs/logs/frontend/seller-product-new/001-seller-product-new.md`
