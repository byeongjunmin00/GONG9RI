# 상품 상세조회 페이지 (frontend/product-detail) — Design

## 개요

GONG9RI 상품 상세 페이지다. 상품 기본 정보(이름/설명/기본가/가격구간표)와 모집 중인 공동구매 팀 목록을 보여주고, "혼자 구매하기"/"기존 팀 참가하기"/"신규 팀 신설하기"/"계속 쇼핑하기" 네 가지 액션의 진입점이다. 공통 디자인 시스템 위에서 정적 HTML/CSS/JS로 동작한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── product.html             # 상품 정보 + 팀 목록 + 액션 버튼 + 공통 안내 배너
└── js/
    └── product.js            # 쿼리 파라미터 파싱, 상품/팀 조회·렌더링, 액션 핸들러
```

- 라우팅: 쿼리스트링 `product.html?id={productId}` (정적 리소스 서빙 구조상 `/products/{id}` 경로 세그먼트 라우팅 불가).
- `css/components.css`에 `.product-detail`(+`[hidden]` 보정 규칙), `.product-price-box`/`.product-price-row`, `.price-tiers-table`, `.product-actions`, `.team-list`/`.team-item`/`.team-item-info`/`.team-item-count` 추가.
- `js/main.js`: 상품 카드 링크를 `product.html?id={productId}`로 연결(과거 `href="#"` placeholder에서 갱신).

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
