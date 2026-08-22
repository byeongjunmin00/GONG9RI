# 상품 상세조회 페이지 (frontend/product-detail) — Design

## 개요

GONG9RI 상품 상세 페이지다. 상품 기본 정보(이름/기본가/가격구간표)와 모집 중인 공동구매 팀 목록을 보여주고, "혼자 구매하기"/"기존 팀 참가하기"/"신규 팀 신설하기"/"계속 쇼핑하기" 네 가지 액션의 진입점이다. 그 아래에 **상품정보 / 리뷰 / 문의** 3개 탭으로 콘텐츠를 전환해서 볼 수 있다. 공통 디자인 시스템 위에서 정적 HTML/CSS/JS로 동작한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── product.html             # 상품 정보 + 팀 목록 + 액션 버튼 + 공통 안내 배너
└── js/
    └── product.js            # 쿼리 파라미터 파싱, 상품/팀 조회·렌더링, 액션 핸들러
```

- 라우팅: 쿼리스트링 `product.html?id={productId}` (정적 리소스 서빙 구조상 `/products/{id}` 경로 세그먼트 라우팅 불가).
- `css/components.css`에 `.product-detail`(+`[hidden]` 보정 규칙), `.product-price-box`/`.product-price-row`, `.price-tiers-table`, `.product-actions`, `.team-list`/`.team-item`/`.team-item-info`/`.team-item-count`, `.product-tabs`/`.product-tab`/`.product-tab-panel`, `.mypage-list-item__date`(리뷰·문의 작성일시), `#reviews-list.mypage-list .mypage-list-item`/`#inquiries-list.mypage-list .mypage-list-item`(박스 스타일 스코프 무효화), `#product-image .card-wishlist-btn`(찜 하트 크기 조정) 추가.
- `js/main.js`: 상품 카드 링크를 `product.html?id={productId}`로 연결(과거 `href="#"` placeholder에서 갱신).

## 탭 UI (상품정보 / 리뷰 / 문의)

- `team-list-section`(모집 중인 공구팀) 다음, `product-tabs-section` 안에 탭 네비게이션(`role="tablist"`,
  버튼 3개 `.product-tab`)과 패널 3개(`.product-tab-panel`)를 둔다. 팀 목록은 참가하기와 직결된 구매
  액션이라 탭 밖에 그대로 둔다.
- 탭: `product-tab-info`(기본 활성) / `product-tab-reviews` / `product-tab-inquiries`.
- 패널:
  - **상품정보** (`product-info-panel`): `#product-description`(`product.description`을 `textContent`로
    렌더). 설명이 비어 있으면 `#product-description-status`(`product-status--empty` 패턴)로 "등록된
    상품 설명이 없습니다." 안내. 과거에는 상품명 옆 헤더에 흐린 문단으로만 노출됐으나, 이 작업으로
    헤더(`section__head`)에는 판매자/상품명만 남고 설명은 이 패널로 이동했다.
  - **리뷰** (`reviews-panel`, 기존 `.reviews-section`): 리뷰 기능 전체(평균 평점, 목록, 작성/수정
    폼). 패널 안에서 **작성 폼(`#review-form`)이 목록(`#reviews-list`)보다 앞**에 온다(순수 마크업
    순서, `product.js`는 `getElementById` 기반이라 순서 무관 — 회귀 없음). 각 항목(`createReviewItem`)은
    작성자·별점·본문 아래에 `review.createdAt`을 `.mypage-list-item__date`(`toLocaleString('ko-KR')`
    절대 날짜+시각, 기존 코드베이스 관행과 통일)로 표시하고, 카드형 박스(배경/테두리/hover 그림자)는
    보이지 않는다 — `li.className`은 여전히 `mypage-list-item`이고, `components.css`의
    `#reviews-list.mypage-list .mypage-list-item` 스코프 선택자가 이 리스트 안에서만 박스 스타일을
    무효화한다(`.mypage-list-item` 베이스 규칙 자체는 불변이라 buyer/seller-mypage, admin-refunds의
    같은 클래스 재사용 화면에는 영향 없음).
  - **문의** (`inquiries-panel`, 기존 `.inquiries-section`): 문의 기능 전체(개수 표시, 목록, 작성/수정
    폼, 판매자 답변 인라인 폼). 리뷰와 동일하게 **작성 폼(`#inquiry-form`)이 목록(`#inquiries-list`)
    보다 앞**에 오고, 각 문의 항목에 `inquiry.createdAt`을 같은 방식으로 표시하며,
    `#inquiries-list.mypage-list .mypage-list-item` 스코프로 박스 스타일이 제거된다. 상세:
    `docs/dev/inquiry/crud/design.md`.
- 탭 전환(`js/product.js`의 `switchTab()`/`setUpTabs()`)은 **표시/숨김(`hidden`)과 `is-active`/
  `aria-selected` 토글만** 수행한다. 리뷰/문의 데이터 재조회는 하지 않는다 — 데이터는 탭 UI와 무관하게
  기존 트리거(`init()`의 `loadReviews`/`loadInquiries` 즉시 호출, `loadProduct()` 성공 후
  `loadInquiries` 재호출, `gong9ri:auth-resolved` 도착 시 재호출)로 항상 먼저 로드돼 있다.

## 찜(위시리스트) 버튼

`#product-image`(상품 사진) 위에 `#product-wishlist-btn`을 둔다. 상세: `docs/dev/product/wishlist/design.md`.

- `main.js`(메인 페이지 카드)가 쓰는 `.card-wishlist-btn` 마크업/아이콘/정책을 그대로 재사용한다.
  `main.js`는 인덱스 전용 클로저라 직접 호출할 수 없어, `product.js`가 `loadWishlistState()`/
  `handleToggleWishlist()`로 동일 로직을 독립적으로 구현한다.
- `renderGallery()`가 캐러셀 전환마다 `#product-image`의 자식을 전부 지우고 `<img>`만 다시 그리는
  구조라, 정적 HTML에 넣은 하트 버튼 노드(`wishlistBtnEl`)를 참조로 들고 있다가 `renderGallery()`가
  다시 그릴 때마다 `imageEl.appendChild(wishlistBtnEl)`로 재부착한다.
- 초기 active 상태: 로그인한 구매자(`currentMemberRole === 'BUYER'`)에 한해 `GET
  /api/buyer/mypage/wishlist`(개별 조회 API가 없어 전체 목록에서 현재 상품 id 포함 여부로 판정)를
  `gong9ri:auth-resolved` 도착 시점에 호출한다.
- 클릭 시 멱등 `POST`/`DELETE /api/products/{id}/wishlist` + 낙관적 토글, 비로그인이면
  `/login.html?redirect=...`, 403(판매자 계정)이면 `showPageAlert`로 안내 — `main.js`의
  `toggleWishlist`와 동일 정책.
- `components.css`의 `#product-image .card-wishlist-btn`이 상세 페이지의 큰 사진에 맞게 하트
  크기(40px)만 키운다(`.card-wishlist-btn` 자체 정의는 불변, 메인 카드 하트에는 영향 없음).

## 데이터 연동

- `id` 쿼리 파라미터를 `/^[1-9]\d*$/`로 검증 — 없음/비숫자/0/음수/소수는 API 호출 없이 "잘못된 접근" 상태.
- `GET /api/products/{id}` → 상품 정보 렌더링. `PRODUCT_NOT_FOUND`(404) → "상품을 찾을 수 없습니다" 안내(상세 영역은 `hidden`으로 완전히 숨김).
- `GET /api/products/{id}/teams` → `RECRUITING` 팀 목록 렌더링(빈 배열은 에러가 아닌 빈 상태). 상태 뱃지는 응답 `status` 값을 기존 `.badge-recruiting`/`.badge-success`/`.badge-failed`에 매핑.
- 상품명/설명/판매자명/서버 에러 message는 전부 `textContent`로만 대입(XSS 방지).

## 액션 처리

| 액션 | 호출 | 성공 | 실패 |
|---|---|---|---|
| 팀 참가 | `POST /api/teams/{teamId}/join` | 안내 배너(결제 이동 링크 포함, `checkout.html?productId={id}&teamId={teamId}`) + 목록 재조회 | 401(로그인 필요+링크)/403(서버 message)/409 `TEAM_FULL`·`ALREADY_JOINED`(서버 message+재조회)/404(서버 message+재조회) |
| 팀 신설 | `POST /api/products/{id}/teams` | 안내 배너(결제 이동 링크 포함, 신설된 `teamId` 사용) + 목록 재조회 | 위와 동일 |
| 혼자 구매하기 | 없음(이동만) | `checkout.html?productId={id}`로 이동 | — |
| 계속 쇼핑하기 | 없음 | `/`로 이동 | — |

## 규칙 / 검증

- **결제 연동**: `checkout.html`(결제창 페이지)이 추가되면서 "혼자 구매하기"는 즉시 결제창으로 이동하고, "팀 신설"/"팀 참가" 성공 배너에는 결제로 이동하는 링크가 노출된다(자동 리다이렉트는 하지 않는다 — 사용자가 결과를 확인한 뒤 스스로 넘어간다). 상세는 `docs/dev/frontend/checkout/design.md` 참고. 결제 링크를 무시하면 결제 미완료 상태로 팀원(신설자 포함)이 남을 수 있는데, 이 상태의 후속 처리(리더/참가자 결제 마감 정책 등)는 이 페이지의 범위 밖이다.
- **헤더는 연동됨, 페이지 액션 버튼은 미연동**: 헤더 자체는 이후 `frontend/header-auth` 작업으로 로그인 상태에 따라 바뀌게 됐다(`docs/dev/frontend/header-auth/design.md`). 다만 이 페이지의 참가/신설 버튼은 그와 별개로 로그인 여부를 사전에 확인하지 않아 항상 노출되고, 결과는 여전히 서버 응답(401 등)으로만 판정한다.
- **CSS `hidden` 속성 주의**: `hidden` 속성을 쓰는 요소가 자체 `display`를 선언한 클래스(`.product-detail` 등)와 결합되면 반드시 `.클래스[hidden] { display: none; }` 보정 규칙이 필요하다(이번 작업에서 `.product-detail`에 실제로 이 버그가 있었고 수정함 — `.btn[hidden]`과 동일 패턴).
- **로그인 후 복귀 지원(이후 추가됨)**: 이 작업 시점엔 `login.js`가 로그인 성공 시 항상 `/`로 리다이렉트해 복귀 경로 파라미터를 지원하지 않았다. 이후 `feat(frontend): 로그인 후 원래 페이지로 복귀` 작업에서 `login.js`가 `?redirect=` 파라미터를 지원하게 됐고, 이 페이지의 "로그인이 필요합니다" 링크도 `?redirect={현재 경로}`를 붙인다(`js/product.js`) — 401을 만나 로그인 페이지로 이동해도 로그인 성공 시 이 상세 페이지로 복귀한다.

## 관련 코드 위치

- `src/main/resources/static/product.html`, `js/product.js` — 신규
- `src/main/resources/static/css/components.css` — 상세 페이지 전용 규칙 추가
- `src/main/resources/static/js/main.js` — 카드 링크 갱신
- 경위: `docs/dev/frontend/product-detail/changes/001-product-detail.md`, 실행 로그: `docs/logs/frontend/product-detail/001-product-detail.md`
- 탭 UI 추가 경위: `docs/dev/frontend/product-detail/changes/002-product-detail-tabs.md`, 실행 로그:
  `docs/logs/frontend/product-detail/002-product-detail-tabs.md`
- 리뷰·문의 작성일시/박스 제거/폼 위치 + 찜 버튼 경위:
  `docs/dev/frontend/product-detail/changes/003-ui-polish.md`, 실행 로그:
  `docs/logs/frontend/product-detail/005-ui-polish.md`
