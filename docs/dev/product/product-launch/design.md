# 오픈예정 상품 (product/product-launch) — Design

## 개요

판매자가 상품을 미리 등록해두고 특정 시각부터 공개(구매 가능)되게 예약할 수 있다. 와디즈의 "오픈예정" 탭에서 착안했지만, 처음엔 스코프를 상품 단위 필드 하나로 좁혔다 — 상품은 항상 목록/상세에 노출되며(둘러보기는 항상 가능) 구매·팀 신설만 시각 도달 전까지 막는다. 이후 별도 브라우징 탭이 추가됐다 — 메인 페이지 카테고리 바에 "오픈예정" 탭이 생기고, 특정 카테고리 탭에서는 아직 오픈 전인 상품이 제외되는 규칙까지 확장됐다(`docs/dev/product/list-enhancements/design.md`의 "오픈예정 필터" 절 참고, `docs/dev/ongoing/product-open-soon-tab.md`).

## API / 인터페이스

- `POST`/`PUT /api/products`: `openAt`(선택, `LocalDateTime`, `@Future`) — 생략하면 기존과 동일하게 즉시 공개.
- `GET /api/products`, `GET /api/products/{id}`: 응답에 `openAt` 포함.
- `POST /api/payments`, `POST /api/products/{id}/teams`: `openAt`이 미래인 상품이면 `409 PRODUCT_NOT_YET_OPEN`.

## 데이터 모델

`product.open_at` — nullable `DATETIME`, 기본값 없음(신규 컬럼이지만 NULL이 곧 "이미 공개" 의미라 기존 row도 자연스럽게 하위 호환됨, `category`/`autoRefundOnCancel`처럼 `@ColumnDefault`가 필요 없다).

## 규칙 / 검증

- **차단 지점은 2곳뿐**: 혼자구매/팀결제(`PaymentService.create()`)와 신규 팀 신설(`TeamService.create()`). 기존 팀에 참가(`TeamService.join()`)는 별도로 막지 않는다 — 팀이 존재한다는 것 자체가 그 팀 생성 시점에 이미 공개 상태였다는 뜻이라(팀 신설이 이미 막혀있으므로) 구조적으로 안전하다. 예외적으로 판매자가 이미 팀이 있는 상품의 `openAt`을 나중에 미래로 재설정하는 기이한 케이스는 의도적으로 다루지 않는다(스코프 밖).
- `Product.isNotYetOpen()` — `openAt != null && openAt.isAfter(now)`.
- 프론트: 메인 카드에 "오픈예정" 배지(마감임박 배지와 배타적, 오픈 전 상품은 RECRUITING 팀을 가질 수 없어 구조적으로 동시에 뜨지 않음), 상품 상세 페이지에 안내 배너 + "혼자 구매하기"/"신규 팀 신설하기" 비활성화. **이건 UX 보조일 뿐 최종 판정은 항상 서버**다 — 마감임박 배지처럼 상품 상세를 열어둔 채로 오픈 시각이 지나가도 새로고침 전까진 프론트 상태가 그대로지만, 그 사이 실제로 구매를 시도하면 서버가 정상 처리한다(기능상 문제 없음, `product.js`의 `updateOpenAtNotice()` 주석 참고).
- 검색·정렬은 오픈예정 여부와 무관하게 기존과 동일하게 동작한다(오픈예정 상품도 검색된다) — "숨기지 않고 보여주되 구매만 막는다"는 원칙과 일관됨. **카테고리 조회는 예외다**: "전체" 조회와 카테고리 미지정 검색은 여전히 오픈예정 여부와 무관하게 동일하게 동작하지만, 특정 카테고리를 지정한 조회는 이제 오픈예정 상품을 제외한다(오픈 전까지 자신의 실제 카테고리 탭에는 보이지 않고 "전체"·"오픈예정" 탭에서만 보인다) — `docs/dev/product/list-enhancements/design.md`의 "오픈예정 필터" 절 참고.

## 관련 코드

`entity/Product.java`(`openAt`, `isNotYetOpen()`), `dto/ProductRegisterRequest.java`(`@Future openAt`), `dto/ProductResponse.java`/`ProductSummaryResponse.java`, `service/PaymentService.create()`, `service/TeamService.create()`, `common/exception/ErrorCode.PRODUCT_NOT_YET_OPEN`, `static/seller/products/{new,edit}.html`+js(datetime-local 입력), `static/js/main.js`(오픈예정 배지), `static/product.html`+`js/product.js`(`#open-at-notice`, `updateOpenAtNotice()`).
