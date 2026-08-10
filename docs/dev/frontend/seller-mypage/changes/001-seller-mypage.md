# 판매자 마이페이지 (`/seller/mypage`)

대상: frontend/seller-mypage
담당: 전용운

## 배경 / 요구

`docs/WIREFRAME.md` "7. 판매자 마이페이지"를 만든다: 등록 완료 물품 목록(상품 카드/상품명/가격) + 물품 수정 및 삭제 + 수익 현황 확인 + 공구 참여 현황 보기.

API는 `docs/api/mypage.md`에 이미 있다(판매자 전용, 전부 `UNAUTHORIZED`(401)/`FORBIDDEN`(403, 구매자 계정) 에러 포함):
- `GET /api/seller/mypage/products` — 등록 상품 목록. 필드: `productId`/`name`/`basePrice`/`maxParticipants`/`createdAt`.
- `GET /api/seller/mypage/revenue` — `{ totalRevenue, paidCount, refundedCount }`.
- `GET /api/seller/mypage/teams` — 내 상품에 개설된 팀 전체(`teamId`/`productId`/`productName`/`currentCount`/`maxParticipants`/`status`/`deadline`/`createdAt`).

물품 수정/삭제는 `docs/api/product.md`의 `PUT /api/products/{productId}`(전체 교체, 본인 상품만)/`DELETE /api/products/{productId}`(본인 상품만)를 그대로 쓴다.

## 코드 확인으로 파악한 사실

- **SecurityConfig는 이미 서브디렉토리 html을 허용한다**: `SecurityConfig.java`의 정적 리소스 permitAll 매처가 `.requestMatchers("/", "/*.html", "/**/*.html", "/css/**", "/js/**", "/partials/**").permitAll()`로 이미 일반화돼 있다(frontend/seller-product-new 작업에서 처리 완료, 코드로 재확인). 이번 작업에서 `SecurityConfig`를 또 건드릴 필요는 없을 것으로 보인다.
- **`GET /api/seller/mypage/products` 응답에는 `description`/`priceTiers`가 없다.** 수정 폼에 기존 값을 채우려면 이 목록 API가 아니라 `GET /api/products/{productId}`(상세 조회, `docs/api/product.md`)를 다시 호출해야 한다.
- `js/api.js`에 `Api.get`/`Api.put`/`Api.del`이 이미 있고 `del`은 `204 No Content` 응답을 `null`로 정상 처리한다 — 이번 작업에서 API 래퍼 자체를 손댈 필요는 없어 보인다.
- `partials/header.html`에는 아직 마이페이지 진입 링크가 전혀 없다("메인"/"판매 물품 등록"만 있음, 로그인/회원가입은 `.site-header__auth` 영역).
- `css/components.css`에 이미 `.badge-recruiting`/`.badge-success`/`.badge-failed`(공구 상태 뱃지)가 있고 `js/product.js`의 `statusToBadgeClass`/`statusToLabel`이 `RECRUITING`/`SUCCESS`/`FAILED` 문자열을 이 클래스/라벨로 매핑하는 로직을 이미 갖고 있다 — 공구 참여 현황 표시에 재사용 가능한 선례.
- `docs/dev/ongoing/`에 이 작업 외 다른 진행 중 작업 없음(README만 존재, 중복 없음).
- `docs/policy/`에서 이 작업과 관련된 정책은 `team-success-criteria.md`(정원 도달 즉시 `RECRUITING→SUCCESS`)와 `refund-trigger.md`(마감 지난 팀은 스케줄러가 `FAILED`+환불 처리) — 표시 문구에 참고 대상, 클라이언트가 판정 로직을 재구현하지 않는다(서버 `status` 값을 그대로 신뢰).

## 설계

### 산출물 / 라우팅

- 신규 정적 페이지 3장, 전부 `seller/` 서브디렉토리(CSS/JS/partial 참조는 절대경로 원칙 준수):
  - `src/main/resources/static/seller/mypage.html` — 마이페이지 본체(등록 상품 목록 + 수익 현황 + 공구 참여 현황).
  - `src/main/resources/static/seller/products/edit.html?id={productId}` — 상품 수정 폼(신설이 아닌 별도 파일). 기존 `seller/products/new.html`/`js/seller-product-new.js`(완료된 `frontend/seller-product-new` 기능)는 수정하지 않는다 — 마이페이지 작업 범위에서 그 완료된 개념을 건드리지 않기 위해 독립 파일로 신설한다(공통 폼 컴포넌트 클래스는 재사용 가능).
  - 대응 스크립트: `js/seller-mypage.js`, `js/seller-product-edit.js`(가칭, 정확한 파일명은 Generate 단계에서 확정).
- `partials/header.html`에 "판매자 마이페이지"(`/seller/mypage.html`) 진입 링크를 추가한다. 위치(nav vs 계정 영역)와 정확한 마크업은 Generate 단계에서 정하되, 기존 "판매 물품 등록"과 동일하게 **로그인 여부/역할과 무관하게 항상 노출**한다(헤더 로그인 상태 미연동 원칙 유지 — 비로그인/구매자 상태에서 클릭해도 페이지 자체는 열리고, 각 API 호출에서 401/403으로 사후 판정).

### 데이터 흐름

1. `seller/mypage.html` 로드 시 세 API를 각각 호출: `GET /api/seller/mypage/products`, `GET /api/seller/mypage/revenue`, `GET /api/seller/mypage/teams`. 로그인 상태 사전 확인 없음(기존 원칙 유지) — 세 호출 중 하나라도 `401`/`403`이면 그 섹션(또는 페이지 전체)에 안내를 띄운다.
2. 상품 목록 각 항목에 "수정"(→ `seller/products/edit.html?id={productId}`로 이동) / "삭제" 액션을 둔다.
   - 삭제: 사용자 확인 절차를 거친 뒤 `DELETE /api/products/{productId}` 호출 → 성공(`204`) 시 목록에서 제거(또는 재조회) / 실패(`403 FORBIDDEN`, `404 PRODUCT_NOT_FOUND`)는 안내만 하고 목록은 유지.
3. 수익 현황은 `revenue` 응답 3개 필드(`totalRevenue`/`paidCount`/`refundedCount`)를 그대로 표시(추가 계산 없음).
4. 공구 참여 현황은 `teams` 응답을 `status`별로 구분해 표시(뱃지/라벨은 `js/product.js`의 기존 매핑 재사용 가능): `RECRUITING`(모집 중, 마감까지 남은 기간 표시 가능 — `deadline` 필드 존재), `SUCCESS`(성사), `FAILED`(미성사, 환불 처리됨).
5. `seller/products/edit.html`: 쿼리 파라미터 `id`를 파싱(형식 검증은 `product.html`의 `id` 파싱과 동일한 방향) → `GET /api/products/{id}`로 기존 값(name/description/basePrice/maxParticipants/priceTiers)을 불러와 폼에 채운다 → 제출 시 `PUT /api/products/{id}` 호출(요청 body는 `POST /api/products`와 동일한 전체 교체 형식). 성공(`200`) → `seller/mypage.html`로 이동. 실패: `400 VALIDATION_FAILED`(서버 message)/`403 FORBIDDEN`(본인 상품 아님/구매자 계정)/`404 PRODUCT_NOT_FOUND`.
   - `GET /api/products/{id}`는 `sellerId`도 반환하므로, 본인 상품이 아닌 경우를 폼 로드 시점에 클라이언트에서 걸러낼지(UX 가드레일) 여부는 Generate 단계 판단에 맡긴다 — 어느 쪽이든 최종 판정은 서버 `PUT` 응답(`403`)이다.

## 확인 필요

1. **물품 수정/삭제를 이번 작업 범위에 포함할지**: 이 계획은 **포함하는 쪽으로 판단**했다(와이어프레임에 명시된 요구고, API가 이미 존재해 백엔드 선행 작업이 필요 없음). 승인 시 이 판단도 함께 승인하는 것으로 간주한다. 범위를 줄이고 싶다면(목록 조회 + 삭제만 하고 수정 폼은 후속 작업으로 미루는 등) 승인 전에 알려달라.
2. **헤더 "판매자 마이페이지" 링크의 정확한 위치**(주요 nav vs 로그인/회원가입 옆 계정 영역)는 Generate 단계에서 정한다 — 특별한 선호가 있으면 알려달라.

## 태스크

- [ ] (승인 후) `src/main/resources/static/seller/mypage.html` 마크업 작성 (헤더/푸터 include, 상품 목록+수정/삭제 액션, 수익 현황, 공구 참여 현황 3개 섹션)
- [ ] `js/seller-mypage.js` 작성 — 3개 API 호출·렌더링, 삭제 확인+호출+목록 갱신, 각 섹션 401/403/기타 에러 처리
- [ ] `src/main/resources/static/seller/products/edit.html` + 전용 스크립트 작성 — 기존 값 로드(`GET /api/products/{id}`)·폼 채움·`PUT /api/products/{id}` 제출·에러 처리
- [ ] `css/components.css`에 필요한 신규 규칙 추가(공구 상태 뱃지는 기존 `.badge-*` 재사용, `[hidden]` 보정 규칙 필요 시 함께 추가)
- [ ] `partials/header.html`에 "판매자 마이페이지" 링크 추가

## 평가(통과) 기준

`./gradlew bootRun` 후 브라우저로 아래를 확인한다.

- 판매자 계정으로 로그인 후 `/seller/mypage.html` 접속 시 본인이 등록한 상품 목록/수익 현황/공구 참여 현황이 정상 표시된다.
- 구매자 계정으로 로그인 후 접속 시 각 섹션(또는 페이지)에 `403 FORBIDDEN` 안내가 뜨고 페이지가 깨지지 않는다.
- 비로그인 상태로 접속 시 `401 UNAUTHORIZED` 안내(로그인 필요 + 로그인 페이지 링크)가 뜬다(페이지 자체는 401 JSON 없이 정상 렌더링된다 — SecurityConfig 매처 재확인).
- 상품 목록에서 "수정" 클릭 → 기존 값이 채워진 폼이 뜨고, 값 변경 후 저장하면 `PUT /api/products/{id}`가 호출되고 마이페이지로 돌아왔을 때 변경 사항이 반영돼 있다.
- 상품 목록에서 "삭제" 클릭(확인 절차 포함) → `DELETE /api/products/{id}` 호출 후 목록에서 사라진다.
- 본인 소유가 아닌 상품 id로 `edit.html?id=...`에 직접 접근해 저장을 시도하면 `403 FORBIDDEN` 안내가 뜬다.
- 공구 참여 현황에서 `RECRUITING`/`SUCCESS`/`FAILED` 상태가 서로 구분되어 표시된다(뱃지 또는 라벨).
- (코드 리뷰) 서버 응답 문자열은 `textContent`로만 대입(XSS 방지, `innerHTML` 미사용).

## 리스크 / 전제

- **수정 폼의 가격구간(`priceTiers`) 정합성은 서버가 강제하지 않는다**(seller-product-new 단계에서 이미 확인된 사실 — `docs/dev/frontend/seller-product-new/design.md` 참고). 수정 폼도 등록 폼과 동일한 UX 가드레일만 두고, 서버 측 강제 로직 추가는 범위 밖이다.
- **삭제된 상품이 이미 결제/공구팀과 연결돼 있는 경우의 서버 동작은 이번 계획에서 확인하지 않았다** — `DELETE /api/products/{productId}` API 문서에는 `PRODUCT_NOT_FOUND`/`FORBIDDEN`만 정의돼 있고, 연관 데이터(팀/결제) 존재 시의 동작(차단/연쇄 삭제 등)은 문서화돼 있지 않다. 서버가 이를 막지 않는다면 삭제 후 마이페이지의 "공구 참여 현황"·구매자 쪽 화면에 orphan 데이터가 남을 가능성이 있다(백엔드 정책 확인 필요, 이번 프론트 작업 범위 밖으로 둔다).
- **헤더 링크 추가는 buyer-mypage 작업과 같은 파일(`partials/header.html`)을 건드린다.** 두 작업(`frontend/seller-mypage`, `frontend/buyer-mypage`)을 순차적으로 진행한다면 문제 없으나, 병렬로 진행하면 편집 충돌 가능성이 있다.
