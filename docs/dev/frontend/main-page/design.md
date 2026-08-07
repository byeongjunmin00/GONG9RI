# 메인 페이지 (`/`) (frontend/main-page) — Design

## 개요

GONG9RI의 첫 화면(`/`)이다. 공통 디자인 시스템(`docs/dev/frontend/design-system/design.md`) 위에서 구현된 첫 개별 기능 페이지로, 진행 중인 공동구매 상품을 카드 목록으로 보여준다. React/Vue 등 프레임워크 없이 정적 HTML/CSS/JS로 작성되어 있으며, 헤더/푸터/토큰/컴포넌트 CSS는 디자인 시스템 산출물을 그대로 재사용한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── index.html               # 메인 페이지 마크업 (헤더/푸터 include + 히어로 문구 + 상품 카드 그리드 + "더 보기" 버튼)
└── js/
    └── main.js               # `Api.get('/products')` 호출 → 카드 렌더링, 로딩/빈 목록/에러 상태 처리, "더 보기" 페이지네이션
```

- `css/components.css`에 `.product-status`(로딩/빈 목록/에러 공통 안내), `.load-more-wrap`, `.btn[hidden] { display: none; }`(버튼에 `hidden` 속성이 항상 적용되도록 하는 specificity 보정 규칙) 추가.
- 신규 CSS 파일 없음 — `css/tokens.css`, `base.css`, `layout.css`, `components.css`, `js/api.js`, `js/include.js`, `partials/header.html`, `partials/footer.html`을 그대로 재사용.

## 데이터 연동

- 데이터 소스: `GET /api/products`(`docs/api/product.md`). `SecurityConfig`에서 이미 `permitAll`이라 비로그인 상태에서도 호출된다.
- 필드 매핑: `name`→카드 타이틀, `basePrice`→기본가(취소선), `bestPrice`→베스트 공구가(강조), `sellerName`→판매자명, `maxParticipants`→"N인 모이면 1인당 최저가" 라벨. 사용자 입력 기반 문자열(`name`/`sellerName`)은 `textContent`로만 대입해 XSS를 방지한다.
- 상태: 로딩 중 안내 → 성공+목록 있음(카드 렌더링) / 성공+목록 없음(빈 상태 안내, 에러 아님) / 실패(`Api.get`이 던지는 `Error.message`를 에러 안내로 노출).
- 페이지네이션: 전체 페이지 번호 UI는 없음. "더 보기" 버튼 클릭 시 `page`를 1 증가시켜 재호출하고 응답 `content`를 기존 카드 뒤에 append, `loadedCount >= totalElements`가 되면 버튼을 숨긴다.

## 규칙 / 검증

- **상세 페이지 링크**: `/products/{id}` 상세 페이지가 아직 없어 카드는 `href="#"` placeholder로만 존재한다(`TODO` 주석으로 표시). 상세 페이지가 생기면 이 링크를 실제 경로로 교체해야 한다.
- **이미지/공구 상태 뱃지 없음**: `GET /api/products` 응답에 이미지 URL과 공구 상태 필드가 없어, 카드 이미지 영역은 placeholder 그라디언트만 쓰고 상태 뱃지는 붙이지 않는다. (뱃지는 상세 페이지에서 팀 조회 API와 연동할 때 다룬다.)
- **로그인 상태 미연동**: 헤더는 디자인 시스템 단계와 동일하게 비로그인 고정 마크업을 그대로 쓴다.
- **CSS specificity 주의**: `hidden` 속성이 붙는 요소에 `.btn`처럼 자체 `display` 값을 가진 클래스를 같이 쓸 경우, `.btn[hidden] { display: none; }`류의 속성 선택자 보정 규칙이 없으면 `hidden`이 무시된다(이번 작업에서 실제로 겪은 버그, `components.css`에 보정 규칙 추가로 해결).

## 관련 코드 위치

- `src/main/resources/static/index.html`, `src/main/resources/static/js/main.js` — 신규
- `src/main/resources/static/css/components.css` — `.product-status`/`.load-more-wrap`/`.btn[hidden]` 규칙 추가
- 경위: `docs/dev/frontend/main-page/changes/001-main-page.md`, 실행 로그: `docs/logs/frontend/main-page/001-main-page.md`
