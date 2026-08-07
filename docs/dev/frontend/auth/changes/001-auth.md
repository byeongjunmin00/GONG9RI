# 로그인 페이지 + 회원가입 페이지 (`/login`, `/signup`)

대상: frontend/auth          <!-- 완료 시 docs/dev/frontend/auth/design.md(SSOT) 작성 + docs/dev/frontend/auth/changes/ 로 이동 -->
담당: 전용운

## 배경 / 요구

- 직전 작업(`docs/dev/frontend/main-page/design.md`)에서 메인 페이지(`/`)까지 완료됐다. `docs/WIREFRAME.md` 화면 흐름상 메인페이지 다음은 "2. 로그인 페이지"·"3. 회원가입 페이지"다.
- 사용자 지시: "다음 프론트 진행해" — 로그인/회원가입 두 페이지를 이번 작업 범위로 잡는다. 두 페이지는 폼 컴포넌트(`components.css`의 `.form-*`)를 공유하고, 로그인 실패 시 "회원가입 하러가기"·회원가입 성공 시 "로그인 하러가기"처럼 서로 강하게 연결돼 있어 메인페이지 때처럼 **하나의 작업 단위(하나의 계획 문서)**로 묶는다.
- `docs/api/auth.md`: `POST /api/auth/signup`, `POST /api/auth/login`이 이 작업이 쓰는 데이터 소스다. 둘 다 `SecurityConfig`에서 이미 `permitAll`(design-system 단계 확인 사항, `docs/dev/frontend/design-system/design.md` 참고)이라 비로그인 상태에서 호출 가능하다.

### 사전 확인 결과

- **`docs/dev/ongoing/`**: `README.md` 외 다른 진행 중 작업 없음 → 충돌 없음.
- **`docs/policy/`** 3건(`refund-trigger.md`, `team-success-criteria.md`, `caching.md`) 확인 — 전부 공구팀/환불/캐싱 관련이며, 회원가입·로그인(아이디 형식, 비밀번호 규칙 등)에 대한 정책 문서는 **없다**. 즉 클라이언트/서버 어느 쪽에도 문서화된 비밀번호·아이디 형식 규칙이 없다(서버 측 Bean Validation 규칙이 실제로 어떻게 걸려 있는지는 이번 Plan에서 코드를 규정하지 않으므로 확인하지 않았고, 서버가 `VALIDATION_FAILED`로 응답하는 모든 경우를 포괄적으로 처리하는 방향으로 설계한다).
- **`partials/header.html`(현재 상태 재확인)**: 사용자 지시문에 "현재 로그인/회원가입 버튼이 `href="#"` placeholder"라고 돼 있었으나, 실제 저장소를 다시 확인한 결과 **이미 `href="/login.html"`, `href="/signup.html"`로 돼 있다**(design-system 단계에서 미리 실제 목표 경로를 자리표시자 값으로 써둔 것 — `docs/logs/frontend/design-system/001-design-system.md` 참고). 따라서 "링크 값 자체"는 갱신할 필요가 없다. 다만 `header.html` 상단 주석은 "아직 만들어지지 않은 개별 페이지... 해당 페이지가 실제로 만들어지면 경로를 맞춰 갱신한다"고 적혀 있어, 로그인/회원가입 페이지가 실제로 생기는 이번 작업에서 그 주석 문구(더 이상 placeholder가 아니라는 사실)만 갱신 대상으로 잡는다. 네비게이션의 "판매 물품 등록" 링크(`/seller/products/new.html`)는 이번 범위 밖(해당 페이지 없음, 계속 placeholder로 둠).
- **`docs/api/auth.md`**: `signup`/`login`은 성공 시 DTO(둘 다 동일 형태: `memberId`/`username`/`name`/`role`)를 반환하고, 실패 시 `{code, message}` 단일 메시지만 준다(필드별 세부 오류 목록은 계약에 없다). `logout`은 있으나 이번 작업 범위에서 "로그인 상태 UI"를 만들지 않으므로 로그아웃 버튼/호출은 이번 범위에 포함하지 않는다(아래 "로그인 상태 헤더 연동" 항목에서 이유 설명).

## 설계

### 1. 기능 폴더를 하나로 묶는 근거

- `docs/dev-doc-guide.md`의 "개념(concept)" 기준: 로그인·회원가입은 둘 다 "인증(auth)"이라는 하나의 응집된 주제이고, 화면 간 이동(로그인 실패 시 안내 문구에 회원가입 링크, 회원가입 성공 후 로그인 페이지로 이동)이 서로 강하게 연결돼 있다. 메인페이지 작업 때 `index.html`+`js/main.js`를 "하나의 기능(main-page)"으로 묶었던 것과 동일한 기준으로, 이번에도 **`frontend/auth`라는 하나의 기능**으로 묶는다(`login.html`+`signup.html`+각 JS를 한 design.md가 서술).
- 대안(로그인/회원가입을 `frontend/login`, `frontend/signup` 두 기능으로 분리)도 가능하지만, 두 페이지가 API 계약(`docs/api/auth.md`)과 폼 컴포넌트를 100% 공유하고 독립적으로 릴리즈될 가능성이 낮아 분리 시 문서가 서로 중복 참조하게 된다고 판단했다. **이 판단에 이견이 있으면 승인 전에 알려달라.**

### 2. 파일 (무엇을)

- `src/main/resources/static/login.html` 신규 — 아이디/비밀번호 입력 폼 + 로그인 버튼 + 회원가입 페이지로 가는 링크. 기존 partial(`data-include="header|footer"`)과 `css/tokens.css`·`base.css`·`layout.css`·`components.css`, `js/include.js`·`js/api.js`를 그대로 재사용(신규 CSS 파일은 원칙적으로 없음, 폼 레이아웃 보강이 꼭 필요하면 `components.css`에 최소 추가는 Generate 재량).
- `src/main/resources/static/signup.html` 신규 — 아이디/비밀번호/이름/이메일 입력 폼 + 회원 유형(`구매자`/`판매자`) 선택 + 가입 버튼 + 로그인 페이지로 가는 링크.
- 각 페이지 전용 스크립트(파일 분리 여부는 Generate 재량, 예: `js/login.js`/`js/signup.js`) — 폼 제출 시 `Api.post`로 각 엔드포인트 호출, 성공/실패 처리.
- `partials/header.html` — 상단 주석만 갱신(로그인/회원가입 링크가 더 이상 placeholder가 아님을 반영). 링크 값(`href`) 자체는 변경 없음(이미 맞음, 위 "사전 확인 결과" 참고).

### 3. 제출 처리 방향 (어떻게)

- **로그인(`login.html`)**
  - 제출 시 `Api.post('/auth/login', { username, password })` 호출.
  - 성공: 메인 페이지(`/`)로 리다이렉트한다. (세션 쿠키는 응답 헤더로 브라우저가 자동 저장하므로 클라이언트가 별도로 다룰 게 없다.)
  - 실패:
    - `VALIDATION_FAILED`(400, 필드 누락) / `LOGIN_FAILED`(401, 아이디·비밀번호 불일치) 모두 **어느 입력값이 원인인지 API가 구분해서 알려주지 않으므로**, 특정 입력 필드의 `.form-error`가 아니라 **폼 전체에 대한 공통 에러 영역**에 서버가 준 `message`를 표시하는 방향으로 잡는다(정확한 배치/마크업은 Generate 몫).
    - 제출 전 클라이언트 측 필수값 체크(빈 값 제출 방지) 정도는 둘 수 있으나, 이는 UX 보조용이고 실제 검증 기준(SSOT)은 서버 응답이다(서버가 이미 `VALIDATION_FAILED`를 판단해 주므로 클라이언트가 서버 규칙을 추측해 재구현하지 않는다).
- **회원가입(`signup.html`)**
  - 제출 시 `Api.post('/auth/signup', { username, password, name, email, role })` 호출. `role`은 `BUYER`/`SELLER` 중 하나를 고르는 선택 UI(예: 라디오 버튼류, 정확한 마크업은 Generate 몫)로 받아 그 값 그대로 전달한다.
  - 성공: 곧바로 로그인되는 게 아니므로(가입 API는 세션을 만들지 않음, `docs/api/auth.md`에 로그인과 별개로 정의됨) **`login.html`로 리다이렉트**하고, 가입이 막 완료됐다는 안내를 로그인 페이지에서 보여주는 방향으로 잡는다(예: 쿼리스트링으로 상태를 전달하는 정도 — 정확한 방식은 Generate 몫).
  - 실패:
    - `DUPLICATE_USERNAME`(409)은 원인 필드(아이디)가 명확하므로 **아이디 입력 필드 아래 `.form-error`에 매핑**한다.
    - `VALIDATION_FAILED`(400)는 로그인과 마찬가지로 어느 필드가 원인인지 API가 구분해 주지 않으므로 **폼 전체 공통 에러 영역**에 표시한다.

### 4. 로그인 상태 헤더 연동 — 이번 범위에서 하지 않음

- `docs/api/auth.md`에 "현재 로그인한 사용자 조회" 엔드포인트가 없다(design-system 단계에서 이미 확인된 제약, `docs/dev/frontend/design-system/design.md` 참고). 즉 새로고침·다른 페이지 진입 시 "지금 로그인 상태인지"를 서버에 물어볼 방법이 없다.
- 로그인 성공 직후 같은 탭 안에서만 클라이언트가 "방금 로그인했다"는 사실을 알 수는 있지만(응답을 받은 그 순간), 그 상태를 새로고침 후에도 신뢰성 있게 재현할 방법이 없어 로그인 성공 직후 리다이렉트 대상인 메인 페이지에서조차 헤더를 "로그인됨" 상태로 정확히 표시할 수 없다.
- 따라서 이번 작업은 메인페이지·디자인시스템 단계와 동일하게 **헤더를 비로그인 고정 마크업 그대로 둔다**(헤더 상태 전환 로직 추가 없음, 로그아웃 버튼/호출 UI도 이번 범위에 포함하지 않음). **→ 이 판단에 동의하는지 승인 시 확인 부탁한다(사용자 지시문에서도 애매하면 확인하라고 명시된 항목).**

## 태스크

- [ ] `static/login.html` — 헤더/푸터 include + 아이디/비밀번호 폼(`.form-group`/`.form-label`/`.form-input`) + 로그인 버튼 + 회원가입 링크 + 공통 에러 영역
- [ ] `static/signup.html` — 헤더/푸터 include + 아이디/비밀번호/이름/이메일 폼 + 회원 유형(구매자/판매자) 선택 UI + 가입 버튼 + 로그인 링크 + (아이디 필드 에러 영역 + 공통 에러 영역)
- [ ] 로그인/회원가입 제출 스크립트 — `Api.post` 호출, 성공 시 리다이렉트(로그인→`/`, 회원가입→`login.html`), 실패 시 에러 코드별 표시 위치 매핑(위 "제출 처리 방향" 반영)
- [ ] `partials/header.html` — 상단 주석 갱신(로그인/회원가입은 이제 실제 페이지가 있음을 반영, `href` 값 자체는 변경 없음)
- [ ] (필요 시) `components.css`에 이번 두 폼 페이지에서만 필요한 최소 보강(예: 공통 에러 영역, 회원 유형 선택 UI 스타일) — 기존 토큰 체계 안에서, Generate 재량

## 평가(통과) 기준

- `./gradlew bootRun` 후 브라우저로 확인:
  - `http://localhost:8080/login.html`, `.../signup.html` 접속 시 헤더/푸터가 정상 표시되는가.
  - **회원가입 성공**: 신규 아이디로 필수값을 채워 제출 → `201` 응답 후 로그인 페이지로 이동하는가.
  - **회원가입 실패 - 중복 아이디**: 방금 가입한 아이디로 다시 가입 시도 → `409 DUPLICATE_USERNAME` 에러가 아이디 필드 쪽에 표시되는가.
  - **회원가입 실패 - 필수값 누락**: 일부 필드를 비운 채 제출 → `400 VALIDATION_FAILED` 에러가 표시되는가(클라이언트에서 막든 서버 응답으로 막든, 사용자에게 명확히 안내되는가).
  - **로그인 성공**: 방금 가입한 계정으로 로그인 → 메인 페이지로 리다이렉트되고, 개발자도구 Network 탭에서 로그인 응답 헤더에 `Set-Cookie: JSESSIONID=...`가 발급되는가(애플리케이션 탭에서 쿠키 확인도 가능).
  - **로그인 실패 - 비밀번호 불일치**: 존재하는 아이디에 잘못된 비밀번호 → `401 LOGIN_FAILED` 에러가 표시되는가.
  - **로그인 실패 - 필수값 누락**: 아이디/비밀번호 중 하나를 비운 채 제출 → `400 VALIDATION_FAILED` 처리가 되는가.
  - 개발자도구 콘솔에 처리되지 않은 JS 에러가 없는가.
  - 헤더의 로그인/회원가입 버튼이 실제로 `login.html`/`signup.html`로 이동하는가(기존에 이미 값이 맞았던 것 재확인).
- 자바 도메인 로직 변경은 예상되지 않으므로 `./gradlew test` 계산적 평가는 해당 사항이 제한적이다(변경이 생기면 확인).

## 리스크 / 전제

- **평문 비밀번호 전송**: 로컬 개발 환경은 HTTPS가 아니므로 비밀번호가 평문으로 전송된다(개발 환경 전제, 배포 시 HTTPS 적용은 이 작업 범위 밖).
- **XSS**: 서버가 돌려주는 에러 `message`, 그리고 로그인/회원가입 폼에 입력된 값을 화면에 다시 보여줄 경우(예: 아이디 유지) 사용자 입력·서버 응답 문자열을 `innerHTML`이 아닌 방식으로 안전하게 렌더링해야 한다(main-page 때 `textContent` 사용 전례와 동일한 원칙, 구체 구현은 Generate).
- **클라이언트/서버 검증 중복**: 클라이언트에 최소한의 필수값 체크를 두더라도, 실제 판정 기준(SSOT)은 서버 응답이다. 문서화된 비밀번호 규칙(정책)이 없으므로 클라이언트가 서버 규칙을 추측해서 재구현하지 않는다(위 "사전 확인 결과" 참고).
- **비밀번호 규칙/아이디 형식 정책 부재**: `docs/policy/`에 회원가입 관련 정책 문서가 없다는 사실을 그대로 기록한다(추가 정책이 필요하면 별도 논의).
- **로그인 상태 헤더 미연동**: 위 설계 4항 판단에 따라 이번 작업 완료 후에도 헤더는 로그인 여부와 무관하게 항상 "로그인/회원가입" 버튼을 보여준다(로그인한 사용자가 메인페이지로 돌아가도 헤더는 비로그인 상태처럼 보인다) — 이는 새 버그가 아니라 "현재 사용자 조회" API 부재로 인한 알려진 제약이다.
- `js/api.js`, `js/include.js`, `css/*`는 기존 산출물을 그대로 재사용하는 것을 전제로 하며, 이번 작업에서 그 파일들의 기존 동작(공통 응답 파싱 규칙 등)을 변경하지 않는다.

## 문서 산출물

- 이 계획 문서: `docs/dev/ongoing/frontend-auth.md`
- 신규 API/DB 명세 없음(기존 `docs/api/auth.md` 그대로 사용, 변경 없음).
- Evaluate 통과 시 `docs/dev/frontend/auth/design.md`(SSOT) 신규 작성 + 이 ongoing 문서를 `docs/dev/frontend/auth/changes/001-auth.md`로 채번 이동.
