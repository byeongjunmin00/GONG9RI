# 마감임박 정렬 + 카드 배지

대상: product/list-enhancements
담당: 민병준

## 배경 / 요구

와디즈/텀블벅 참고 화면 검토 중 "마감임박" 탭/표시를 확인, "욕심나는 것들 싹 다 해보자"는 사용자 요청으로 먼저 착수. 나머지(찜, 판매자 신뢰 배지, 실시간 인기 검색어, 오픈예정 상태)는 각각 스코프가 커서 별도 진행 예정.

## 설계

- `GET /api/products?sort=DEADLINE` — RECRUITING 팀 중 가장 이른 마감일(`MIN(deadline)` 상관 서브쿼리) 오름차순. `OrderSpecifier.nullsLast()`로 진행 중인 팀 없는 상품을 맨 뒤로 보낸다(MySQL ASC 기본 동작은 NULL이 앞으로 와서 그대로 두면 의도와 반대가 됨).
- `ProductSummaryResponse.activeTeamDeadline` 추가 — 이미 진행바용으로 고르고 있던 "대표 팀"(진행률 최고)의 마감일을 그대로 재사용(추가 쿼리 없음).
- 프론트: 마감까지 3일 이하로 남았을 때만 카드 이미지에 "N일 남음"/"오늘 마감" 배지 노출.

## 태스크

- [x] `ProductSort.DEADLINE` + 상관 서브쿼리 정렬(nullsLast)
- [x] `ProductSummaryResponse.activeTeamDeadline` 추가
- [x] 정렬 select에 "마감임박순" 옵션
- [x] 카드에 마감임박 배지(3일 이하만)
- [x] 테스트: 마감임박순 정렬(팀 없는 상품 맨 뒤 포함) 검증
- [x] `docs/api/product.md` 갱신

## 평가(통과) 기준

- `./gradlew test` 통과
- 로컬 실서버로 마감임박순 정렬 + 카드 배지 실측 확인
