# 구매자/판매자 마이페이지 조회

대상: mypage/view
담당: 민병준

## 배경 / 요구

마지막 핵심 기능. `docs/api/mypage.md`에 확정된 5개 GET 엔드포인트(구매내역/공구참여/등록상품/매출집계/판매자공구현황)를 구현한다. 쓰기 동작이 없는 순수 조회/집계라 신규 엔티티·에러코드는 필요 없다.

## 설계

- DTO 5개(record + `from(entity)`): `PurchaseResponse`, `BuyerTeamResponse`, `SellerProductResponse`, `RevenueResponse`, `SellerTeamResponse`
- Repository: `PaymentRepository`(member 범위 fetch-join + seller 매출 집계), `TeamParticipationRepository`(member 범위 fetch-join), `GroupBuyTeamRepository`(seller 범위 fetch-join), `ProductRepository`(seller 범위 단순 조회)
- `TeamParticipation`/`Product` 엔티티에 문서에만 있던 인덱스(`idx_member`, `idx_seller`) 추가
- `BuyerMypageController`/`BuyerMypageService`, `SellerMypageController`/`SellerMypageService`로 분리
- 매출 집계는 CASE WHEN 조건부 SUM/COUNT 한 쿼리로 처리(PAID만 합산, REFUNDED 제외)
- `docs/api/mypage.md` 필드명 정규화(`teamStatus`→`status`)

## 태스크
- [x] `TeamParticipation`/`Product` 엔티티 인덱스 추가
- [x] Repository 메서드 추가 4곳 + `RevenueSummaryProjection` 신규
- [x] DTO 5개
- [x] `BuyerMypageService`/`Controller`, `SellerMypageService`/`Controller`
- [x] `docs/api/mypage.md` 필드명 수정 + `docs/db/{group_buy_team,product}.md` 사용기능 목록 보강
- [x] 테스트 (`BuyerMypageControllerTest`, `SellerMypageControllerTest`)
- [x] `SHOW INDEX`로 인덱스 반영 확인
- [x] `./gradlew build` 통과 확인

## 평가(통과) 기준

- 5개 엔드포인트: 정상/FORBIDDEN/UNAUTHORIZED
- 스코핑 테스트(buyer: member.id 축, seller: product.seller.id 축) 각 최소 1개
- 매출 집계: PAID/REFUNDED 섞인 데이터로 금액·건수 정확성 검증
- 인덱스 2개 실제 DB 반영 확인
- `./gradlew build` 전체 통과
