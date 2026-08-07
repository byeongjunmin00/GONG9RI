# 메인 페이지 (`/`)

대상: frontend/main-page          <!-- 완료 시 docs/dev/frontend/main-page/changes/ 로 이동 -->
담당: 전용운

## 배경 / 요구

- 직전 작업(`docs/dev/frontend/design-system/design.md`)에서 공통 레이아웃 + 디자인 시스템(토큰 CSS, 헤더/푸터 partial, `js/include.js`, `js/api.js` 뼈대, `design-system.html` 쇼케이스)까지 완료했고, `index.html`(메인 페이지)은 그 작업 범위에서 의도적으로 비워뒀다.
- 사용자 지시: "일단 풀업 받아오고 마저 메인 페이지 작업해" — 그 산출물 위에서 첫 개별 기능 페이지인 메인 페이지(`/`)를 구현한다.
- `docs/WIREFRAME.md` "1. 메인 페이지" 요구사항: 상품 카드 목록(이미지/상품명/기본가/베스트 공구가) + 판매 물품 등록하기/로그인/회원가입 버튼.
- `docs/api/product.md`의 `GET /api/products`(상품 목록 조회)가 이 페이지가 쓸 데이터 소스다. `SecurityConfig`에서 이미 `permitAll`이라 비로그인 상태에서도 호출 가능하다(design-system 작업 때 확인됨).

### 사전 확인 결과

- **`docs/dev/ongoing/`**: `README.md` 외 다른 진행 중 작업 없음 → 충돌 없음.
- **`docs/policy/`** 3건(`refund-trigger.md`, `team-success-criteria.md`, `caching.md`) 확인 — 이번 작업(메인 페이지 상품 목록 표시)과 직접 관련된 정책 없음. "베스트 공구 가격" 표시 방식에 대한 별도 정책 문서는 없다(참고만 하고 없다는 사실을 기록). `caching.md`는 상품 목록 캐싱을 다루지만 "고도화 단계 작업, MVP에서 구현하지 않는다"고 명시돼 있어 지금 적용 대상이 아니다.
- **`docs/api/product.md`**: `GET /api/products` 응답 필드는 `productId`, `name`, `basePrice`, `bestPrice`, `maxParticipants`, `sellerName`, `createdAt` + 페이지네이션(`page`/`size`/`totalElements`)뿐이다. **이미지 URL 필드와 공구 상태(`RECRUITING`/`SUCCESS`/`FAILED`) 필드는 이 응답에 없다.** (설계 3항에서 스코프 경계로 반영)

## 설계

### 1. 파일 (무엇을)

- `src/main/resources/static/index.html` 신규 — 메인 페이지 마크업. 기존 partial(`data-include="header|footer"`)과 `css/tokens.css`·`base.css`·`layout.css`·`components.css`, `js/include.js`·`js/api.js`를 그대로 재사용한다(신규 CSS 파일 없음).
- `src/main/resources/static/js/main.js`(가제) 신규 — 메인 페이지 전용 스크립트. `window.Api.get`으로 상품 목록을 가져와 카드 그리드로 렌더링하는 로직을 담는다.
- 로딩/빈 상태 표시에 최소한의 스타일 보강이 필요하면 기존 `components.css`/`base.css`에 추가할 수 있다(기존 토큰 체계 안에서, 필수는 아님 — Generate 재량).

### 2. 데이터 연동 방향

- 페이지 로드 시 `window.Api.get('/products')` 호출(쿼리 파라미터 없이 서버 기본값 `page=0`/`size=20` 사용 — 페이지네이션 UI는 이번 범위에서 만들지 않음, 3항 참고).
- 응답 `content` 배열의 각 항목을 디자인 시스템 쇼케이스(`design-system.html`)에서 이미 쓰인 `.card`/`.card-image`/`.card-body`/`.card-seller`/`.card-title`/`.card-price-row`/`.card-price-base`/`.card-price-best`/`.card-price-label` 구조에 매핑한다:
  - `name` → 카드 타이틀
  - `basePrice` → 기본가(취소선 스타일, `card-price-base`)
  - `bestPrice` → 베스트 공구가(강조 스타일, `card-price-best`)
  - `sellerName` → 판매자명(`card-seller`)
  - `maxParticipants` → 가격 라벨 문구에 활용(예: "N인 모이면 1인당 최저가" 류의 안내) — 정확한 문구 문자열은 Generate가 정한다.
- 상태 처리 방향(정확한 마크업/문구는 Generate 몫):
  - **로딩 중**: 카드 그리드 영역에 로딩 상태를 표시.
  - **성공 + 목록 있음**: 카드들을 렌더링.
  - **성공 + 목록 없음**(`content: []`): 에러가 아닌 빈 상태 안내를 표시.
  - **실패**(네트워크/서버 오류): 에러 상태 안내를 표시. `Api.get`이 던지는 `Error`(`code`/`message`)를 활용.

### 3. 이번 범위에서 하지 않는 것 (데이터 계약·스코프 경계)

- **이미지**: 응답에 이미지 URL 필드가 없어 `card-image` 영역은 실제 상품 사진 없이 기존 placeholder 그라디언트 배경만 노출한다(쇼케이스와 동일한 fallback). 실제 이미지는 상품 API에 필드가 추가돼야 가능하며 이번 계획 범위 밖이다.
- **공구 상태 뱃지(모집중/성사/미성사)**: 이 뱃지는 `group_buy_team` 상태에 연동되는데 상품 목록 API는 상품 단위라 그 정보를 포함하지 않는다. 메인 페이지 카드에는 상태 뱃지를 붙이지 않는다(상세 페이지에서 팀 조회 API와 연동할 몫).
- **페이지네이션 UI**: 전체 페이지 번호 UI(1/2/3...)는 만들지 않지만, 사용자 승인에 따라 **간단한 "더 보기" 버튼**은 이번 범위에 포함한다 — 클릭 시 `page`를 1 증가시켜 `Api.get('/products?page=N')`을 다시 호출하고, 받아온 `content`를 기존 카드 목록 뒤에 추가(append)한다. `totalElements` 대비 이미 모두 불러왔으면 버튼을 숨기거나 비활성화한다(정확한 판단 조건·마크업은 Generate가 정한다).
- **상품 상세 페이지 이동 링크**: `/products/{id}` 상세 페이지가 아직 없다. 기존 `partials/footer.html`의 `href="#"` placeholder 관례를 따라, 카드에 `href="#"` 자리표시자 링크만 두고 실제 이동은 만들지 않는다(주석으로 "상세 페이지 작업 시 갱신" 명시). **→ 사용자 승인 완료.**
- **로그인 상태 연동**: design-system 단계와 동일하게 헤더는 비로그인 고정 마크업을 그대로 재사용한다. 메인 페이지 자체에 별도 로그인 상태 분기 로직을 추가하지 않는다.
- **판매 물품 등록/로그인/회원가입 버튼**: 이미 공통 헤더(`partials/header.html`)에 있다(등록 nav 링크, 로그인/회원가입 버튼). 와이어프레임이 언급하는 이 버튼들은 헤더 재사용으로 충족된다고 보고 메인 페이지 본문에서 중복 배치하지 않는다.

## 태스크

- [ ] `static/index.html` — 헤더/푸터 include + 상품 카드 그리드 컨테이너 + "더 보기" 버튼을 포함한 메인 페이지 마크업
- [ ] `static/js/main.js` — `Api.get('/products')` 호출 → 카드 렌더링, 로딩/에러/빈 목록 상태 처리, "더 보기" 클릭 시 다음 page를 불러와 append, 더 불러올 항목이 없으면 버튼 숨김/비활성화
- [ ] (필요 시) `components.css`/`base.css`에 로딩·빈 상태·"더 보기" 버튼 표시용 최소 스타일 보강

## 평가(통과) 기준

- `./gradlew bootRun` 후 `http://localhost:8080/` 접속:
  - 헤더/푸터가 partial 삽입 방식으로 정상 표시되는가
  - **DB에 상품 데이터가 있는 경우**: 카드 목록이 API 응답 기준으로 렌더링되고, 각 카드에 상품명/기본가/베스트 공구가/판매자명이 정확히 매핑되어 표시되는가
  - **DB에 상품 데이터가 없는 경우**: 빈 상태 안내가 정상 표시되는가(에러로 오인되지 않는 명확한 문구)
  - 개발자도구 Network 탭에서 `GET /api/products` 호출이 비로그인 상태에서도 200으로 성공하는가
  - 개발자도구 콘솔에 처리되지 않은 JS 에러가 없는가
  - 모바일 뷰(창 좁힘)에서 카드 그리드가 `layout.css`의 기존 반응형 규칙(`.grid-cards`)대로 열 수가 자연스럽게 줄어드는가
  - "더 보기" 버튼 클릭 시 다음 페이지 상품이 기존 카드 뒤에 이어붙여지는가, 더 불러올 항목이 없을 때 버튼이 숨겨지거나 비활성화되는가(상품이 20개 미만이면 이 케이스는 첫 페이지만으로도 확인 가능)
- 자바 도메인 로직 변경은 예상되지 않으므로 `./gradlew test` 계산적 평가는 해당 사항이 제한적이다(변경이 생기면 확인).

## 리스크 / 전제

- 로컬 DB에 테스트 상품 데이터가 없을 수 있다 — 이 경우 카드 렌더링 확인은 빈 상태 UI 확인으로 대체된다(평가 기준에 반영됨).
- `GET /api/products`는 `SecurityConfig`에서 이미 `permitAll`이므로 비로그인 상태에서도 정상 호출될 것으로 전제한다. 별도 인증/CORS 이슈는 없을 것으로 전제.
- API 응답의 `name`/`sellerName` 등 사용자(판매자) 입력 기반 문자열을 DOM에 표시할 때 XSS 방지가 필요하다 — 구체 처리 방식은 Generate가 정한다.
- 상품 목록 API에 이미지/공구 상태 필드가 없는 것은 이번 프론트 작업으로 해결할 수 없는 백엔드 계약상 제약이다(설계 3항 참고, 향후 API 확장이 필요하면 별도 계획).
- `js/api.js`, `js/include.js`, `css/*`는 design-system 단계 산출물을 그대로 재사용하는 것을 전제로 하며, 이번 작업에서 그 파일들의 기존 동작을 변경하지 않는다(변경이 필요해지면 알리고 범위를 재논의한다).

## 문서 산출물

- 이 계획 문서: `docs/dev/ongoing/frontend-main-page.md`
- 신규 API/DB 명세 없음(기존 `docs/api/product.md` 그대로 사용).
- Evaluate 통과 시 `docs/dev/frontend/main-page/design.md`(SSOT) 신규 작성 + 이 ongoing 문서를 `docs/dev/frontend/main-page/changes/001-main-page.md`로 채번 이동.
