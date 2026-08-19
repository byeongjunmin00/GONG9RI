# 가격 구간 최소 인원 서버 검증 추가

대상: product/crud
담당: 전용운

## 배경 / 요구

판매자가 상품 등록/수정 시 가격 구간(`priceTiers[].minCount`, "이 가격이 적용되는 최소 참여 인원")을 1로 설정할 수 있는 버그가 보고됨. 정책상 팀 최소 인원은 2명 이상이어야 한다(1명은 "혼자구매"와 동일해 팀 개념이 성립하지 않음, `docs/db/price_tier.md`: `team/create`가 이 값을 목표 인원 선택지로 사용).

원인 확인: `PriceTierRequest`(`src/main/java/com/gong9ri/gong9ri/dto/PriceTierRequest.java`)의 `minCount` 필드는 `@NotNull`만 있고 하한 제약이 없다. 프론트(`seller-product-new.js`/`seller-product-edit.js`)는 `minCount < 2`를 막는 가드레일이 있지만, 코드 주석에 명시된 대로 "SSOT는 서버 응답"이며 서버는 이를 강제하지 않는다. 즉 API를 직접 호출하면(또는 프론트 검증을 우회하면) `minCount=1`(혹은 0/음수)로 상품이 등록/수정될 수 있다.

## 설계

- 계약 변경: `POST /api/products`, `PUT /api/products/{id}`의 `priceTiers[].minCount`에 최소값 2 제약을 추가한다 (`docs/api/product.md` 갱신).
- 영향 계층: `dto`(Bean Validation 제약 추가) — `service`/`controller`는 기존 `VALIDATION_FAILED`(`400`) 처리 경로를 그대로 탄다(신규 분기 불필요).
- 범위: 이번 작업은 사용자가 보고한 "최소 인원 2 미만 허용" 문제만 고친다. 프론트 가드레일에 이미 존재하는 다른 검증(오름차순/중복/최대인원 이하)은 서버 미검증 상태가 동일하게 남아있지만 이번 스코프 밖이다(리스크로만 기록).

## 태스크

- [ ] `PriceTierRequest.minCount`에 서버 측 최소값(2) 제약 추가
- [ ] `docs/api/product.md`의 `priceTiers[].minCount` 설명에 "2 이상" 제약 명시
- [ ] 등록/수정 API에 `minCount=1`(또는 그 이하) 요청 시 `400 VALIDATION_FAILED`를 반환하는지 검증하는 컨트롤러 테스트 추가

## 평가(통과) 기준

- `./gradlew test` 전체 통과
- `minCount=1`(또는 0/음수)로 상품 등록/수정 요청 시 `400 VALIDATION_FAILED` 응답 확인(신규 테스트)
- 기존 정상 케이스(`minCount=2` 이상)는 그대로 통과(기존 테스트 회귀 없음)
