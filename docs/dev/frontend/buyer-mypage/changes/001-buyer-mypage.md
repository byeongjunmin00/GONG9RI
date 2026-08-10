# 구매자 마이페이지 (`/buyer/mypage`)

대상: frontend/buyer-mypage
담당: 전용운

## 배경 / 요구

`docs/WIREFRAME.md` "8. 구매자 마이페이지"를 만든다:
- **구매 목록**: 구매 완료 상품 카드/상품명/가격.
- **공구 참여 목록**:
  - **미성사 팀**: 남은 팀 유지 기간, 현재 팀 인원 상태.
  - **성사 팀**: 구매 목록과 동일한 주문 상태로 전환.

API는 `docs/api/mypage.md`에 이미 있다(구매자 전용, 전부 `UNAUTHORIZED`(401)/`FORBIDDEN`(403, 판매자 계정) 에러 포함):
- `GET /api/buyer/mypage/purchases` — 구매 완료 목록. 필드: `paymentId`/`productId`/`productName`/`amount`/`status`(`PAID`|`REFUNDED`)/`paidAt`.
- `GET /api/buyer/mypage/teams` — 본인이 참여한 팀 전체(성사/미성사 포함). 필드: `teamId`/`productId`/`productName`/`currentCount`/`maxParticipants`/`status`(`RECRUITING`|`SUCCESS`|`FAILED`)/`deadline`/`joinedAt`.

## 코드 확인으로 파악한 사실

- **SecurityConfig는 이미 서브디렉토리 html을 허용한다**: `SecurityConfig.java`의 정적 리소스 permitAll 매처가 `.requestMatchers("/", "/*.html", "/**/*.html", "/css/**", "/js/**", "/partials/**").permitAll()`로 이미 일반화돼 있다(frontend/seller-product-new 작업에서 처리 완료, 코드로 재확인). 이번 작업에서 `SecurityConfig`를 또 건드릴 필요는 없을 것으로 보인다.
- **`purchases`와 `teams`는 서로 다른 API이고 응답 필드도 다르다** — `purchases`는 결제(payment) 단위, `teams`는 팀(group_buy_team) 단위. `teams` 응답에는 `paymentId`/`amount`가 없고, `purchases` 응답에는 `status`(팀 성사 여부)나 `deadline`이 없다. 두 목록을 코드 레벨로 병합(같은 `paymentId`로 매칭)할 방법이 API상 없다 — "성사 팀은 구매 목록과 동일한 주문 상태로 전환"은 **데이터 병합이 아니라 화면 표시(성사 팀 항목을 구매 확정 상태로 보이게 스타일링/배치)**로 해석한다.
- `js/product.js`에 이미 `statusToBadgeClass`/`statusToLabel`(`RECRUITING`/`SUCCESS`/`FAILED` → `.badge-recruiting`/`.badge-success`/`.badge-failed` + 한글 라벨) 로직이 있다 — 공구 참여 목록에 재사용 가능한 선례.
- `partials/header.html`에는 아직 마이페이지 진입 링크가 전혀 없다.
- `docs/dev/ongoing/`에 이 작업 외 다른 진행 중 작업 없음(README만 존재, 중복 없음).
- `docs/policy/refund-trigger.md`: 마감(`deadline`) 지난 `RECRUITING` 팀은 스케줄러가 주기적으로(기본 1분마다) `FAILED` 전환 + 관련 결제 `REFUNDED` 일괄 처리한다. 즉 `FAILED` 상태로 보이는 팀은 이미 환불 처리가 끝났다고 봐도 된다(클라이언트가 별도로 환불 여부를 재확인할 API는 없음).
- `docs/policy/team-success-criteria.md`: 정원 도달 즉시 실시간으로 `RECRUITING→SUCCESS` 전환(배치 지연 없음) — `teams` 응답의 `status`를 그대로 신뢰하면 된다.

## 설계

### 산출물 / 라우팅

- 신규 정적 페이지: `src/main/resources/static/buyer/mypage.html` + `js/buyer-mypage.js`(가칭, 정확한 파일명은 Generate 단계에서 확정). `seller/products/new.html`과 동일하게 서브디렉토리 페이지이므로 CSS/JS/partial 참조는 절대경로 원칙을 따른다.
- `partials/header.html`에 "구매자 마이페이지"(`/buyer/mypage.html`) 진입 링크를 추가한다. 위치/마크업은 Generate 단계에서 정하되, 로그인 여부/역할과 무관하게 항상 노출한다(기존 원칙 유지, 판매자 계정으로 열어도 페이지는 열리고 API 호출에서 `403`으로 사후 판정).

### 데이터 흐름

1. `buyer/mypage.html` 로드 시 `GET /api/buyer/mypage/purchases`, `GET /api/buyer/mypage/teams` 두 API를 호출. 로그인 상태 사전 확인 없음 — 401/403이면 안내만 표시.
2. **구매 목록** 섹션: `purchases` 응답을 그대로 카드/행으로 렌더링(상품명/금액/상태/결제일시). `status`가 `REFUNDED`인 항목은 "환불됨"으로 구분 표시(팀 미성사 자동 환불 결과일 수 있음 — `refund-trigger.md` 참고).
3. **공구 참여 목록** 섹션: `teams` 응답을 `status`로 나눠 표시.
   - `RECRUITING`(미성사/모집 중): "남은 팀 유지 기간"은 `deadline`과 현재 시각의 차이로 계산해 표시. "현재 팀 인원 상태"는 `currentCount`/`maxParticipants`로 표시.
   - `SUCCESS`(성사): 구매 목록과 동일한 시각적 취급(같은 카드 스타일/톤, 또는 구매 목록 섹션과 인접 배치)으로 "성사 완료" 상태를 표시한다. 단, 실제 결제 상세(금액 등)는 `teams` 응답에 없으므로 필요하면 `productId`로 `purchases` 목록에서 대응 항목을 찾아 함께 보여줄 수 있다(둘 다 로드된 이후 클라이언트에서 매칭, 매칭 실패해도 에러 아님).
   - `FAILED`(미성사 확정): 마감이 지나 실패 처리된 팀 — "미성사(환불 처리됨)" 등으로 표시.
4. 사용자 입력 기반 문자열 없음(전부 서버 데이터) — 상품명 등은 `textContent`로만 대입(XSS 방지, 기존 원칙 유지).

## 확인 필요

1. **"성사 팀 = 구매 목록과 동일 취급"의 구현 방향**: 위 "코드 확인으로 파악한 사실"에서 설명했듯 API 구조상 완전한 데이터 병합은 불가능해 **화면 표시 수준의 동일 취급**(같은 스타일/문구, `productId` 기준 느슨한 매칭 시도)으로 해석했다. 이 해석에 이견이 있으면 승인 전에 알려달라.
2. **헤더 "구매자 마이페이지" 링크의 정확한 위치**는 Generate 단계에서 정한다 — 특별한 선호가 있으면 알려달라.

## 태스크

- [ ] (승인 후) `src/main/resources/static/buyer/mypage.html` 마크업 작성 (헤더/푸터 include, 구매 목록 섹션, 공구 참여 목록 섹션(상태별 구분))
- [ ] `js/buyer-mypage.js` 작성 — 두 API 호출·렌더링, `RECRUITING` 잔여기간 계산, 상태별 표시 분기, 401/403/기타 에러 처리
- [ ] `css/components.css`에 필요한 신규 규칙 추가(공구 상태 뱃지는 기존 `.badge-*` 재사용, `[hidden]` 보정 규칙 필요 시 함께 추가)
- [ ] `partials/header.html`에 "구매자 마이페이지" 링크 추가

## 평가(통과) 기준

`./gradlew bootRun` 후 브라우저로 아래를 확인한다.

- 구매자 계정으로 로그인 후 `/buyer/mypage.html` 접속 시 본인의 구매 목록과 공구 참여 목록(성사/미성사 구분)이 정상 표시된다.
- 판매자 계정으로 로그인 후 접속 시 `403 FORBIDDEN` 안내가 뜨고 페이지가 깨지지 않는다.
- 비로그인 상태로 접속 시 `401 UNAUTHORIZED` 안내(로그인 필요 + 로그인 페이지 링크)가 뜬다(페이지 자체는 401 JSON 없이 정상 렌더링된다).
- `RECRUITING` 상태 팀 항목에 남은 유지 기간(마감까지)과 현재 인원(`currentCount`/`maxParticipants`)이 표시된다.
- `SUCCESS` 상태 팀 항목이 미성사 팀과 시각적으로 구분되어(구매 목록과 유사한 톤으로) 표시된다.
- `FAILED` 상태 팀 항목이 미성사(환불 처리됨)로 구분 표시된다.
- (코드 리뷰) 서버 데이터는 `textContent`로만 DOM에 대입(XSS 방지, `innerHTML` 미사용).

## 리스크 / 전제

- **팀 상태의 실시간 재계산 없음**: `RECRUITING`의 남은 기간은 페이지 로드 시점 기준 1회 계산이며, 페이지를 새로고침하지 않는 한 실시간 카운트다운/자동 갱신은 이번 범위에 포함하지 않는다(필요하면 후속 작업).
- **성사 팀의 결제 상세 매칭은 최선 노력(best-effort)**: `productId` 기준 매칭이라 한 상품에 구매 이력이 여러 번 있으면(혼자구매+공구 등) 어느 결제가 해당 팀 결제인지 API로는 구분할 수 없다(팀 응답에 `paymentId`가 없음). 이 경우 금액 등 결제 상세는 생략하고 팀 자체 정보(상품명/인원/성사 여부)만 표시한다.
- **헤더 링크 추가는 seller-mypage 작업과 같은 파일(`partials/header.html`)을 건드린다.** 두 작업(`frontend/seller-mypage`, `frontend/buyer-mypage`)을 순차적으로 진행한다면 문제 없으나, 병렬로 진행하면 편집 충돌 가능성이 있다.
