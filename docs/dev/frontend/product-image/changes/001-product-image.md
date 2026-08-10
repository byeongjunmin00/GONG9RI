# 상품 이미지 지원

대상: `product/image`(백엔드) + `frontend/product-image`(프론트) — 완료 시 각 `changes/`로 채번 이동
담당: 전용운

## 배경 / 요구

사용자 요청: "상세 페이지 카드에 실제 이미지 지원(백엔드 API 확장 필요)". 상품 이미지는 지금까지 모든 카드(메인 목록, 상품 상세, 마이페이지)에서 CSS 그라디언트 placeholder로만 표시돼 왔다(`docs/dev/frontend/main-page/design.md`, `product-detail/design.md`에 이미 "이미지 URL 필드가 API 계약에 없다"고 명시된 제약). `Product` 엔티티에는 `imageUrl` 컬럼(`length=500`, nullable)이 이미 있지만 생성자·`update()` 어디에도 이 값을 채우는 경로가 없어 죽은 컬럼이었다(코드 확인).

이미지 소스: 사용자 지시에 따라 AI 생성 이미지가 아니라 실제 사진을 쓴다. 무료 라이선스 스톡 사진(Pexels License — 상업적 사용 무료, 저작자 표시 불필요, 코드 재배포·수정 가능)에서 가져온 직접 CDN 링크(`images.pexels.com/...`)를 데모/테스트 데이터에 사용한다. 저장소가 공개돼 있고 Railway 배포 사이트도 공개 접속 가능하므로 라이선스가 명확한 이미지를 쓰는 것으로 판단했다(임의의 저작권 있는 이미지를 스크래핑하지 않음).

## 코드 확인으로 파악한 사실

- `Product` 엔티티(`entity/Product.java`): `imageUrl` 컬럼 존재(51~52행), 생성자(62~68행)와 `update()`(70~75행) 둘 다 이 필드를 받지 않는다 — 추가 필요.
- `ProductRegisterRequest`: `name`/`description`/`basePrice`/`maxParticipants`/`priceTiers`만 있고 `imageUrl` 없음.
- `ProductResponse`(상세 조회 응답)·`ProductSummaryResponse`(목록 응답) 둘 다 `imageUrl` 필드 없음.
- `ProductService.register()`/`update()`: `new Product(...)`/`product.update(...)` 호출 시 `imageUrl`을 넘기지 않는다.
- 프론트 `product.html`: 상품 정보 영역(`#product-detail`)에 이미지 표시 영역 자체가 없다(카드 이미지 컨테이너 없음) — 신규로 추가해야 한다.
- 프론트 `js/main.js`: 메인페이지 카드 렌더링(`createProductCard` 계열 함수)에서 `.card-image` div를 이미 만들고 있지만 내용은 항상 비어 있어 CSS 그라디언트 placeholder만 보인다(53~55행) — `product.imageUrl`이 있으면 그 안에 `<img>`를 넣도록 확장 필요.
- 프론트 `seller/products/new.html`/`js/seller-product-new.js`, `seller/products/edit.html`/전용 스크립트: 이미지 URL을 입력받는 필드가 없다 — 등록/수정 폼에 선택 입력 필드로 추가 필요.
- `css/components.css`의 `.card-image`(main-page용)는 이미 `background: var(--gradient-brand-soft)`, `overflow: hidden`을 갖고 있고 `.card-image img { width/height:100%, object-fit:cover }` 규칙도 이미 존재한다(product-detail 단계에서 뱃지 오버레이용으로 만들어둔 것) — **`<img>`를 그 안에 넣기만 하면 기존 CSS로 커버된다(신규 CSS 불필요할 가능성 높음)**, 상세페이지용 이미지 영역은 이 클래스를 재사용하거나 유사하게 만든다.

## 설계

### 1. 백엔드 — `imageUrl` 필드 배관 (product/image)

- `Product.java`: 생성자에 `String imageUrl` 파라미터 추가(nullable 허용 — `@NotBlank` 등 필수 검증 없음), `update()` 메서드에도 동일하게 추가.
- `ProductRegisterRequest`: `String imageUrl` 필드 추가(검증 애노테이션 없음 — 선택 입력, URL 형식 검증 여부는 Generate 재량이나 필수는 아니다).
- `ProductResponse`(상세), `ProductSummaryResponse`(목록): `imageUrl` 필드 추가, `of()` 정적 팩토리에서 `product.getImageUrl()` 매핑.
- `ProductService.register()`/`update()`: `request.imageUrl()`을 `Product` 생성자/`update()`에 전달.
- `docs/api/product.md`: `GET /api/products`(목록)·`GET /api/products/{id}`(상세) 응답과 `POST/PUT /api/products` 요청에 `imageUrl`(String, nullable) 필드 추가 — 이 Plan 문서와 함께 이번 단계에서 반영한다(아래 "문서 산출물" 참고).
- 영향 계층: `entity`, `dto`(3개: RegisterRequest/Response/SummaryResponse), `service`. `controller`/`SecurityConfig`/`repository` 변경 없음(기존 필드 추가라 시그니처만 확장).
- 캐시 영향: `ProductService.list()`/`detail()`은 이미 `@Cacheable`이 걸려 있다 — 필드 추가 자체는 캐시 키/무효화 전략에 영향 없음(기존 register/update 시 캐시 무효화 로직 그대로 재사용).

### 2. 프론트엔드 — 이미지 표시 및 입력 (frontend/product-image)

- **메인 페이지 카드**(`js/main.js`): 기존 `.card-image` div 생성 지점에서 `product.imageUrl`이 있으면 `<img src="..." alt="...">`를 그 안에 추가(alt는 상품명, `textContent` 아닌 `alt` 속성이라 XSS 우려 없음 — 다만 속성 삽입 시에도 신뢰 불가 문자열은 안전하게 처리). 없으면 지금처럼 빈 채로 두어 그라디언트 placeholder 유지(하위 호환 — 기존에 이미지 없이 등록된 상품도 안 깨짐).
- **상품 상세 페이지**(`product.html`/`js/product.js`): `#product-detail` 안에 이미지 영역을 신규로 추가(마크업 위치는 Generate가 정하되, 상단 — 이름/설명보다 먼저 보이는 위치를 권장). 기존 `.card-image` 클래스와 `img` 자식 규칙을 재사용하거나 필요하면 상세페이지 전용 크기 규칙만 최소 추가.
- **판매 물품 등록/수정 폼**(`seller/products/new.html`+JS, `seller/products/edit.html`+JS): "상품 이미지 URL" 입력 필드(선택, `type="url"` 또는 `type="text"`, placeholder로 예시 URL 안내) 추가. 등록 시 `POST /api/products` body에 `imageUrl` 포함(비어 있으면 생략 또는 `null`). 수정 폼은 기존 값 프리필.
- **마이페이지**(`seller/mypage.html`의 상품 목록, 필요하면): 이미지까지 표시할지는 범위 밖으로 둔다 — 사용자 요청은 "상세 페이지 카드"와 그 데이터 소스(등록 폼)에 한정되고, 마이페이지 목록은 지금처럼 텍스트 위주 리스트 유지(과도한 확장 방지). **다르게 원하면 확인 필요.**
- **XSS/안전성**: `imageUrl`은 사용자(판매자)가 입력한 문자열이다. `<img src="...">`에 그대로 대입할 때 `src` 속성 자체는 스크립트 실행 경로가 아니라 `innerHTML` 문자열 조합 없이 `img.src = url`(속성 대입)로만 처리하면 XSS 위험이 없다(계획에 명시, 구체 구현은 Generate).
- CSS: 기존 `.card-image`/`.card-image img` 재사용 우선, 상세페이지 전용 크기·비율 조정이 필요하면 최소 규칙만 `components.css`에 추가.

### 3. 데모/테스트 데이터에 쓸 이미지 (Plan에서 확정)

Evaluate 단계의 브라우저 확인 및 이후 시연에 사용할 이미지 3장(Pexels License, 상업적 사용 무료):

| 용도 예시 | 직접 이미지 URL |
|---|---|
| 감귤류 과일 | `https://images.pexels.com/photos/2294477/pexels-photo-2294477.jpeg?auto=compress&cs=tinysrgb&w=800` |
| 빵/크루아상 | `https://images.pexels.com/photos/2135/food-france-morning-breakfast.jpg?auto=compress&cs=tinysrgb&w=800` |
| 우유/유제품 | `https://images.pexels.com/photos/4187717/pexels-photo-4187717.jpeg?auto=compress&cs=tinysrgb&w=800` |

이 URL들은 상품 데이터가 아니라 **평가/시연용 예시 값**이다 — DB 시드 데이터나 마이그레이션으로 넣지 않는다(이 프로젝트는 사용자가 직접 등록하는 구조라 그런 시드 자체가 없음). Evaluate 단계에서 브라우저로 실제 등록 폼에 입력해 렌더링을 확인하는 용도로만 쓴다.

## 태스크

- [ ] `entity/Product.java` — 생성자·`update()`에 `imageUrl` 파라미터 추가
- [ ] `dto/ProductRegisterRequest.java` — `imageUrl` 필드 추가
- [ ] `dto/ProductResponse.java`, `dto/ProductSummaryResponse.java` — `imageUrl` 필드 추가 + `of()` 매핑
- [ ] `service/ProductService.java` — `register()`/`update()`에서 `imageUrl` 전달
- [ ] `docs/api/product.md` — 4개 엔드포인트 문서에 `imageUrl` 필드 반영
- [ ] `js/main.js` — 카드 이미지 영역에 `imageUrl` 있으면 `<img>` 삽입
- [ ] `product.html` + `js/product.js` — 상세 페이지에 이미지 영역 신규 추가
- [ ] `seller/products/new.html` + JS, `seller/products/edit.html` + JS — "상품 이미지 URL" 입력 필드 추가(등록/수정 요청에 포함, 수정 폼은 프리필)
- [ ] 필요 시 `css/components.css`에 상세페이지 이미지 영역 최소 규칙 추가

## 평가(통과) 기준

- `./gradlew test` 전체 통과(기존 `ProductServiceTest`/`ProductControllerTest` 등이 있다면 `imageUrl` 관련 회귀 없는지 포함).
- `./gradlew bootRun` 후 브라우저 실측:
  - 판매자 계정으로 상품 등록 시 "이미지 URL" 필드에 위 예시 URL(또는 다른 유효한 이미지 URL) 입력 → 등록 성공 → 상세 페이지에 실제 이미지가 렌더링된다.
  - 같은 상품이 메인 페이지 카드에서도 실제 이미지로 표시된다.
  - 이미지 URL 없이 등록한 기존 방식 상품은 여전히 그라디언트 placeholder로 깨짐 없이 표시된다(하위 호환).
  - 상품 수정 폼에 기존 이미지 URL이 프리필되고, 변경 후 저장하면 반영된다.
  - 이미지 URL을 잘못된 값(예: 존재하지 않는 URL)으로 넣어도 페이지 자체가 깨지지 않는다(`<img>` 로드 실패는 브라우저 기본 깨진 이미지 아이콘으로 처리 — 별도 폴백 로직은 이번 범위에서 필수 아님, Generate 재량).
- (코드 리뷰) `imageUrl`을 DOM에 넣을 때 `innerHTML` 문자열 조합이 아니라 속성 대입(`img.src =`, `img.alt =`)으로만 처리했는지.

## 리스크 / 전제

- **URL 유효성 검증 없음**: 서버는 `imageUrl`이 실제 이미지를 가리키는지 검증하지 않는다(단순 문자열 컬럼). 판매자가 아무 문자열이나 넣으면 깨진 이미지로 보일 수 있으나, 이는 이번 범위에서 서버 검증을 추가하지 않는다(정책 문서에도 관련 규칙 없음).
- **파일 업로드 아님**: 이번 작업은 이미지 URL(링크) 저장 방식이다. 실제 파일 업로드·저장소(S3 등) 연동은 범위 밖 — 훨씬 큰 인프라 작업이라 사용자 요청("인터넷에서 가져와")과도 URL 방식이 더 맞는다고 판단.
- **기존 데이터 영향 없음**: `imageUrl`은 nullable이라 이미 등록된 상품(이미지 URL 없음)은 마이그레이션 없이 그대로 동작한다(값이 `null`일 뿐).
- **캐시**: 등록/수정 시 기존 `@CacheEvict` 로직이 그대로 적용되므로 이미지 추가로 인한 캐시 정합성 문제는 없다.
- **외부 이미지 호스트 의존**: Pexels CDN이 다운되거나 이미지가 삭제되면 그 URL을 쓴 상품의 이미지도 깨진다 — 데모/평가 목적이라 이번 범위에서 이 리스크에 대한 별도 대응(자체 호스팅 등)은 하지 않는다.

## 문서 산출물

- 이 계획 문서: `docs/dev/ongoing/product-image.md`
- `docs/api/product.md` 갱신은 승인 후 Generate 단계에서 실제 구현과 함께 반영한다(엔드포인트 문서와 코드가 어긋나지 않도록 같은 단계에서 처리).
- Evaluate 통과 시 `docs/dev/product/image/design.md`, `docs/dev/frontend/product-image/design.md` 신규 작성 + 이 ongoing 문서를 각 `changes/001-*.md`로 채번 이동.
