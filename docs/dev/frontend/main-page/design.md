# 메인 페이지 (`/`) (frontend/main-page) — Design

## 개요

GONG9RI의 첫 화면(`/`)이다. 공통 디자인 시스템(`docs/dev/frontend/design-system/design.md`) 위에서 구현된 첫 개별 기능 페이지로, 진행 중인 공동구매 상품을 카드 목록으로 보여준다. React/Vue 등 프레임워크 없이 정적 HTML/CSS/JS로 작성되어 있으며, 헤더/푸터/토큰/컴포넌트 CSS는 디자인 시스템 산출물을 그대로 재사용한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── index.html               # 메인 페이지 마크업 (헤더/푸터 include + 프로모션 바 + 카테고리/정렬 + 상품 카드 그리드 + "더 보기" 버튼)
└── js/
    └── main.js               # 프로모션 바 캐러셀, 카테고리/정렬 상태 관리, `Api.get('/products')` 호출 → 카드(+진행바) 렌더링, 페이지네이션
```

- `css/components.css`에 `.product-status`(로딩/빈 목록/에러 공통 안내), `.load-more-wrap`, `.btn[hidden] { display: none; }`(버튼에 `hidden` 속성이 항상 적용되도록 하는 specificity 보정 규칙), `.promo-bar*`(프로모션 바), `.category-bar*`/`.sort-select`(카테고리·정렬), `.card-progress*`(카드 진행바) 추가.
- 신규 CSS 파일 없음 — `css/tokens.css`, `base.css`, `layout.css`, `components.css`, `js/api.js`, `js/include.js`, `partials/header.html`, `partials/footer.html`을 그대로 재사용.

### 프로모션 바(`#promo-bar`)

친구 피드백(큰 그라디언트 히어로 대신 한 줄짜리 공지 바 스타일)을 반영해 만든 슬림 배너. 문구 3개를 4초 간격으로 opacity 페이드 전환하고(`prefers-reduced-motion: reduce`면 자동 전환 안 함), 점 클릭으로 수동 전환도 가능하다. **각 슬라이드는 실제로 이동 가능한 링크를 갖는다**(죽은 링크 금지):
- 1번(인기 상품): `GET /api/products?sort=POPULAR&size=1`로 실제 인기순 1위 상품을 조회해 상품명·참여 인원을 채우고 그 상품 상세로 링크한다. 진행 중인 팀이 있는 상품이 하나도 없으면(요청 실패 포함) 기본 문구(그리드로 스크롤)를 그대로 유지한다 — 실제로 없는 이벤트/상품을 지어내지 않는다.
- 2번(신규 가입): 실제 카카오 로그인/가입 흐름(`/api/auth/kakao/login`)으로 바로 연결.
- 3번(공구 둘러보기): `#product-grid`로 스크롤.

### 카테고리 바 + 정렬(`#category-bar`, `#sort-select`)

`docs/dev/product/list-enhancements/design.md` 참고. 카테고리 pill(전체+6종) 클릭, 정렬 select("최신순"/"인기순") 변경 둘 다 `?category=`/`?sort=` 쿼리파라미터를 URL에 반영(`history.replaceState`)하고 기존 페이지네이션 상태를 초기화한 뒤 목록을 처음부터 다시 불러온다.

### 검색창(`#search-form`)

`docs/dev/product/list-enhancements/design.md` 참고. 상품명 또는 판매자명으로 검색, 제출 시 `?keyword=`를 URL에 반영하고 카테고리/정렬과 동일하게 목록을 처음부터 다시 불러온다.

### 카드 참여 진행바

`activeTeamCurrentCount`/`activeTeamTargetParticipants`가 응답에 둘 다 있을 때만(RECRUITING 팀이 있을 때만) "N명 참여 중 · M명 달성 시 성사" 진행바 + "N% 달성" 배지(참고 사이트들의 공통 패턴)를 카드에 그린다.

### 카드 호버(입체감)

`transform: translateY(-8px) scale(1.015)` + `box-shadow: var(--shadow-lg)`(기존 `translateY(-4px)`+`shadow-md`보다 강화) — 사용자 피드백으로 상승 폭·확대·그림자를 전부 키움.

## 데이터 연동

- 데이터 소스: `GET /api/products`(`docs/api/product.md`). `SecurityConfig`에서 이미 `permitAll`이라 비로그인 상태에서도 호출된다.
- 필드 매핑: `name`→카드 타이틀, `basePrice`→기본가(취소선), `bestPrice`→베스트 공구가(강조), `sellerName`→판매자명, `maxParticipants`→"N인 모이면 1인당 최저가" 라벨. 사용자 입력 기반 문자열(`name`/`sellerName`)은 `textContent`로만 대입해 XSS를 방지한다.
- 상태: 로딩 중 안내 → 성공+목록 있음(카드 렌더링) / 성공+목록 없음(빈 상태 안내, 에러 아님) / 실패(`Api.get`이 던지는 `Error.message`를 에러 안내로 노출).
- 페이지네이션: 전체 페이지 번호 UI는 없음. "더 보기" 버튼 클릭 시 `page`를 1 증가시켜 재호출하고 응답 `content`를 기존 카드 뒤에 append, `loadedCount >= totalElements`가 되면 버튼을 숨긴다.

## 규칙 / 검증

- **상세 페이지 링크(이후 해소됨)**: 이 작업 시점엔 상세 페이지가 없어 카드가 `href="#"` placeholder였다. 이후 `frontend/product-detail` 작업에서 `js/main.js`가 실제 `product.html?id={productId}` 링크로 갱신됐다 — 상세: `docs/dev/frontend/product-detail/design.md`.
- **이미지**: `GET /api/products` 응답의 `imageUrl`이 있으면 카드 이미지 영역에 실제 이미지를 렌더링하고, 없으면 기존 placeholder 그라디언트를 그대로 유지한다(하위 호환) — 상세: `docs/dev/frontend/product-image/design.md`.
- **공구 상태 뱃지 없음**: `GET /api/products` 응답에 공구 상태 필드가 없어 상태 뱃지는 붙이지 않는다. (뱃지는 상세 페이지에서 팀 조회 API와 연동할 때 다룬다.)
- **로그인 상태 연동(이후 추가됨)**: 이 작업 시점엔 헤더가 디자인 시스템 단계와 동일하게 비로그인 고정 마크업이었다. 이후 `frontend/header-auth` 작업에서 실제 로그인 상태에 따라 헤더가 바뀌도록 연동됐다 — 상세: `docs/dev/frontend/header-auth/design.md`.
- **CSS specificity 주의**: `hidden` 속성이 붙는 요소에 `.btn`처럼 자체 `display` 값을 가진 클래스를 같이 쓸 경우, `.btn[hidden] { display: none; }`류의 속성 선택자 보정 규칙이 없으면 `hidden`이 무시된다(이번 작업에서 실제로 겪은 버그, `components.css`에 보정 규칙 추가로 해결).

## 관련 코드 위치

- `src/main/resources/static/index.html`, `src/main/resources/static/js/main.js`
- `src/main/resources/static/css/components.css`
- 경위: `docs/dev/frontend/main-page/changes/001-main-page.md`(최초 구현), `002-promo-category-sort-progress.md`(배너/카테고리/정렬/진행바), 실행 로그: `docs/logs/frontend/main-page/001-main-page.md`
