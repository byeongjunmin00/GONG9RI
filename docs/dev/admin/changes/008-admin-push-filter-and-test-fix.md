# 관리자 추천 푸시(PUSH) DB 필터링 정밀화 및 AdminControllerTest 실측 단언 강화

대상: backend/admin
담당: 전용운

## 배경 및 원인 분석

1. **🔴 "추천 푸시" 필터 가짜 동작 해결**:
   - `ProductRepositoryImpl.java`의 `findAllForAdmin`에서 `status`가 `"PUSH"`일 때 기존에는 단순히 `product.hidden.isFalse()`로만 처리되어 `VISIBLE`과 차이 없이 전체 공개 상품이 반환되던 버그가 있었음.
   - 실제 추천/인기 푸시 기준(평점 4.5 이상 또는 활성 공구팀 진행률 50% 이상)이 QueryDSL DB 쿼리 레벨에 반영되도록 서브쿼리를 구현함.

2. **🔴 `AdminControllerTest` 단언 강화**:
   - `products_withSearchAndFilter_returnsFilteredProducts()` 테스트에서 응답의 `$.data.content`가 배열인지만 체크하던 부실함을 해결하여, 매칭 결과 개수 및 상품명 단언을 추가하고 `status=HIDDEN`, `status=PUSH` 각각의 필터링 정합성을 검증하도록 테스트 케이스 작성.

## 해결 및 구현 내용

1. **`ProductRepositoryImpl.java` PUSH 조건 정밀화 (QueryDSL 서브쿼리 적용)**:
   - `status == "PUSH"` 요청 시 `product.hidden.isFalse()`와 함께 아래 조건 중 하나 이상을 만족하는 상품만 DB 레벨에서 정교하게 필터링:
     - **평점 조건**: 리뷰 평균 점수가 4.5 이상 (`QReview` avg >= 4.5)
     - **활성 팀 진행률 조건**: RECRUITING 상태인 공구팀 중 참여인원이 목표의 50% 이상 (`QGroupBuyTeam` `currentCount * 2 >= maxParticipants`)

2. **`AdminControllerTest.java` 테스트 단언 강화**:
   - `products_withSearchAndFilter_returnsFilteredProducts`: `content.length() == 1` 및 `content[0].name` 단언 추가.
   - `products_withHiddenStatusFilter_returnsOnlyHiddenProducts`: `status=HIDDEN` 파라미터 시 숨김 상품만 반환되는지 단언.
   - `products_withPushStatusFilter_returnsOnlyPushCandidates`: `status=PUSH` 파라미터 시 평점 4.5 이상인 추천 푸시 대상 상품만 정교하게 반환되는지 단언.

## 변경된 파일 목록

- `src/main/java/com/gong9ri/gong9ri/repository/ProductRepositoryImpl.java`: `status=PUSH` QueryDSL 서브쿼리 필터링 정밀화
- `src/test/java/com/gong9ri/gong9ri/controller/AdminControllerTest.java`: 상품 검색, HIDDEN, PUSH 필터 실측 단언 강화
- `docs/dev/admin/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/admin/008-admin-push-filter-and-test-fix.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava compileTestJava` 빌드 검증 성공.
- `PUSH` 필터링 요청 시 QueryDSL 서브쿼리를 거쳐 평점 4.5 이상 또는 팀 진행률 50% 이상 상품만 필터링되도록 처리 완료.
- `AdminControllerTest` 단언 강화 완료.
