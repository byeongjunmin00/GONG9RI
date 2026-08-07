# 결제창 페이지 (frontend/checkout) — Design

## 개요

GONG9RI 결제창 페이지다. 상품 상세 페이지(`product-detail`)의 "혼자 구매하기"/"신규 팀 신설하기"/"기존 팀 참가하기" 세 액션 뒤에서 실제 결제(`POST /api/payments`)를 처리한다. 정적 HTML/CSS/JS로 동작하며, 공통 디자인 시스템 위에서 `product-detail`의 컴포넌트(`.product-detail`, `.product-price-box`, `.price-tiers-table`, `.form-alert`, `.product-status`)를 재사용한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── checkout.html             # 상품 정보 확인 + 결제하기/삭제(취소) + 결제 완료 요약
└── js/
    └── checkout.js            # 쿼리 파라미터 파싱, 상품 조회·렌더링, 결제 처리
```

- 라우팅: 쿼리스트링 `checkout.html?productId={productId}`(혼자구매) 또는 `checkout.html?productId={productId}&teamId={teamId}`(공구팀 결제). 파라미터명은 `POST /api/payments` 요청 필드명(`productId`/`teamId`)과 동일하게 맞췄다(`product.html`의 `id`와는 다른 이름).
- CSS 신규 규칙 없음 — 기존 `css/components.css`의 `.product-detail`(`[hidden]` 보정 포함)/`.product-price-box`/`.product-price-row`/`.price-tiers-table`/`.form-hint`/`.form-alert`/`.product-status`를 그대로 재사용한다.

## 데이터 연동

- `productId`(필수, `/^[1-9]\d*$/`), `teamId`(선택, 있으면 같은 정규식) 쿼리 파라미터를 검증. 둘 중 하나라도 형식이 잘못되면 API 호출 없이 "잘못된 접근" 상태만 보여준다.
- `GET /api/products/{productId}` → 판매자명/상품명/`basePrice`/`priceTiers` 렌더링. `PRODUCT_NOT_FOUND`(404) → "상품을 찾을 수 없습니다" 안내(결제 영역은 계속 `hidden`).
- 금액 표시:
  - `teamId` 없음(혼자구매): `basePrice`를 "결제 금액"(최종 확정 금액)으로 표시.
  - `teamId` 있음(공구팀): `basePrice`+`priceTiers`를 "정가 (참고)"로만 표시하고, "결제 시점 팀 인원 기준으로 서버가 확정한다"는 안내 문구를 노출. 클라이언트는 실제 결제 금액을 계산하지 않는다(구간 선택 로직은 서버 `PaymentService`에만 있고, 팀 단건 조회 API가 없어 재현 불가).
  - `teamId`의 존재/정원 여부는 사전 검증하지 않는다 — `POST /api/payments` 응답으로만 사후 판정.
- 상품명/판매자명/서버 에러 message/`status`/`paidAt`은 전부 `textContent`로만 대입(XSS 방지, `innerHTML` 미사용).

## 액션 처리

| 액션 | 호출 | 성공 | 실패 |
|---|---|---|---|
| 결제하기 | `POST /api/payments` `{ productId, teamId? }` | 결제 완료 요약(상품명/금액/상태/결제시각) 노출, 결제 영역은 숨김 | 400 `VALIDATION_FAILED`(서버 message)/401 `UNAUTHORIZED`(로그인 필요+링크)/403 `FORBIDDEN`(서버 message, 판매자 계정)/404 `PRODUCT_NOT_FOUND`·`TEAM_NOT_FOUND`(서버 message)/409 `TEAM_FULL`(서버 message) |
| 삭제(취소) | 없음(호출 없음) | `product.html?id={productId}`로 이동 | — |
| 계속 쇼핑하기(결제 완료 후) | 없음 | `/`로 이동 | — |

## 규칙 / 검증

- **결제 취소/환불 API 없음**: `POST /api/payments`는 즉시 `PAID` 확정이며 별도 취소 API가 없다. 팀 미성사 시 자동 환불만 백엔드 스케줄러(`docs/policy/refund-trigger.md`)가 처리한다.
- **공구팀 결제 사전 검증 불가**: `teamId` 단건 상태 조회 API가 없어(목록 API는 `RECRUITING`만 반환), 팀이 결제 시점에 이미 `SUCCESS`/`FAILED`여도 이 페이지는 사전에 알 수 없다. `POST /api/payments` 응답(`TEAM_NOT_FOUND`/`TEAM_FULL`)으로만 사후에 알게 된다.
- **결제 링크를 무시하는 경우**: 팀 신설/참가는 성공해도 결제로 이어지는 것은 사용자가 안내 배너의 링크를 클릭해야 한다(자동 리다이렉트 없음). 링크를 무시하면 결제 미완료 상태로 팀원이 남을 수 있다(범위 밖).
- **쿼리 파라미터 이름 혼동 소지**: `product.html`은 `id`, `checkout.html`은 `productId`를 쓴다(결제 API 필드명과 맞춘 의도적 선택). 두 페이지를 오가는 링크 작성 시 주의.
- **헤더 로그인 상태 미연동 · 로그인 복귀 경로 없음**: 401을 만나 로그인 페이지로 이동해도 로그인 후 checkout으로 자동 복귀하지 않는다(auth 단계부터의 기존 제약).
- **"삭제(취소)"의 의미**: 백엔드에 장바구니/보류 중 결제 개념이 없어(결제는 호출 즉시 `PAID` 확정), "결제를 진행하지 않고 이 화면에서 나가기"로 해석했다. API 호출 없이 상품 상세 페이지로 돌아간다.

## product-detail 연동 변경

- **혼자 구매하기**(`handleBuyAlone`): 기존 "결제 기능은 준비 중입니다" placeholder를 제거하고 `checkout.html?productId={productId}`로 이동한다.
- **신규 팀 신설하기**/**기존 팀 참가하기** 성공 시: 기존 안내 배너 + 팀 목록 재조회는 유지하며, 배너에 결제로 이동하는 링크(`#page-alert-pay-link`, `checkout.html?productId={productId}&teamId={teamId}`)를 추가로 노출한다. 자동 리다이렉트는 하지 않는다.
- 신설/참가 모두 결제 링크를 노출하는 이유: 백엔드 코드(`PaymentService.requireRoomOrAlreadyJoined`)가 "팀 참가는 team/join·team/create에서 이미 완결되고, 결제는 그 결과를 다시 검증만 한다"는 전제로 짜여 있어, 신설·참가 모두 "먼저 팀원이 되고 그다음 결제한다"는 동일한 2단계 흐름이기 때문(`docs/api/team.md`의 문서 공백은 누락으로 판단).
- 관련 변경은 `docs/dev/frontend/product-detail/design.md`의 "액션 처리"/"규칙 / 검증"에도 반영돼 있다(placeholder 해소).

## 관련 코드 위치

- `src/main/resources/static/checkout.html`, `js/checkout.js` — 신규
- `src/main/resources/static/js/product.js` — `handleBuyAlone`/`handleJoin`/`handleCreateTeam`/`showPageAlert`/`hidePageAlert` 수정
- `src/main/resources/static/product.html` — `#page-alert-pay-link` 슬롯 추가
- `docs/api/payment.md` — `FORBIDDEN`(403) 에러 코드 보강
- 경위: `docs/dev/frontend/checkout/changes/001-checkout.md`, 실행 로그: `docs/logs/frontend/checkout/001-checkout.md`
