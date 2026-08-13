# 공구팀 신설 시 목표 인원(price_tier) 선택

대상: team/crud (기존 기능 확장, 새 폴더 아님)
담당: (미정)

## 배경 / 요구

판매자는 상품 등록 시 "몇 명이 모이면 얼마"라는 가격 구간표(`price_tier`, `minCount`+`price` 쌍 여러 개)를 이미
등록할 수 있다(`product/crud`, `seller/products/new.html`). 하지만 **구매자가 공구팀을 신설할 때는 이 구간표 중
하나를 고를 수가 없다** — 지금 `TeamController.create()`는 요청 body가 없고, `TeamService.create()`는
`product.getMaxParticipants()`(상품에 하나뿐인 값)를 무조건 그대로 `GroupBuyTeam.maxParticipants`(정원)에
복사한다. 즉 판매자가 등록해둔 여러 구간(예: 2명/5명/10명)이 있어도 구매자는 그중 하나를 선택하는 게 아니라
언제나 상품의 단일 `maxParticipants` 값으로만 팀이 만들어진다.

이번 작업은 **구매자가 팀 신설 시 그 상품의 `price_tier.minCount` 목록 중 정확히 하나를 선택**하게 하고, 그
값이 곧 그 팀의 정원(`GroupBuyTeam.maxParticipants`)이 되게 한다. 임의의 숫자를 자유 입력하는 게 아니라
**판매자가 미리 정해둔 몇 가지 옵션 중 하나만** 고를 수 있어야 한다.

> **재작업 배경**: 이전에 같은 요구사항을 "`Product`에 `minParticipants`~`maxParticipants` 범위 컬럼을 새로
> 추가해서 그 사이 임의 정수를 자유 선택하게 하는" 안으로 계획했었는데, 요구사항을 잘못 해석한 것이었다(사용자가
> 정정). 그 계획은 프로덕션 DB에 되돌리기 어려운 컬럼 추가가 필요했고 이미 revert됨 — 폐기. 이번 계획은
> `Product`/DB에 **새 컬럼을 추가하지 않는다**.

## 조사 결과 (현재 상태)

- **`Product.maxParticipants`의 실제 쓰임**(`grep` 전체 확인):
  - 서버: `Product` 엔티티, `ProductRegisterRequest`(`@NotNull`), `ProductResponse`/`ProductSummaryResponse`/
    `SellerProductResponse`/`ProductSearchResult` DTO, `ProductService.register`/`update`(그대로 저장),
    `TeamService.create()`(현재 이 값을 그대로 `GroupBuyTeam.maxParticipants`에 복사 — 이번에 바뀌는 지점).
  - 프론트: 판매자 등록/수정 화면(`seller/products/{new,edit}.html`의 `maxParticipants` 입력 필드) —
    `seller-product-new.js`/`seller-product-edit.js`의 `validatePriceTiersGuardrail()`이 **"각 `price_tier`의
    `minCount`는 `maxParticipants`를 넘을 수 없다"**는 상한 검증을 이미 UI 레벨로 하고 있다(주석: "서버가
    강제하지 않는 부분... SSOT는 서버 응답" — 실제로 `ProductService`엔 이 상한 검증이 없다, 기존 갭, 이번
    스코프 밖). 구매자 화면(`product.js`)은 상세 표시용으로만 쓴다. `main.js`(메인 목록 카드)의
    "N인 모이면 최저가" 라벨도 이 값을 그대로 표시한다.
  - **결론**: `Product.maxParticipants`는 이미 코드 곳곳(엔티티·DTO 5개·프론트 4개 파일·등록 폼 필수 입력)에
    박혀 있고, "이 상품에 허용되는 팀 인원 상한(가드레일 참고값)"이라는 의미로 이미 실질적으로 쓰이고 있다.
    이 필드를 제거하거나 이름을 바꾸면 위 계층 전부(엔티티/DTO/등록·수정 폼/검증 로직/테스트)를 건드려야 하는데,
    이번 기능(구매자의 팀 정원 선택)과는 직접 관련이 없다.
  - **결정: 필드를 그대로 두고 의미만 재정의한다** (스키마 변경 없음). `Product.maxParticipants`는 앞으로
    "이 상품에 허용되는 팀 인원 **상한**(참고값, 각 `price_tier.minCount`가 넘지 않아야 하는 기준)"으로만
    쓰이고, **팀의 실제 정원은 더 이상 이 값에서 오지 않는다** — 구매자가 신설 시 고른 `price_tier.minCount`가
    `GroupBuyTeam.maxParticipants`가 된다. 이 재정의는 `docs/db/product.md`에 반영했다(이번 Plan에서 완료).
    - 제거 안을 채택하지 않은 이유: (1) 스키마 변경 없이 "그대로 두고 의미만 재정의"하는 쪽이 훨씬 작은
      변경이고, (2) 등록 폼에서 판매자가 가격 구간을 입력하기 전 대략적인 팀 규모 상한을 잡아두는 참고값으로는
      여전히 쓸모가 있다(가드레일 UI가 이미 이 용도로 쓰고 있음), (3) 제거하려면 프로덕션 데이터가 있는
      컬럼을 드롭해야 해서 이번 재작업의 취지(되돌리기 어려운 스키마 변경 회피)와 정면으로 배치된다.
- **`GroupBuyTeam.maxParticipants`**: 팀 생성 시점에 고정되는 스냅샷이라는 기존 설계(재검증 완료,
  `docs/db/group_buy_team.md`, `docs/policy/team-success-criteria.md`)는 그대로 유지한다. 바뀌는 건 "무엇을
  복사해오는지"뿐 — `product.maxParticipants` 대신 **구매자가 선택한 `price_tier.minCount`**를 복사한다.
  필드/컬럼 자체는 변경 없음.
- **`PaymentService.resolveTeamPrice()`(커밋 `9b5ad57`) 코드 검증 결과**: `price_tier`를 `minCount` 오름차순으로
  조회한 뒤, `team.maxParticipants >= tier.minCount`를 만족하는 동안 계속 `price`를 갱신하고 첫 불만족 지점에서
  `break`한다. 오름차순 전제이므로 구매자가 고른 값과 **정확히 일치하는 `minCount`의 tier**가 항상 "만족하는
  가장 큰 구간"이 되어 그 tier의 가격이 정확히 적용된다(더 큰 `minCount`를 가진 다음 tier들은 전부 불만족이라
  break). **이 로직은 변경 불필요** — 이미 이번 설계와 정확히 맞물린다.
- **`price_tier`가 하나도 없는 상품**: 발생하지 않는다. `ProductRegisterRequest.priceTiers`가 `@NotEmpty`라
  등록 시점에 최소 1개가 강제되고, 수정도 동일 DTO(`PUT`)를 써서 전체 교체하므로 항상 최소 1개가 유지된다.
  **별도 폴백 로직(예: `product.maxParticipants` 단일값으로 대체)은 불필요** — "목록에 없는 값" 에러 경로가
  자연히 이 경우도 포함한다(빈 목록이면 어떤 `targetParticipants`를 보내도 항상 존재하지 않음 에러가 된다).
- **스키마 변경 필요 여부**: 불필요함을 최종 확인. `product`/`group_buy_team`/`price_tier` 어느 테이블에도 컬럼
  추가·삭제가 필요 없다 — `GroupBuyTeam.maxParticipants` 컬럼은 이미 존재하고, 거기 들어가는 값의 "출처"만
  `product.maxParticipants`에서 `price_tier.minCount`(구매자 선택)로 바뀔 뿐이다.
- **프론트 판매자 등록 화면**: 변경 불필요 확인 — `price_tier` 입력 UI(`seller/products/new.html`,
  `seller-product-new.js:86-148`)는 이미 여러 구간을 입력받으므로 그대로 재사용한다.
- **테스트 영향 범위**:
  - `TeamControllerTest`: `create_success`/`create_forbidden_seller`/`create_productNotFound`/
    `create_unauthorized`가 전부 body 없이 POST하고, `saveProduct()`도 `price_tier`를 만들지 않는다 — 이번
    API 계약 변경(body 필수화)으로 **전부 수정 필요**(요청 body 추가 + 상품에 최소 1개 `price_tier` 픽스처
    추가). 새 실패 케이스(목록에 없는 값 → 400) 테스트도 추가한다.
  - `ProductControllerTest`: 이 컨트롤러는 팀 신설 엔드포인트를 호출하지 않아 **수정 불필요**(확인 완료).
  - `PaymentControllerTest`: `GroupBuyTeam`을 `TeamService.create()`가 아니라 리포지토리로 직접 생성해
    `maxParticipants`를 테스트가 원하는 값으로 바로 넣는다 — 팀 신설 API를 거치지 않으므로 **수정 불필요**
    (확인 완료).

## 설계

### API 계약 (반영 완료 — `docs/api/team.md`)

- `POST /api/products/{productId}/teams`가 요청 body를 받도록 바뀐다: `{ "targetParticipants": <int> }`.
- 서버는 `targetParticipants`가 해당 상품의 `price_tier.minCount` 목록에 **정확히 존재하는지**(범위 체크가
  아니라 존재 여부 체크) 검증한다. 존재하지 않으면 `400 INVALID_TARGET_PARTICIPANTS`(신규 에러코드). 필드
  자체가 없으면 기존 관례대로 `400 VALIDATION_FAILED`.
- 검증 통과 시 `GroupBuyTeam.maxParticipants = targetParticipants`로 팀을 생성한다(나머지 흐름은 기존과 동일 —
  leader 자동 참여, `currentCount=1`, `deadline`=생성+7일).
- 응답 형식(`TeamResponse`)은 변경 없음 — `maxParticipants` 필드가 이제 요청값 그대로를 반영할 뿐.

### 영향 계층

- **DTO**: 팀 신설 요청을 담을 새 request DTO(현재는 request 파라미터가 없음) 1개 추가.
- **Controller**: `TeamController.create()`가 `@RequestBody`를 받도록 변경.
- **Service**: `TeamService.create()`가 (1) `productId`로 `price_tier` 목록 조회, (2) 요청받은
  `targetParticipants`가 그 목록의 `minCount` 중 하나와 일치하는지 검증, (3) 일치하면 그 값으로
  `GroupBuyTeam` 생성 — 이 순서로 바뀐다. `PriceTierRepository`(이미 존재)를 새로 의존.
- **에러 코드**: `ErrorCode`에 `INVALID_TARGET_PARTICIPANTS`(400) 추가.
- **프론트(구매자, `product.js`/`product.html`)**: 이미 `GET /api/products/{id}` 응답에 `priceTiers` 배열이
  있으므로(현재도 표 형태로 렌더링 중), "신규 팀 신설하기" 액션 흐름에 **이 `priceTiers`의 `minCount` 값들 중
  하나를 고르게 하는 선택 UI**(버튼/라디오 등, 구체 마크업은 Generate에서 결정)를 추가하고, 고른 값을
  `POST .../teams`의 `targetParticipants`로 보낸다. 아무 것도 고르지 않은 상태로는 신설을 시도할 수 없게 한다
  (버튼 비활성화 등 UX는 Generate 재량).
- **프론트(판매자)**: 변경 없음(위 조사 결과).

### 스코프 제외 / 명시 사항

- 판매자가 나중에 `price_tier`를 수정/삭제해도 **이미 만들어진 `GroupBuyTeam`에는 영향 없다** — 기존 설계와
  동일하게 `GroupBuyTeam.maxParticipants`는 생성 시점 스냅샷이라 그 이후 `price_tier` 변경과 무관하게 불변.
  (`product/crud` design.md의 "상품 수정 시 기존 price_tier 전부 삭제 후 재삽입" 정책과 충돌 없음 — 이미
  생성된 팀은 `price_tier` 테이블을 다시 참조하지 않는다.)
- `Product.maxParticipants` 필드/컬럼 자체의 제거·이름 변경은 이번 스코프에 포함하지 않는다(위 조사 결과의
  근거).
- 판매자 등록/수정 화면 변경은 스코프에 포함하지 않는다(이미 있는 UI로 충분).
- "가격 구간의 `minCount`가 `product.maxParticipants`를 넘으면 안 된다"는 규칙을 서버가 강제하지 않는
  기존 갭은 이번 스코프에서 다루지 않는다 — 이번 설계(존재 여부 체크)는 이 갭과 무관하게 정확히 동작한다
  (판매자가 실수로 상한을 넘는 tier를 등록해도, 구매자는 그 tier의 `minCount`를 그대로 팀 정원으로 선택할 수
  있을 뿐 오류가 나지 않는다 — 기존에도 이미 그런 tier로 결제가 가능했으므로 새로운 위험이 아니다).

## 태스크

- [ ] `ErrorCode.INVALID_TARGET_PARTICIPANTS`(400) 추가
- [ ] 팀 신설 요청 DTO 추가 (`targetParticipants` 필수)
- [ ] `TeamController.create()` — `@RequestBody` 반영
- [ ] `TeamService.create()` — `price_tier.minCount` 존재 검증 + 통과 시 해당 값으로 `GroupBuyTeam` 생성
- [ ] `product.js`/`product.html` — 구매자가 `priceTiers`의 `minCount` 중 하나를 고르는 선택 UI, 선택값을
      팀 신설 요청 body에 포함
- [ ] `TeamControllerTest` — 기존 `create_*` 4케이스에 요청 body + `price_tier` 픽스처 추가, 신규 실패 케이스
      (목록에 없는 값 → 400 `INVALID_TARGET_PARTICIPANTS`) 추가, 정상 선택 시 생성된 `GroupBuyTeam.maxParticipants`가
      선택값과 같은지 확인하는 케이스 추가
- [ ] Evaluate 통과 후 `docs/dev/team/crud/design.md` 갱신(이 변경 반영) + 이 문서를 `docs/dev/team/crud/changes/`로
      채번 이동

## 평가(통과) 기준

- 상품에 등록된 `price_tier.minCount` 중 하나(예: 2, 5, 10)로 팀 신설 요청 시 `201`, 생성된 팀의
  `maxParticipants`가 그 값과 같다.
- 그 목록에 없는 값(예: 3)으로 요청 시 `400 INVALID_TARGET_PARTICIPANTS`.
- `targetParticipants` 필드 누락 시 `400 VALIDATION_FAILED`.
- 선택한 값으로 만든 팀에 대해 결제를 생성하면(`POST /api/payments`), 그 값에 해당하는 `price_tier.price`가
  정확히 적용된다(기존 `PaymentControllerTest`가 이미 이 로직 자체를 검증하고 있으므로, 신설 API가 올바른
  값을 `GroupBuyTeam.maxParticipants`에 넣기만 하면 별도 수정 없이 성립 — 필요 시 확인용 케이스 1개 추가).
- 판매자 계정 시도 403, 비로그인 401, 존재하지 않는 상품 404 — 기존 케이스 유지.
- `ProductControllerTest`/`PaymentControllerTest`는 수정 없이 그대로 통과(이번 계약 변경과 무관함을 재확인).
- `./gradlew test` 전체 통과.

## 리스크 / 전제

- API 계약 변경(요청 body 필수화)이라 기존 프론트(`product.js`)도 같은 배포 단위로 함께 바꿔야 한다 —
  서버만 먼저 배포하면 body 없는 기존 프론트 요청이 전부 `400 VALIDATION_FAILED`로 실패한다(단일 인스턴스/
  단일 배포라 실제로는 문제 없을 전제, 별도 API 버전 관리 없음).
- `product.maxParticipants`의 의미 재정의(상한 참고값)는 문서(`docs/db/product.md`) 상으로만 명확해지고,
  서버가 이 상한을 실제로 강제하는 로직은 없다(기존 갭, 이번 스코프 밖) — 판매자가 상한보다 큰 `minCount`의
  `price_tier`를 등록하면, 구매자는 그 값을 정상적으로 목표 인원으로 선택할 수 있다(오류 아님, 기존 결제
  로직도 이미 이렇게 동작했으므로 새로운 리스크는 아니다).
- `TeamService.create()`가 이제 상품별 `price_tier` 목록을 매 요청마다 조회한다 — 상품 상세/목록처럼 캐싱된
  경로가 아니라 매번 DB 조회이지만, 이미 `product/crud`에도 없던 캐시이고 신설은 참가(`join`)보다 훨씬 낮은
  빈도이므로 성능 영향은 미미할 것으로 예상(실측 근거는 없음, 필요 시 Evaluate에서 재검토).
