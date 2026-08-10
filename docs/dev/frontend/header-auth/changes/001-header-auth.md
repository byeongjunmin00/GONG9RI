# 헤더 로그인 상태 연동

대상: `auth/me`(백엔드, 신규) + `frontend/header-auth`(프론트, 신규) — 완료 시 두 개의 `changes/`로 각각 채번 이동
담당: 전용운

## 배경 / 요구

지금까지 완성된 프론트 8개 페이지(`docs/dev/frontend/{design-system,main-page,auth,product-detail,checkout,seller-product-new,seller-mypage,buyer-mypage}/design.md`)가 공통으로 안고 있던 제약: **"현재 로그인한 사용자 조회" API가 없어 헤더가 로그인 여부·역할과 무관하게 항상 비로그인 고정 마크업(로그인/회원가입 버튼)만 보여준다.** 이 제약은 `docs/dev/frontend/design-system/design.md`(35행)부터 시작해 `frontend/auth`, `main-page`, `product-detail`, `checkout` 등 이후 모든 design.md에 반복해서 "범위 밖"으로 명시돼 있다.

사용자 요청: "헤더 로그인 상태 연동부터 하고 그 다음에 다듬으면 될거같아" — 헤더가 실제 로그인 상태(로그인 여부 + 역할 `BUYER`/`SELLER`)에 따라 다르게 보이도록 만든다. 이번 작업으로 위 제약을 해소한다.

## 코드 확인으로 파악한 사실

- `AuthController`에는 `signup`/`login`/`logout`만 있고 "현재 사용자 조회" 엔드포인트가 없다. `login()`이 이미 `MemberResponse.from(principal.getMember())`(`memberId`/`username`/`name`/`role`)를 반환하는 패턴이 있어 그대로 재사용 가능 — 신규 DTO·신규 `ErrorCode` 불필요.
- `SecurityConfig`는 `POST /api/auth/{signup,login}`과 정적 리소스만 `permitAll`이고 나머지는 `anyRequest().authenticated()`다. 신규 엔드포인트를 이 매처들에 추가하지 않으면 자동으로 `anyRequest().authenticated()`에 걸려 미인증 요청은 `ApiAuthenticationEntryPoint`가 기존과 동일한 형식(`{success:false, code:"UNAUTHORIZED", message:...}`, 401)으로 응답한다 — `docs/dev/auth/logout/design.md`의 `logout`과 동일한 인가 패턴.
- `js/api.js`(`window.Api.get/post/...`)는 실패 시(`success:false` 또는 non-2xx) `code`/`message`/`status`를 담은 `Error`를 throw한다. 헤더 스크립트가 `Api.get('/auth/me')` 호출 후 실패를 `catch`해 "비로그인"으로 처리할 수 있다(신규 fetch 로직 불필요, 기존 유틸 재사용).
- `js/include.js`는 `data-include` 요소들을 fetch로 가져와 삽입하고, 완료 시점을 `Promise.all`(`includeAll()`)로 이미 추적하고 있다 — 다만 현재는 이 완료 시점을 외부에 알리는 방법(이벤트 등)이 없다.
- `partials/header.html`은 nav 링크(메인/판매 물품 등록/판매자 마이페이지/구매자 마이페이지)와 로그인/회원가입 버튼이 로그인 여부·역할과 무관하게 항상 노출되는 고정 마크업이다.
- **정적 페이지 전수 확인**: 헤더 partial(`data-include="header"`)을 쓰는 페이지는 `index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html` 총 10개다. 신규 헤더 스크립트를 붙이려면 이 10개 파일 전부에 `<script>` 태그 한 줄씩 추가가 필요하다(영향 범위가 넓다는 리스크).
- **기존 원칙과의 충돌 소지**: 지금까지 모든 페이지가 "역할과 무관하게 nav 링크는 항상 노출, 결과는 서버 401/403으로 사후 판정"이라는 원칙을 지켜왔다(`seller-mypage`/`buyer-mypage`/`seller-product-new`/`product-detail`/`checkout` design.md에 반복 명시). 헤더에서 역할별로 링크를 아예 숨기는 방향으로 가면 이 원칙과 배치될 수 있다 — 아래 "확인 필요" 참고.

## 설계

### 1. 백엔드 — `GET /api/auth/me` (신규, `docs/dev/auth/me`)

- 엔드포인트 계약은 `docs/api/auth.md`에 이미 작성함(이 Plan의 산출물). 응답은 `signup`/`login`과 동일한 `MemberResponse` 재사용, 신규 DTO 없음.
- 인가: `permitAll` 목록에 추가하지 않는다 — `anyRequest().authenticated()`에 자연스럽게 걸려 미인증 시 401을 받는다(그래야 프론트가 "로그인 안 함"을 구분할 수 있다는 게 이번 작업의 전제).
- 영향 계층: `controller`(`AuthController`에 메서드 추가) 1곳. `dto`/`entity`/`repository`/`SecurityConfig` 변경 없음(기존 인가 규칙이 이미 이 엔드포인트를 커버함).
- 세션에서 인증된 사용자 정보를 꺼내는 방식은 `login()`이 이미 쓰는 것과 동일한 `MemberUserDetails`(Spring Security 인증 주체) 경로를 재사용한다 — 구체적으로 컨트롤러 메서드 파라미터를 어떻게 받을지(예: `@AuthenticationPrincipal` vs `SecurityContextHolder` 직접 조회)는 Generate가 정한다.

### 2. 프론트엔드 — 헤더 로그인 상태 표시 (신규, `docs/dev/frontend/header-auth`)

**흐름**: 페이지 로드 → `include.js`가 헤더 삽입 완료 → 신규 스크립트가 `GET /api/auth/me` 호출 → 성공(200)이면 로그인 상태로 헤더 갱신, 실패(401 등)면 비로그인 상태 그대로 유지.

- **include.js와의 연동 방식(결정)**: `include.js`가 모든 `data-include` 삽입이 끝난 시점(현재도 `Promise.all`로 추적 중)에 커스텀 이벤트를 `document`에 발행하도록 확장한다. 신규 헤더 스크립트는 이 이벤트를 구독해 실행 시점을 보장한다 — 헤더가 없는 페이지에서도 안전해야 하고, 이벤트는 삽입 완료 후 정확히 1회 발생해야 한다는 조건만 이 단계에서 정한다. 정확한 이벤트 이름·리스너 등록 방식(예: `window.Gong9riInclude`에 플래그 추가 여부)은 Generate가 정한다.
- **신규 정적 리소스**: 헤더 로그인 상태를 처리하는 스크립트 1개(예시 경로 `src/main/resources/static/js/header-auth.js`, 정확한 파일명은 Generate가 확정)와, 위 10개 페이지 전부에 이 스크립트를 로드하는 `<script>` 태그 추가.
- **`partials/header.html` 마크업 변경**: 로그인/회원가입 버튼 영역을 로그인 상태에 따라 토글할 수 있는 구조로 바꾼다(비로그인 시 로그인/회원가입 버튼, 로그인 시 사용자 이름 표시 + 로그아웃 버튼). 정확한 클래스/DOM 구조는 Generate가 정한다.
- **역할별 nav 강조(결정 필요 — 아래 "확인 필요" 참고, 기본 방향 제안)**: 제안하는 기본 방향은 "숨김"이 아니라 "강조"다 — nav 링크(판매 물품 등록/판매자 마이페이지/구매자 마이페이지)는 로그인 여부·역할과 무관하게 계속 전부 노출하되(기존 프로젝트 전역 원칙 유지, 서버 401/403 사후 판정 경계를 프론트로 옮기지 않음), 로그인한 역할에 해당하는 링크에만 시각적 강조(예: 활성 상태 스타일)를 준다. 이 방향에 동의하는지, 아니면 역할과 무관한 링크를 실제로 숨기길 원하는지는 승인 시 확인이 필요하다.
- **로그아웃 버튼 동작(결정 필요 — 아래 "확인 필요" 참고, 기본 방향 제안)**: `POST /api/auth/logout` 성공 후 **현재 페이지를 새로고침**하는 방향을 제안한다. 이러면 로그아웃 시점의 페이지가 무엇이든(마이페이지 등 인증 필요 페이지 포함) 그 페이지의 헤더와 기존 401 처리 로직이 그대로 다시 평가되어 별도의 신규 리다이렉트 로직이 필요 없다. 대안(항상 메인 `/`으로 이동)도 있다 — 승인 시 확인 필요.
- CSS: 로그인 상태 표시(이름/로그아웃 버튼/강조 스타일)에 필요한 규칙은 기존 `css/components.css`에 추가한다(신규 CSS 파일 없음). 정확한 클래스명은 Generate가 정한다.

## 영향 계층 요약

- 백엔드: `controller`(`AuthController`) — 신규 메서드 1개. `dto`/`SecurityConfig`/`entity`는 변경 없음(기존 자산 재사용).
- 프론트: `partials/header.html`(마크업 변경), `js/include.js`(삽입 완료 이벤트 발행 추가), 신규 JS 파일 1개(헤더 로그인 상태 처리), `css/components.css`(신규 스타일 규칙), 10개 정적 HTML 페이지(`<script>` 태그 추가) — `index.html`, `login.html`, `signup.html`, `product.html`, `checkout.html`, `seller/products/new.html`, `seller/products/edit.html`, `seller/mypage.html`, `buyer/mypage.html`, `design-system.html`.
- 문서: `docs/api/auth.md`(이번 Plan에서 이미 작성함), 완료 후 `docs/dev/auth/me/design.md`(신규)·`docs/dev/frontend/header-auth/design.md`(신규) 작성 + 이 ongoing 문서를 두 `changes/`로 각각 채번 이동.

## 태스크

- [ ] (승인 후) `AuthController`에 `GET /api/auth/me` 추가 (`MemberResponse` 재사용)
- [ ] (승인 후) `AuthControllerTest`에 케이스 추가: 로그인 후 `/api/auth/me` 200 + 필드 검증, 미로그인 시 401 + `UNAUTHORIZED`
- [ ] (승인 후) `include.js`에 삽입 완료 이벤트 발행 추가
- [ ] (승인 후) 신규 헤더 로그인 상태 스크립트 작성 (`GET /api/auth/me` 호출 → 상태별 헤더 렌더링)
- [ ] (승인 후) `partials/header.html` 마크업을 로그인 상태 토글 가능한 구조로 변경 + 상단 주석 갱신(더 이상 "헤더 로그인 상태 미연동"이 아님을 반영)
- [ ] (승인 후) 위 10개 페이지에 신규 스크립트 `<script>` 태그 추가
- [ ] (승인 후) `css/components.css`에 필요한 신규 규칙 추가
- [ ] (Evaluate 통과 후) `docs/dev/auth/me/design.md`, `docs/dev/frontend/header-auth/design.md` 작성 + 이 ongoing 문서를 각 `changes/001-*.md`로 채번 이동

## 평가(통과) 기준

- `./gradlew test` 전체 통과, 특히 `AuthControllerTest`의 신규 `/api/auth/me` 케이스(로그인 상태 200 + 필드 일치, 미로그인 401 + `UNAUTHORIZED`).
- `./gradlew bootRun` 기동 후 브라우저 실측:
  - 비로그인 상태로 아무 페이지나 접속 시 헤더가 기존과 동일하게 로그인/회원가입 버튼을 보여준다.
  - `BUYER`로 로그인 후 임의 페이지(새로고침 포함) 접속 시 헤더가 로그인 상태(이름 표시 + 로그아웃 버튼)로 바뀐다.
  - `SELLER`로 로그인 후 동일하게 확인, 역할별 강조(또는 확인 후 결정된 방향)가 적용된다.
  - 로그아웃 버튼 클릭 시 `POST /api/auth/logout` 호출 후 결정된 이동 방향대로 동작하고, 이후 헤더가 다시 비로그인 상태로 보인다.
  - 개발자도구 Network 탭에서 각 페이지 로드 시 `GET /api/auth/me` 호출이 정확히 실행되는지, 헤더 삽입이 끝나기 전에 실행돼 DOM 요소를 못 찾는 에러가 없는지 확인.

## 확인 필요 (승인 전 결정 필요)

1. **역할별 nav 링크 — 숨김 vs 강조**: 지금까지 8개 페이지 design.md에 반복 문서화된 "역할 무관 항상 노출, 서버 401/403 사후 판정" 원칙을 유지하고 로그인한 역할에 해당하는 링크만 시각적으로 강조할지, 아니면 이번에 원칙을 바꿔 로그인하지 않은 역할의 링크(예: `BUYER` 로그인 시 "판매 물품 등록"/"판매자 마이페이지", `SELLER` 로그인 시 "구매자 마이페이지")를 헤더에서 아예 숨길지. 위 설계에는 "강조" 방향을 기본 제안으로 적어 뒀다.
2. **로그아웃 후 이동 대상**: 현재 페이지 새로고침(제안) vs 항상 메인(`/`)으로 이동.
3. **비로그인 상태에서 nav 링크 클릭 시 동작 변경 여부**: 위 1번에서 "숨김"을 선택하면, 비로그인 상태에서 "판매 물품 등록" 등 링크가 아예 안 보이므로 지금까지의 "클릭은 되지만 API가 401/403으로 사후 판정" 흐름 자체가 일부 무의미해진다(그 링크로 갈 수단이 없어짐). 1번 답변에 따라 이 문서의 세부 태스크가 달라질 수 있다.
