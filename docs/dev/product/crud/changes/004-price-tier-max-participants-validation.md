# 상품 가격 구간 검증 강화 및 maxParticipants 파생 자동화

대상: product/crud
담당: 전용운

## 배경 / 요구

- 현재 상품 등록/수정 시 `Product.maxParticipants`(상단 입력)와 `PriceTier.minCount`(하단 가격 구간) 사이의 검증이 누락되어 있음.
  - 예: `maxParticipants = 5`인데 `PriceTier.minCount = 10` 등록 가능 → 구매자가 10명짜리 팀 신설 가능해지는 모순 발생.
  - `maxParticipants` 및 `priceTier.price`에 0, 음수 등 유효하지 않은 값이 전달될 수 있음.
  - `priceTier.minCount` 중복 등록 및 기본가보다 비싼 공구가/인원 증가 시 가격 역전 현상 방치.
- 또한, 하단에서 가격 구간표(`priceTiers`)를 등록하면서 상단에 `maxParticipants`를 별도로 입력받는 것은 판매자 UX 측면에서 중복이자 혼란을 유발함.
- **목적**:
  1. 판매자가 입력한 `priceTiers` 중 **가장 큰 `minCount`를 `Product.maxParticipants`로 자동 파생/지정**하여 데이터 모순과 이중 입력 피로를 원천 예방함.
  2. 가격 구간의 유효성(기본가 대비 할인율, 단조 감소, 중복 금지 등)을 서버 레벨에서 철저히 검증함.

---

## 설계 및 변경 방향

### 1. DTO & 백엔드 서비스 검증 (`ProductService`, DTOs)
- **DTO Bean Validation 강화**:
  - `ProductRegisterRequest.basePrice`: `@NotNull @Min(1)`
  - `PriceTierRequest.price`: `@NotNull @Min(1)`
  - `PriceTierRequest.minCount`: `@NotNull @Min(2)`
- **`ProductService` 비즈니스 검증 (`validateProductRegisterRequest`)**:
  - **`maxParticipants` 자동 파생/동기화**:
    - 백엔드가 요청받은 `priceTiers` 중 `max(minCount)`를 계산하여 `Product.maxParticipants`로 저장함 (DTO의 `maxParticipants` 필드는 선택 입력 혹은 서버 자동 산출값으로 대체/동기화하여 불일치 원천 차단).
  - **가격 구간 정합성 검증**:
    - 모든 `priceTier.price < basePrice` (공구 가격은 기본가보다 엄격히 싸야 함)
    - `priceTier.minCount` 중복 금지
    - 인원이 커질수록 가격은 같거나 낮아져야 함 (`priceTier[N+1].price <= priceTier[N].price`)

### 2. 프론트엔드 UI 개선 (`seller/products/new.html`, `edit.html`, `js/seller-product-*.js`)
- `maxParticipants` (팀 최대 인원) 입력 칸을 제거하거나, 입력된 가격 구간의 최대 인원을 보여주는 **자동 안내/읽기 전용(Readonly)** 필드로 전환.
- 폼 제출 시 `priceTiers` 중 가장 큰 `minCount`가 `maxParticipants`로 전달되거나 서버가 알아서 파생 처리하도록 연동.

---

## 태스크

- [ ] `ProductRegisterRequest`, `PriceTierRequest` Bean Validation 애노테이션 강화
- [ ] `ProductService.register()` 및 `update()`에 `validateProductRegisterRequest()` 비즈니스 검증 로직 추가
  - `priceTiers` 최댓값으로 `maxParticipants` 파생 적용
  - 기본가 대비 할인 검증 (`price < basePrice`)
  - 가격 단조 감소 검증 (`priceTier[N+1].price <= priceTier[N].price`)
  - `minCount` 중복 검증
- [ ] `seller/products/new.html`, `edit.html` 및 관련 JS 수정 (maxParticipants UI 필드 정리 / 자동 계산 연동)
- [ ] `ProductControllerTest`, `ProductServiceTest` 단위 및 통합 테스트 작성/갱신
  - 정상 등록/수정 케이스
  - 가격 역전, 중복 minCount, basePrice 초과 시 400 VALIDATION_FAILED 검증 케이스
- [ ] `docs/dev/product/crud/design.md` 갱신 및 `ongoing` 문서를 `changes/`로 채번 이동

---

## 평가(통과) 기준

- `basePrice`보다 비싸거나 같은 `priceTier` 등록 시 `400 VALIDATION_FAILED`.
- 인원이 증가함에도 가격이 비싸지는 `priceTier` 등록 시 `400 VALIDATION_FAILED`.
- `minCount`가 중복된 `priceTier` 등록 시 `400 VALIDATION_FAILED`.
- 등록 성공 시, `priceTiers` 중 가장 큰 `minCount`가 `Product.maxParticipants`로 정확히 지정됨.
- 판매자 상품 등록/수정 화면에서 별도의 maxParticipants 입력 없이도 가격 구간 등록만으로 정상 등록/수정 완료.
- `./gradlew test` 전체 테스트 스위트 100% 통과 (`BUILD SUCCESSFUL`).

---

## 리스크 / 전제

- 기존 등록된 테스트 데이터 중 `maxParticipants`와 `priceTiers` 기준에 부합하지 않는 픽스처가 있을 경우 테스트 수정이 필요할 수 있음.
- DTO에서 `maxParticipants` 필드를 nullable로 처리하거나 파생값으로 덮어쓰는 과정에서 기존 API 계약 파급 효과를 최소화하도록 하위 호환성을 유지함.
