# 상품 검색(keyword) + 달성% 배지

대상: product/list-enhancements
담당: 민병준

## 배경 / 요구

친구가 참고로 보여준 와디즈·텀블벅 화면을 보고, 검색창이 빠져있다는 걸 확인 — "상품명이랑 판매자명 둘 다 검색되게" 요청.

## 설계

- `GET /api/products?keyword=` — `product.name` 또는 `product.seller.name`에 매치(OR, 대소문자 무시).
- 검색어가 있으면 목록 캐시(`PRODUCT_LIST_CACHE`)를 완전히 건너뛴다(`@Cacheable(condition=...)`) — 검색어 조합이 무한해 캐시 키로 쓰면 대부분 한 번만 쓰이는 엔트리로 캐시가 계속 불어난다.
- 카드 진행바 옆에 "N% 달성" 배지 추가(참고 사이트들의 공통 패턴).

## 태스크

- [x] `ProductRepositoryImpl` keyword OR 조건(product.name / seller.name)
- [x] `ProductService.list()` `condition` SpEL로 캐시 스킵
- [x] `ProductController` `keyword` 쿼리파라미터
- [x] 메인 페이지 검색창 UI, 카테고리/정렬과 함께 URL 동기화
- [x] 카드에 달성% 배지
- [x] 테스트: 상품명/판매자명 검색, 캐시 스킵(같은 파라미터 연속 조회해도 새 상품 즉시 반영) 검증
- [x] `docs/api/product.md` 갱신

## 평가(통과) 기준

- `./gradlew test` 통과
- 로컬 실서버로 상품명 검색·판매자명 검색·캐시 스킵 전부 실측 확인
- 사용자가 실제 브라우저에서 검색창 동작 확인
