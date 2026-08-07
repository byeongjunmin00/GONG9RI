# 결제창 페이지 (`/checkout`)

대상: frontend/checkout
담당: 전용운

## 배경 / 요구

`docs/WIREFRAME.md` 5번 페이지("결제창 페이지")를 만들어, `product-detail` 단계에서 placeholder로 남겨둔 세 액션(혼자 구매하기 / 신규 팀 신설하기 / 기존 팀 참가하기)을 실제 결제(`POST /api/payments`, `docs/api/payment.md`)로 이어준다.

`product-detail`의 `design.md`에 이미 명시된 제약: "혼자 구매하기"는 API 호출/이동 없이 "결제 기능은 준비 중입니다" 안내만 띄우고, "신규 팀 신설하기" 성공 시에도 결제로 이어지지 않는다(`docs/dev/frontend/product-detail/design.md` "규칙/검증" 참고). 이번 작업으로 이 두 placeholder를 해소한다.

## 설계

### 산출물 / 라우팅

- 신규 정적 페이지: `src/main/resources/static/checkout.html` + 전용 스크립트 `src/main/resources/static/js/checkout.js`.
- 라우팅 방식: 쿼리스트링 — `checkout.html?productId={productId}` (혼자구매) 또는 `checkout.html?productId={productId}&teamId={teamId}` (공구팀 결제). `product.html?id=`가 이미 쓰는 쿼리스트링 방식(정적 리소스 서빙 구조상 경로 세그먼트 라우팅 불가)을 그대로 따르되, 파라미터명은 `POST /api/payments`의 요청 필드명(`productId`/`teamId`)과 동일하게 맞춘다(product.html의 `id`와는 다른 이름이라 혼동 소지가 있음을 리스크에 기록).
- CSS: 필요한 신규 규칙은 기존 `css/components.css`에 추가한다(토큰/베이스/레이아웃/헤더·푸터·`.form-alert`·`.product-status`·`.price-tiers-table` 등은 재사용). 정확한 클래스 구성은 Generate 단계에서 정한다. **주의**: `hidden` 속성을 쓰는 요소에 자체 `display`를 선언한 클래스를 같이 쓸 경우 `.클래스[hidden] { display: none; }` 보정 규칙이 필요하다(design-system의 `.btn[hidden]`, product-detail의 `.product-detail[hidden]`에서 이미 반복된 버그 — 새 요소를 만들 때 처음부터 적용한다).

### 데이터 흐름

1. `checkout.js`가 로드 시 `productId`(필수)·`teamId`(선택) 쿼리 파라미터를 읽는다.
   - `productId`가 없거나 `/^[1-9]\d*$/`에 맞지 않으면 API 호출 없이 "잘못된 접근" 상태를 보여준다(product.js의 `id` 검증 패턴 재사용).
   - `teamId`가 존재하는데 같은 정규식에 맞지 않으면(형식이 잘못된 경우) 마찬가지로 "잘못된 접근"으로 처리한다(부분적으로 무시하지 않고 형식 오류는 전부 막는다).
2. `GET /api/products/{productId}`로 상품 정보(판매자명/상품명/`basePrice`/`priceTiers`)를 가져와 렌더링한다.
   - `PRODUCT_NOT_FOUND`(404) → "상품을 찾을 수 없습니다" 안내로 전환, 결제 영역은 숨긴다.
3. 화면 표시:
   - `teamId`가 없으면(혼자구매): 상품명 + `basePrice`(정가)를 최종 결제 금액으로 표시한다. 이 값은 `PaymentService.create`가 `teamId == null`일 때 그대로 `product.getBasePrice()`를 쓰므로(코드 확인) 사전 표시와 실제 청구액이 항상 일치한다.
   - `teamId`가 있으면(공구팀 결제): 상품명 + `basePrice`와 `priceTiers` 표를 참고 정보로 표시하고, "실제 결제 금액은 결제 시점의 팀 인원 수를 기준으로 서버가 확정한다"는 안내 문구를 둔다. **의도적으로 클라이언트에서 정확한 금액을 미리 계산하지 않는다** — `resolveTeamPrice`(구간 선택 로직)는 서버(`PaymentService`)에만 있고, 이 팀의 현재 인원(`currentCount`)을 확인할 수 있는 단건 조회 API(`GET /api/teams/{teamId}`)가 없어(`docs/api/team.md`에 없음, 목록 API는 `RECRUITING` 팀만 반환) 클라이언트가 재현하려면 별도 가정이 필요해진다. 서버 응답(`amount`)을 유일한 확정 금액 소스로 둔다.
   - `teamId` 유효성(존재 여부, 정원 여부)은 이 페이지에서 사전 검증하지 않는다. `POST /api/payments` 호출 결과(`TEAM_NOT_FOUND`/`TEAM_FULL`)로만 판정한다.
4. "결제하기" 버튼 클릭 → `POST /api/payments` `{ productId, teamId }`(`teamId` 없으면 생략/`null`).
   - 성공(`201`): 결제 완료 상태로 전환한다 — 응답의 `productName`/`amount`/`status`/`paidAt`을 요약해서 보여주고, "결제하기" 버튼은 비활성화하거나 숨긴다. "계속 쇼핑하기"(홈 `/`) 링크를 제공한다. 별도 구매 내역 페이지(`/buyer/mypage`)가 아직 프론트에 없어 그쪽으로의 링크는 만들지 않는다(범위 밖).
   - 실패:
     - `400 VALIDATION_FAILED` → 공통 에러 배너에 서버 `message` 표시.
     - `401 UNAUTHORIZED` → "로그인이 필요합니다" 안내 + 로그인 페이지 링크(product-detail과 동일 패턴).
     - `403 FORBIDDEN`(판매자 계정으로 결제 시도) → 서버 `message` 표시. **이 코드는 기존 `docs/api/payment.md`에 빠져 있었는데, 실제 백엔드(`PaymentService.requireBuyer`)가 던지는 걸 코드로 확인해 이번 Plan에서 문서에 추가했다**(`docs/api/payment.md` 참고).
     - `404 PRODUCT_NOT_FOUND`/`TEAM_NOT_FOUND` → 서버 `message` 표시(이 페이지엔 재조회할 목록이 없으므로 안내만).
     - `409 TEAM_FULL` → 서버 `message` 표시(결제 시점 경합으로 정원이 찬 정상적인 경우).
5. "결제창 물품 삭제" 액션: **API 호출 없이** 상품 상세 페이지(`product.html?id={productId}`)로 돌아가는 것으로 구현한다. 판단 근거는 아래 "설계 판단 근거" 참고.

### product-detail(`product.html`/`js/product.js`) 연동 변경

- **혼자 구매하기**(`handleBuyAlone`): 기존 "결제 기능은 준비 중입니다" placeholder 배너를 제거하고, `checkout.html?productId={productId}`로 이동시킨다.
- **신규 팀 신설하기**(`handleCreateTeam`) 성공 시: 기존처럼 안내 배너를 띄우고 팀 목록을 재조회하는 것은 유지하되, 배너에 결제로 이동하는 링크(신설 응답의 `teamId` 사용, `checkout.html?productId={productId}&teamId={teamId}`)를 추가로 노출한다. **자동 리다이렉트는 하지 않는다** — 사용자가 결과(새 팀 생성)를 확인한 뒤 스스로 결제로 넘어가게 하며, 기존 "성공 시 배너만 띄우는" UX와 일관되게 유지한다.
- **기존 팀 참가하기**(`handleJoin`) 성공 시: 신설과 동일하게, 안내 배너에 결제로 이동하는 링크(참가한 `teamId` 사용)를 추가로 노출한다. 근거는 아래 "설계 판단 근거" 참고.
- 위 두 배너의 "결제하기" 링크는 `product.html`의 기존 `#page-alert-login-link`(조건부 표시 앵커) 슬롯과 유사하게, 안내 배너 영역에 조건부로 노출되는 링크 슬롯을 추가하는 방식으로 접근한다(정확한 마크업/ID 명명은 Generate 단계에서 정한다).

### 화면 구성 (컴포넌트 재사용 방향)

- 상품 정보 확인 영역: product-detail의 `.product-price-box`/`.price-tiers-table` 패턴을 재사용해 상품명/판매자명/가격(+공구팀인 경우 참고용 구간표)을 표시한다.
- 공통 안내/에러 배너: `.form-alert`/`.form-alert--error`/`.form-alert--success` 재사용.
- 서버 응답 문자열(상품명/판매자명/에러 message)은 기존 선례와 동일하게 `textContent`로만 대입한다(XSS 방지, `innerHTML` 미사용).

## 설계 판단 근거 (확인 필요 시 참고)

- **"기존 팀 참가하기"도 결제가 필요한가?** — `docs/api/team.md`는 "신설" 응답 설명에만 "결제까지 완료해야 참가가 확정된다"는 문구가 있고 "참가"(`join`) 쪽엔 이 언급이 없어 API 문서만으로는 애매했다. 하지만 백엔드 코드(`PaymentService.requireRoomOrAlreadyJoined`)의 주석 — "팀 참가는 team/join·team/create에서 이미 완결된다 — 결제는 그 결과를 다시 검증만 한다. 아직 참가하지 않은 멤버가 이미 정원이 찬 팀으로 결제를 시도하는 경합만 방어적으로 막는다" — 은 참가(`join`)로 이미 `TeamParticipation`이 생성된 멤버가 **그 다음에 결제를 호출하는 흐름**을 전제로 짜여 있다. 즉 신설·참가 모두 "먼저 팀원이 되고, 그다음 결제한다"는 동일한 2단계 흐름이며, `team.md`의 문서 공백은 누락으로 판단한다. 이 근거로 이번 계획은 참가/신설 모두 결제 링크를 노출하는 것으로 확정했다. **다르게 판단해야 한다면(예: 참가는 결제 링크를 노출하지 않아야 한다면) 승인 전에 알려달라.**
- **"결제창 물품 삭제"의 의미** — 백엔드에 장바구니/보류 중 결제 개념이 없다. `POST /api/payments`는 호출 즉시 `PAID` 상태의 결제 레코드를 만들고(별도 "확정" 단계 없음), 결제 전 단계에서는 삭제할 서버 자원 자체가 없다. 그래서 "결제창 물품 삭제"는 "다중 물품 중 하나 삭제"가 아니라 **"결제를 진행하지 않고 이 화면에서 나가기(취소)"**로 해석해, API 호출 없이 상품 상세 페이지로 돌아가는 것으로 설계했다. 이 해석이 의도와 다르면 알려달라.

## 태스크

- [x] `docs/api/payment.md`에 빠져 있던 `FORBIDDEN`(403) 에러 코드 보강 (Plan 단계에서 완료)
- [ ] `checkout.html` 마크업 작성 (헤더/푸터 include, 잘못된 접근/로딩/에러 상태, 상품 정보 확인 영역, 결제하기/삭제(취소) 버튼, 결제 완료 요약 영역, 공통 안내 배너)
- [ ] `js/checkout.js` 작성 — 쿼리 파라미터 파싱·검증, `GET /api/products/{productId}` 호출·렌더링, "결제하기"(`POST /api/payments`) 호출 및 성공/실패(코드별) 처리, "삭제(취소)" 버튼 처리(이동만)
- [ ] 필요한 경우 `css/components.css`에 checkout 전용 보조 스타일 추가 (`[hidden]` 보정 규칙 포함, 정확한 클래스 구성은 Generate 단계에서 결정)
- [ ] `js/product.js` 수정: `handleBuyAlone`을 checkout으로 이동시키도록 변경, `handleCreateTeam`/`handleJoin` 성공 배너에 결제 이동 링크 추가
- [ ] `product.html` 수정: 안내 배너 영역에 결제 이동 링크용 슬롯 추가

## 평가(통과) 기준

`./gradlew bootRun` 후 브라우저로 아래를 확인한다.

- 상품 상세 페이지에서 "혼자 구매하기" 클릭 시 `checkout.html?productId={id}`로 이동하고, 상품명/정가가 정상 렌더링된다.
- 구매자 계정으로 로그인한 상태에서 혼자구매 "결제하기" 클릭 시 결제가 생성되고(`201`), 응답의 상품명/금액/상태가 결제 완료 요약에 반영된다.
- 신규 팀 신설 성공 후 배너의 결제 이동 링크를 클릭하면 `checkout.html?productId={id}&teamId={새 teamId}`로 이동하고, 공구팀 결제 안내(가격 확정은 결제 시 서버가 처리)가 뜬다.
- 기존 팀 참가 성공 후 배너의 결제 이동 링크를 클릭해도 동일하게 이동·안내된다.
- 공구팀 결제 "결제하기" 클릭 시 결제가 생성되고, 응답 `amount`가 해당 팀의 현재 인원 기준 가격구간과 일치한다(수동 확인).
- 비로그인 상태에서 "결제하기" 클릭 시 401 처리(로그인 필요 안내 + 로그인 페이지 링크)가 뜨고 페이지가 깨지지 않는다.
- 판매자 계정으로 로그인한 상태에서 "결제하기" 클릭 시 403(`FORBIDDEN`) 안내가 뜬다.
- 정원이 찬 팀으로 결제 시도 시(수동 재현 가능하면) `409 TEAM_FULL` 안내가 뜬다.
- 존재하지 않는 상품/팀 ID로 접속·결제 시도 시 크래시 없이 안내가 뜬다.
- `productId`가 없거나 형식이 잘못된 경우, `teamId` 형식이 잘못된 경우 모두 API 호출 없이 "잘못된 접근" 안내가 뜬다.
- "삭제(취소)" 버튼 클릭 시 API 호출 없이 상품 상세 페이지로 돌아간다.
- (코드 리뷰) 상품명/판매자명/서버 에러 message 등 신뢰할 수 없는 문자열이 `textContent`로만 DOM에 대입되어 있다(`innerHTML` 미사용).

## 리스크 / 전제

- **결제 취소/환불 API 없음**: `POST /api/payments`는 즉시 `PAID` 확정이며 별도 취소 API가 없다. 결제 완료 후 사용자가 취소하고 싶어도 이번 범위에서 제공할 방법이 없다(팀 미성사 시 자동 환불만 `docs/policy/refund-trigger.md`의 스케줄러가 처리하며, 이는 프론트 개입 없이 백엔드가 이미 처리하는 부분).
- **공구팀 결제 사전 검증 불가**: `teamId`로 특정 팀의 현재 상태(`RECRUITING`/`SUCCESS`/`FAILED`, `currentCount`)를 직접 조회하는 API가 없다(`GET /api/products/{productId}/teams`는 `RECRUITING`만 반환하는 목록 API뿐). 팀이 결제 시점에 이미 `SUCCESS`/`FAILED`로 바뀌어 있으면 이 페이지는 사전에 알 방법이 없고, `POST /api/payments` 응답(`TEAM_NOT_FOUND`/`TEAM_FULL` 등)으로만 사후에 알게 된다.
- **결제 링크를 무시하는 경우**: 팀 신설/참가는 성공해도 결제로 이어지는 것은 링크 클릭(사용자 선택)에 의존한다. 링크를 무시하면 결제 미완료 상태로 팀원(신설자 포함)이 남을 수 있는데, 이 상태의 후속 처리(리더/참가자 결제 마감 정책 등)는 product-detail 단계부터 있던 기존 리스크와 동일하게 이번 범위 밖이다.
- **헤더 로그인 상태 미연동 · 로그인 복귀 경로 없음**: design-system/auth 단계부터의 기존 제약이 그대로 적용된다 — 401을 만나 로그인 페이지로 이동해도 로그인 후 checkout으로 자동 복귀하지 않는다.
- **쿼리 파라미터 이름 혼동 소지**: `product.html`은 상품 ID를 `id`로 받는데 `checkout.html`은 `productId`로 받는다(결제 API 필드명과 맞추기 위한 의도적 선택). 두 페이지를 오가는 링크를 만들 때 파라미터 이름을 헷갈리지 않도록 주의가 필요하다.
- **CSS `[hidden]` specificity 버그 재발 방지**: 새로 추가하는 요소가 `hidden` 속성 + 자체 `display` 선언 클래스를 함께 쓰면 보정 규칙이 필요하다(반복 확인된 패턴).
- `docs/dev/ongoing/`에 다른 진행 중 작업 없음을 확인했다(중복 없음).
