# 판매자 신뢰 배지 추가

대상: product/seller-trust
담당: 민병준

## 배경 / 요구

와디즈 참고 화면의 판매자 신뢰도 표시에서 착안. "욕심나는 것들 싹 다 해보자"는 사용자 요청으로 착수(순서는 위임받아 직접 정함, 찜·오픈예정 다음 세 번째). 새 평판 테이블을 만들지 않고 이미 있는 리뷰 데이터만으로 계산하는 쪽으로 스코프를 좁힘(design.md 참고).

## 설계

`docs/dev/product/seller-trust/design.md` 참고 — 판매자 단위로 리뷰 평균 평점·개수를 집계해 평균≥4.5·개수≥3이면 배지 노출. 목록/상세 캐시 안에 그대로 포함(30분 TTL 감수).

## 태스크

- [x] `SellerRatingProjection`/`SellerRatingProjectionImpl` (QueryDSL 생성자 프로젝션)
- [x] `ReviewRepositoryCustom.findSellerRatingSummaries()` + `ReviewRepositoryImpl`
- [x] `ProductService.trustedSellerMap()`/`isTrustedSeller()` — list/detail/register/update 전부 반영
- [x] `ProductResponse`/`ProductSummaryResponse`에 `sellerTrustedBadge` 노출
- [x] 메인 페이지 카드에 "신뢰 판매자" 배지
- [x] 상품 상세 페이지 판매자명 옆 배지
- [x] 테스트: 기준 충족 true, 리뷰 부족 false, 리뷰 없음 false, 목록 응답 포함 각각
- [x] `docs/api/product.md` 갱신

## 평가(통과) 기준

- `./gradlew test` 통과
- 로컬 실서버로 리뷰 3개(평점 5) 남긴 판매자 상품 → 목록/상세에 배지 노출 실측, 리뷰 2개짜리는 배지 없음 실측
