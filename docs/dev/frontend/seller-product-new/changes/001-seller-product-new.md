# 판매 물품 등록 페이지 (`/seller/products/new`)

대상: frontend/seller-product-new
담당: 전용운

## 배경 / 요구

`docs/WIREFRAME.md` "6. 판매 물품 등록 페이지"를 만든다: 상품 카드 이미지, 상품명, 가격, 베스트 공구 가격(2인~N인 지정), 등록하기/취소하기 버튼. `partials/header.html`의 "판매 물품 등록" 링크(`href="/seller/products/new.html"`)가 이미 이 페이지를 가리키는 placeholder로 지정돼 있다(주석: "아직 해당 페이지가 없는 자리표시자") — 이번 작업으로 이 주석을 갱신한다.

API는 `POST /api/products`(`docs/api/product.md`)가 이미 있다: body `name`/`description`/`basePrice`/`maxParticipants`/`priceTiers`(배열, 각 `minCount`/`price`, 최소 1개, `@NotEmpty`). 응답 `201`. 에러: `VALIDATION_FAILED`(400)/`FORBIDDEN`(403, 구매자 계정)/`UNAUTHORIZED`(401).

## 코드 확인으로 파악한 사실

- **이미지 필드는 API 계약에 없다**: `ProductRegisterRequest`(`src/main/java/.../dto/ProductRegisterRequest.java`)에 `imageUrl` 필드가 없고, `docs/api/product.md`의 `POST /api/products` 요청/응답 어디에도 이미지 URL이 없다. `Product` 엔티티에는 `imageUrl` 컬럼이 존재하지만(`docs/db/product.md`) 등록 API가 이를 받지 않으므로, 폼에 이미지 입력을 넣어도 서버에 저장되지 않는다(main-page/product-detail 단계에서 이미 확인된 동일 제약).
- **정적 리소스 permitAll 매처가 서브디렉토리를 포함하지 않는다**: `SecurityConfig.filterChain`의 `.requestMatchers("/", "/*.html", "/css/**", "/js/**", "/partials/**").permitAll()`에서 `"/*.html"`은 Ant 경로 패턴상 단일 세그먼트만 매칭한다(`/login.html`처럼 루트 바로 아래 파일만 매칭, `/seller/products/new.html`처럼 다단계 경로는 매칭 안 됨 — 실제로 지금까지 만들어진 모든 프론트 페이지가 `static/` 루트에 flat하게 있는 이유와 일치). 이 매처를 그대로 두면 비로그인/구매자 상태로 이 페이지의 HTML 자체를 GET할 때 `anyRequest().authenticated()`에 걸려 `ApiAuthenticationEntryPoint`가 즉시 `401` JSON(`{"success":false,"code":"UNAUTHORIZED",...}`)을 응답한다 — 폼이 전혀 렌더링되지 않는다(폼 제출이 아니라 페이지 로딩 자체가 막힘). 이는 다른 페이지들이 지켜온 "헤더 로그인 상태 미연동, 서버 응답으로만 사후 판정" 원칙과 충돌한다.
- **CSS/JS/partial 참조는 전부 절대경로다**: `index.html`/`login.html`/`product.html`/`checkout.html` 전부 `href="/css/..."`, `src="/js/..."`, `js/include.js`의 `PARTIALS_BASE = '/partials'`, `js/api.js`의 `API_BASE = '/api'` 등 모두 절대경로를 쓴다. 절대경로는 요청한 문서의 위치와 무관하므로, `seller/products/new.html`이 서브디렉토리에 있어도 이 참조들 자체는 깨지지 않는다(위 SecurityConfig 이슈와는 별개 문제).
- **가격구간(`priceTiers`) 서버 검증 범위**: `PriceTierRequest`는 `minCount`/`price` 모두 `@NotNull`만 걸려 있다. 오름차순 정렬, 중복 `minCount` 금지, `minCount`가 2 이상이거나 `maxParticipants` 이하인지에 대한 서버 측 강제는 없다(코드 확인, `ProductService.savePriceTiers`도 그대로 저장). `PaymentService.resolveTeamPrice`는 `findByProductIdOrderByMinCountAsc`로 오름차순 조회 후 `currentCount >= tier.minCount`인 마지막 구간 가격을 적용하는 방식이라, 오름차순이 깨지거나 중복이 있으면 의도와 다른 가격이 적용될 수 있다(서버가 막지 않는 리스크).
- **`docs/policy/`에 상품 등록/가격구간 관련 정책 문서 없음**(`refund-trigger.md`/`team-success-criteria.md`/`caching.md` 확인, 해당 없음). 즉 가격구간 규칙의 SSOT는 API 문서(`docs/api/product.md`)의 필드 설명뿐이다.
- `docs/dev/ongoing/`에 다른 진행 중 작업 없음(중복 없음, README만 존재).

## 설계

### 산출물 / 라우팅

- 신규 정적 페이지: `src/main/resources/static/seller/products/new.html` + 전용 스크립트 `src/main/resources/static/js/seller-product-new.js`(파일명은 예시, 정확한 파일명은 Generate 단계에서 확정). 기존 페이지가 전부 `static/` 루트에 있던 것과 달리 이번엔 최초로 서브디렉토리 구조가 된다(와이어프레임 경로 `/seller/products/new`, 헤더 링크가 이미 이 경로를 가리킴).
- 헤더/푸터/CSS/JS 참조는 기존 페이지와 동일하게 **절대경로**(`/css/...`, `/js/...`, `/partials/...`, `Api.get('/products')` 등)를 그대로 쓴다 — 위 "코드 확인으로 파악한 사실"에서 검증했듯 서브디렉토리에 있어도 문제없다.
- **SecurityConfig 매처 조정이 필요하다**: 위에서 확인한 대로 `"/*.html"` 패턴이 이 경로를 커버하지 못하므로, 이 페이지가 비로그인 상태에서도 로딩되게 하려면 정적 리소스 permitAll 매처의 조정이 필요하다. **결정(사용자 확인 완료)**: `"/**/*.html"`을 기존 `"/*.html"`에 추가하는 방식으로 일반화한다(향후 `/seller/mypage` 등 다른 서브디렉토리 페이지도 커버). 기존 `"/*.html"`/`/api/**` 관련 규칙은 그대로 둔다. 정확한 코드 배치는 Generate 단계에서 정한다. **영향 계층에 `config`(SecurityConfig)가 추가된다**(순수 프론트 정적 파일 작업이 아니게 됨).
- `partials/header.html` 상단 주석 갱신: "판매 물품 등록"(`/seller/products/new.html`)이 이제 실제 페이지임을 반영(다른 항목처럼 "아직 없는 자리표시자" 문구 제거).

### 데이터 흐름

1. 폼 로드 시 별도 API 호출 없음(로그인 상태를 사전에 확인하지 않는 기존 원칙과 동일 — 비로그인/구매자 상태여도 폼은 항상 노출되고, 결과는 제출 시 서버 응답으로만 판정).
2. 폼 필드: 상품명(`name`, 필수), 설명(`description`, 선택), 정가(`basePrice`, 필수), 팀 최대 인원(`maxParticipants`, 필수), 가격구간(`priceTiers`, 최소 1행).
   - **이미지 입력은 포함하지 않는다** — "확인 필요" 참고. 카드 미리보기 영역은 main-page/product-detail과 동일하게 이미지 데이터 없이 플레이스홀더만 쓴다(있다면).
3. 가격구간 입력 UI 방향: 여러 행을 동적으로 추가/삭제할 수 있는 목록(각 행 = `minCount` + `price` 입력 쌍). 최소 1행은 항상 유지(마지막 1행에서는 삭제 버튼 비활성/숨김). "행 추가" 버튼으로 계속 늘릴 수 있음(상한은 두지 않음 — API도 상한을 두지 않음).
   - 클라이언트 가이드 검증 방향(서버가 강제하지 않는 부분을 UX로 보완): `minCount` 오름차순 권장, 중복 `minCount` 금지, `minCount`는 2 이상 & `maxParticipants` 이하 권장. 이 검증은 **UX 가드레일일 뿐 SSOT가 아니다** — 실제 최종 판정은 서버 `400 VALIDATION_FAILED` 응답이다(다른 폼들과 동일 원칙, 서버가 검증 안 하는 조합을 클라이언트가 통과시켜도 그대로 제출될 수 있음). 정확한 검증 타이밍/에러 문구/마크업은 Generate 단계에서 정한다.
4. "등록하기" 클릭 → `POST /api/products` `{ name, description, basePrice, maxParticipants, priceTiers }`.
   - 성공(`201`): 응답의 `productId`를 사용해 이동(대상은 "확인 필요" 참고).
   - 실패:
     - `400 VALIDATION_FAILED` → 공통 에러 배너(`.form-alert--error`)에 서버 `message` 표시(auth 폼과 동일 패턴, 필드별 구분 없이 공통 영역에).
     - `401 UNAUTHORIZED` → "로그인이 필요합니다" 안내 + 로그인 페이지 링크(product-detail/checkout과 동일 패턴).
     - `403 FORBIDDEN`(구매자 계정으로 시도) → 서버 `message` 표시.
5. "취소하기" 클릭 → API 호출 없이 이동만(대상은 checkout의 "삭제(취소)"처럼 단순 이동 — 메인 `/`로 이동, product-detail의 "계속 쇼핑하기"와 동일 패턴).

### 화면 구성 (컴포넌트 재사용 방향)

- 공통 폼 컴포넌트(`.form-group`/`.form-label`/`.form-input`/`.form-textarea`/`.form-error`/`.form-hint`) 재사용(auth 폼과 동일 패턴).
- 공통 에러/안내 배너 `.form-alert`/`.form-alert--error` 재사용.
- 가격구간 행 목록: 신규 컴포넌트가 필요할 가능성이 높다(기존 `.price-tiers-table`은 "표시용" 표라 "입력용" 행과는 다른 마크업이 필요할 수 있음) — 정확한 클래스 구성은 Generate 단계에서 정하되, **`hidden` 속성을 쓰는 요소를 새로 만들면 처음부터 `.클래스[hidden] { display: none; }` 보정 규칙을 같이 넣는다**(design-system `.btn[hidden]`, product-detail `.product-detail[hidden]`에서 이미 반복된 버그 패턴).
- 서버 응답 문자열(에러 `message` 등)은 기존 선례와 동일하게 `textContent`로만 대입(XSS 방지, `innerHTML` 미사용).

## 확인 필요 → 결정 완료 (사용자 확인)

1. **이미지 입력 필드**: **포함하지 않는다.** API가 받지 않아 넣어도 저장/재표시되지 않으므로(main-page/product-detail과 일관). 카드 미리보기는 무이미지 placeholder만.
2. **SecurityConfig 매처 조정 범위**: **일반화한다.** `/seller/mypage`, `/buyer/mypage` 등 와이어프레임상 앞으로 나올 서브디렉토리 페이지가 더 있으므로, 이 페이지 하나만 좁게 허용하지 않고 `permitAll` 매처에 `"/**/*.html"`(다단계 경로의 `.html`)을 기존 `"/*.html"`(단일 세그먼트)에 **추가**하는 방식으로 일반화한다. 기존 `"/*.html"`은 그대로 두고(회귀 방지), 새 패턴을 나란히 추가한다. `/api/**` 인가 규칙은 건드리지 않는다.
3. **등록 성공 후 이동 대상**: **(b) `product.html?id={새 productId}`.** 이미 존재하는 페이지라 등록 결과를 바로 확인할 수 있고, 리스크 섹션에서 언급한 "빈 팀 목록 정상 처리"는 product-detail 단계에서 이미 검증된 동작이라 문제 없음.

## 태스크

- [ ] (승인 후) `src/main/resources/static/seller/products/new.html` 마크업 작성 (헤더/푸터 include, 폼 필드, 가격구간 동적 행, 공통 에러 배너, 등록하기/취소하기 버튼)
- [ ] `js/seller-product-new.js`(가칭) 작성 — 가격구간 행 추가/삭제, 폼 값 수집·클라이언트 가드레일 검증, `POST /api/products` 호출 및 성공/실패(코드별) 처리, "취소하기" 처리(이동만)
- [ ] `css/components.css`에 가격구간 입력 행 등 필요한 신규 규칙 추가(`[hidden]` 보정 규칙 포함, 정확한 클래스 구성은 Generate 단계에서 결정)
- [ ] `src/main/java/.../config/SecurityConfig.java`의 정적 리소스 permitAll 매처를 서브디렉토리 경로가 매칭되도록 조정 (범위는 "확인 필요" 2번 답변에 따름)
- [ ] `partials/header.html` 상단 주석 갱신("판매 물품 등록"이 실제 페이지임을 반영)

## 평가(통과) 기준

`./gradlew bootRun` 후 브라우저로 아래를 확인한다.

- 비로그인 상태에서 헤더의 "판매 물품 등록" 링크를 클릭하면 `/seller/products/new.html`이 정상 렌더링된다(401 JSON이 뜨지 않는다 — SecurityConfig 매처 조정 확인).
- 판매자 계정으로 로그인한 상태에서 폼을 채우고 "등록하기" 클릭 시 상품이 생성되고(`201`), "확인 필요" 3번 답변에 따른 대상으로 이동한다.
- 구매자 계정으로 로그인한 상태에서 "등록하기" 클릭 시 `403 FORBIDDEN` 안내가 뜨고 페이지가 깨지지 않는다.
- 비로그인 상태에서 "등록하기" 클릭 시 `401 UNAUTHORIZED` 안내(로그인 필요 + 로그인 페이지 링크)가 뜬다.
- 필수 필드(상품명/정가/최대 인원/가격구간 1개 이상)를 비운 채 제출하거나 서버가 거부하는 값으로 제출 시 `400 VALIDATION_FAILED` 서버 `message`가 공통 에러 배너에 뜬다.
- 가격구간 행을 추가/삭제할 수 있고, 최소 1행은 삭제되지 않는다(UI 레벨 확인).
- "취소하기" 클릭 시 API 호출 없이 이동한다.
- (코드 리뷰) 서버 에러 `message` 등 신뢰할 수 없는 문자열이 `textContent`로만 DOM에 대입되어 있다(`innerHTML` 미사용).
- (코드 리뷰) `hidden` 속성을 쓰는 신규 요소에 필요한 `[hidden]` 보정 규칙이 함께 추가돼 있다.

## 리스크 / 전제

- **가격구간 정합성은 서버가 강제하지 않는다**: `minCount` 오름차순/중복/범위(2~N)는 서버 검증이 없어(코드 확인), 클라이언트 가드레일을 우회하거나 API를 직접 호출하면 의도와 다른 가격구간이 저장될 수 있다. 이번 작업은 UX 가드레일만 두고 서버 측 강제 로직 추가는 범위 밖이다(백엔드 변경이 필요하면 별도 작업).
- **SecurityConfig 변경은 이 페이지 하나에 한정되지 않을 수 있다**: 매처 조정 범위를 넓게 잡으면(예: 모든 서브디렉토리 html 허용) 향후 다른 서브디렉토리 페이지(`/seller/mypage`, `/buyer/mypage` 등, 아직 없음)에도 영향을 준다 — "확인 필요" 2번에서 방향을 정한 뒤 진행한다.
- **이미지 필드 결정에 따른 후속 영향**: "이미지 입력 없음"으로 확정되면 이후 이미지 업로드/URL 저장 기능은 `POST /api/products` API 자체의 확장(백엔드 별도 작업)이 선행돼야 한다 — 이번 작업의 범위 밖.
- **성공 후 이동 대상이 상세 페이지(product.html)일 경우**: `product.html`은 `GET /api/products/{id}/teams`도 호출하는데, 방금 등록한 상품은 아직 공구팀이 없어 빈 목록이 정상적으로 뜨는지 확인이 필요하다(기존 product-detail 로직이 빈 배열을 에러가 아닌 빈 상태로 처리하므로 문제 없을 것으로 예상되나, 실제 확인은 평가 단계에서 한다).
