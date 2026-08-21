# 관리자 회원 관리 & 상품 현황 종합 정보 및 인사이트 개편

대상: frontend/admin
담당: 전용운

## 배경 / 요구

현재 관리자의 회원 관리(`admin/members.html`) 및 상품 현황(`admin/products.html`) 페이지는 단순 텍스트 나열로 되어 있어 관리가 매우 어려웠다.
- **회원 관리**: 해당 회원이 플랫폼에서 어떤 활동(구매, 공구 참여, 상품 등록)을 했는지 종합 정보를 확인할 수 없으며 검색/필터링 기능이 부족했다.
- **상품 현황**: 어떤 상품이 잘 팔리고 있어 관리가 푸시(추천)를 해주어야 하는지, 혹은 어떤 상품을 제재(숨김/삭제)해야 하는지 한눈에 들어오지 않았다.

회원의 종합 활동 수치(구매/공구/등록상품 건수)와 상품의 성과 지표(결제 건수, 리뷰 평점, 활성 팀 진행률)를 시각적으로 차별화하고 관리자 인사이트 배지(추천 vs 제재)를 적용하여 업무 효율성을 극대화했다.

## 설계 및 구현 내용

### 1. 회원 관리 (`admin/members.html`, `js/admin-members.js`)
- **회원 종합 활동 수치(Metrics) 노출**:
  - `AdminMemberResponse` DTO에 `purchaseCount`, `teamCount`, `productCount` 필드 추가 및 백엔드 집계 연동.
  - 회원 목록 카드마다 구매 건수(`🛍️ 구매 N건`), 참여 공구팀 수(`👥 공구 N건`), 등록 상품 수(`📦 상품 N개`) 배지 표시.
- **회원 카드 UI 비주얼 강화**:
  - 아바타 썸네일 아이콘, 회원 이름/아이디, 이메일, 가입일자, 역할 배지(`BUYER`/`SELLER`/`ADMIN`), 상태 배지(`정상`/`정지됨`) 적용.
- **검색 및 역할/상태 필터링**:
  - 상단 필터 탭(전체, 구매자, 판매자, 정지된 회원) 및 이름/이메일/아이디 실시간 검색 입력창 추가.

### 2. 상품 현황 (`admin/products.html`, `js/admin-products.js`)
- **상품 카드 썸네일 & 정보 시각화**:
  - 실제 상품 썸네일(`imageUrl`) 노출, 카테고리 배지, 기본가, 판매자명, 등록일 표시.
- **관리자 푸시(Push) & 제재(Sanction) 인사이트 배지 도입**:
  - 🚀 **추천/인기 푸시 대상 배지**: 리뷰 평점이 높은 유수 상품 또는 활성 공구팀 달성률(50% 이상)이 높아 푸시 지원이 필요한 우수 상품에 '인기/추천' 배지 부여.
  - ⚠️ **제재/주의 대상 배지**: 현재 관리자 숨김 상태(`hidden=true`)이거나 조치가 필요한 상품에 '숨김/제재' 배지 명확히 노출.
- **상품 인사이트 메트릭스 노출**:
  - `ProductService.listForAdmin()`에서 리뷰 평점/개수 (`ratingAverage`, `reviewCount`) 및 활성 팀 진행률(`attachActiveTeamProgress`)을 attach하여 프론트에 전달.
- **검색 및 상태 필터링**:
  - 상단 필터 탭(전체, 공개 상품, 숨김 상품, 추천 푸시) 및 상품명/판매자명 검색창 추가.

## 변경된 파일 목록

- `src/main/resources/static/admin/members.html`: 검색창, 필터 탭, 아바타/활동 수치 배지 포함 회원 카드 마크업 개편
- `src/main/resources/static/js/admin-members.js`: 검색/필터링, 아바타/활동 수치 배지 렌더링
- `src/main/resources/static/admin/products.html`: 검색창, 필터 탭, 썸네일/인사이트 배지 포함 상품 카드 마크업 개편
- `src/main/resources/static/js/admin-products.js`: 검색/필터링, 썸네일/인사이트 배지 렌더링
- `src/main/java/com/gong9ri/gong9ri/dto/AdminMemberResponse.java`: 활동 수치 필드 추가
- `src/main/java/com/gong9ri/gong9ri/dto/AdminMemberPageResponse.java`: Page/List 팩토리 메서드 추가
- `src/main/java/com/gong9ri/gong9ri/service/AdminService.java`: 회원 활동 수치 매핑
- `src/main/java/com/gong9ri/gong9ri/service/ProductService.java`: `listForAdmin` 리뷰 통계 및 활성 팀 진행률 attach
- `src/main/java/com/gong9ri/gong9ri/repository/PaymentRepository.java`: `countByMemberId` 추가
- `src/main/java/com/gong9ri/gong9ri/repository/TeamParticipationRepository.java`: `countByMember_Id` 추가
- `src/main/java/com/gong9ri/gong9ri/repository/ProductRepository.java`: `countBySeller_Id` 추가
- `docs/api/admin.md`: 명세 갱신
- `docs/dev/admin/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/admin/004-admin-members-products-redesign.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava` 및 `./gradlew compileTestJava` 빌드 검증 성공.
- 회원 활동 수치 배지, 검색/필터링, 상품 썸네일, 🚀푸시 vs ⚠️제재 인사이트 배지 정상 동작 확인.
