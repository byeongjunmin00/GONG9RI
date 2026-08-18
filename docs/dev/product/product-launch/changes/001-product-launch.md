# 오픈예정 상품 상태 추가

대상: product/product-launch
담당: 민병준

## 배경 / 요구

와디즈 참고 화면의 "오픈예정" 탭에서 착안. "욕심나는 것들 싹 다 해보자"는 사용자 요청으로 착수(순서는 위임받아 직접 정함). 별도 브라우징 탭 대신 상품 필드 하나 + 구매 차단으로 스코프를 좁힘(design.md 참고).

## 설계

`docs/dev/product/product-launch/design.md` 참고 — `Product.openAt`(nullable), 결제/팀신설 진입점 2곳에서만 차단, 목록/상세 노출은 그대로 유지.

## 태스크

- [x] `Product.openAt` 필드 + `isNotYetOpen()` + 생성자/`update()` 확장
- [x] `ProductRegisterRequest.openAt`(`@Future`, 선택)
- [x] `ProductResponse`/`ProductSummaryResponse`에 `openAt` 노출
- [x] `ErrorCode.PRODUCT_NOT_YET_OPEN`(409) + `PaymentService.create()`/`TeamService.create()` 차단
- [x] 판매자 등록/수정 폼에 오픈예정 시각(datetime-local) 입력
- [x] 메인 페이지 카드에 "오픈예정" 배지
- [x] 상품 상세 페이지에 안내 배너 + 구매/신설 버튼 비활성화
- [x] 테스트: 미래 openAt 등록 성공, 과거 openAt 검증 실패, 결제/팀신설 차단(409) 각각
- [x] `docs/api/product.md`, `docs/db/product.md` 갱신

## 평가(통과) 기준

- `./gradlew test` 통과
- 로컬 실서버로 오픈예정 상품 등록 → 목록 배지 → 구매/팀신설 시도 시 409 → 오픈 시각 지난 뒤 정상 구매까지 실측 확인
