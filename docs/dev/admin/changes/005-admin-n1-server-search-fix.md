# 관리자 회원/상품 N+1 쿼리 해결 및 서버 사이드 검색·필터링 구현

대상: backend/frontend/admin
담당: 전용운

## 배경 / 문제점

1. **🔴 회원 목록 N+1 쿼리 발생 (성능 버그)**:
   - `AdminService.java`에서 회원 20명을 조회할 때마다 회원 한 명당 3개의 `countBy...` 쿼리를 루프 안에서 개별 호출하여, 한번 페이지를 열 때마다 61개(1 + 20×3)의 쿼리가 실행됐다.
   - `ProductService`의 `reviewStatMap` / `bestPrices` 배치 조회 패턴(IN 절로 쿼리 1방에 집계)을 도입하여 N+1 쿼리를 완전히 해결했다.

2. **🔴 검색/필터의 클라이언트 메모리 바운드 버그**:
   - `admin-members.js` 및 `admin-products.js`에서 검색과 필터링이 서버 API가 아니라 클라이언트 메모리(`allMembers`/`allProducts`)에서만 실행되어, 20명 초과 데이터나 미로드 페이지 회원은 검색되지 않았다.
   - 서버 API(`GET /api/admin/members`, `GET /api/admin/products`)에 검색어(`search`), 역할(`role`), 정지 여부(`suspended`), 상품 상태(`status`) 파라미터를 추가하고 서버 DB 페이징 쿼리로 동작하게 재구현했다.

## 설계 및 구현 내용

### 1. N+1 쿼리 해결 (Batch GROUP BY 쿼리 도입)
- **Repository Batch 쿼리 추가**:
  - `PaymentRepository`: `@Query("SELECT p.member.id, COUNT(p) FROM Payment p WHERE p.member.id IN :memberIds GROUP BY p.member.id") List<Object[]> countPaymentsByMemberIds(@Param("memberIds") List<Long> memberIds);`
  - `TeamParticipationRepository`: `@Query("SELECT tp.member.id, COUNT(tp) FROM TeamParticipation tp WHERE tp.member.id IN :memberIds GROUP BY tp.member.id") List<Object[]> countParticipationsByMemberIds(@Param("memberIds") List<Long> memberIds);`
  - `ProductRepository`: `@Query("SELECT p.seller.id, COUNT(p) FROM Product p WHERE p.seller.id IN :sellerIds GROUP BY p.seller.id") List<Object[]> countProductsBySellerIds(@Param("sellerIds") List<Long> sellerIds);`
- **AdminService 배치 매핑**:
  - 20명 회원의 `memberId` 리스트를 뽑아 위의 3개 쿼리를 1번씩만 호출(총 3회) 후 `Map<Long, Integer>`에 담아 O(1)로 매핑.
  - 결과: 회원 20명 조회 시 쿼리 **61회 -> 4회**로 대폭 축소 (N+1 문제 완벽 해결).

### 2. 서버 사이드 페이징 검색 & 필터링 구현
- **백엔드 API 스키마 확장**:
  - `GET /api/admin/members?page=0&size=20&search=keyword&role=BUYER|SELLER|ADMIN&suspended=true|false`
  - `GET /api/admin/products?page=0&size=20&search=keyword&status=VISIBLE|HIDDEN|PUSH`
- **QueryDSL 동적 쿼리 구현**:
  - `MemberRepositoryCustom` & `MemberRepositoryImpl` 추가: `findAllForAdmin(Pageable pageable, String search, Role role, Boolean suspended)`
    - `search` 조건: `username.containsIgnoreCase(search)` OR `name.containsIgnoreCase(search)` OR `email.containsIgnoreCase(search)`
  - `ProductRepositoryImpl.findAllForAdmin` 확장: `findAllForAdmin(Pageable pageable, String search, String status)`
    - `search` 조건: `name.containsIgnoreCase(search)` OR `seller.name.containsIgnoreCase(search)`
    - `status` 조건: `hidden=true` (`HIDDEN`), `hidden=false` (`VISIBLE`), 추천 푸시 조건 (`PUSH`)
- **프론트엔드 API 연동 재구현 (`admin-members.js`, `admin-products.js`)**:
  - 클라이언트 사이드 `filterMembersList()`, `filterProductsList()` 제거.
  - 검색어 입력(디바운스 적용) 또는 필터 탭 클릭 시 `state.page = 0`으로 초기화 후 query string을 붙여 `GET /api/admin/members?search=...` 서버 API 즉시 요청.

## 변경된 파일 목록

- `src/main/java/com/gong9ri/gong9ri/repository/PaymentRepository.java`: Batch 카운트 쿼리 추가
- `src/main/java/com/gong9ri/gong9ri/repository/TeamParticipationRepository.java`: Batch 카운트 쿼리 추가
- `src/main/java/com/gong9ri/gong9ri/repository/ProductRepository.java`: Batch 카운트 쿼리 추가
- `src/main/java/com/gong9ri/gong9ri/repository/MemberRepositoryCustom.java`: 관리자 회원 QueryDSL 인터페이스 추가
- `src/main/java/com/gong9ri/gong9ri/repository/MemberRepositoryImpl.java`: 관리자 회원 QueryDSL 동적 쿼리 구현
- `src/main/java/com/gong9ri/gong9ri/repository/ProductRepositoryCustom.java`: 오버로딩 인터페이스 추가
- `src/main/java/com/gong9ri/gong9ri/repository/ProductRepositoryImpl.java`: 관리자 상품 QueryDSL 동적 쿼리 구현
- `src/main/java/com/gong9ri/gong9ri/service/AdminService.java`: N+1 해결 Map 매핑 및 파라미터 전달
- `src/main/java/com/gong9ri/gong9ri/controller/AdminController.java`: search, role, suspended, status 파라미터 수신
- `src/main/resources/static/js/admin-members.js`: 서버 사이드 페이징/검색/필터 API 연동
- `src/main/resources/static/js/admin-products.js`: 서버 사이드 페이징/검색/필터 API 연동
- `docs/api/admin.md`: 명세 갱신
- `docs/dev/admin/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/admin/005-admin-n1-server-search-fix.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava` 빌드 성공.
- 회원 20명 로드 시 쿼리 61개 -> 4개로 대폭 절감되어 N+1 회피 확인.
- 미로드 페이지의 회원/상품도 서버 DB 검색 쿼리를 통해 정확히 페이징 결과를 반환하는 것 확인.
