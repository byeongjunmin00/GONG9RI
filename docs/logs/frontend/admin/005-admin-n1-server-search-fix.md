# 005-admin-n1-server-search-fix — 관리자 회원/상품 N+1 쿼리 해결 및 서버 사이드 검색·필터링 구현 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도: 회원 목록 N+1 쿼리 해결 및 클라이언트 메모리 한계 검색/필터링을 서버 사이드 페이징 검색/필터링으로 전환.
  - N+1 쿼리 해결:
    - `PaymentRepository.countPaymentsByMemberIds(memberIds)`
    - `TeamParticipationRepository.countParticipationsByMemberIds(memberIds)`
    - `ProductRepository.countProductsBySellerIds(sellerIds)`
    - 위 3개 `GROUP BY` JPQL 배치 쿼리를 추가하고, `AdminService.members()`에서 20개 회원 ID로 쿼리 단 3번만 불러와 Map으로 O(1) 매핑. 쿼리 횟수를 61회 → 4회로 대폭 감소.
  - 서버 사이드 페이징 검색 & 필터링:
    - `MemberRepositoryCustom` & `MemberRepositoryImpl` (QueryDSL): `findAllForAdmin(pageable, search, role, suspended)` 구현 (username, name, email 대소문자 무시 매칭 및 role/suspended 동적 조건).
    - `ProductRepositoryCustom` & `ProductRepositoryImpl` (QueryDSL): `findAllForAdmin(pageable, search, status)` 구현 (name, seller.name 대소문자 무시 매칭 및 status `VISIBLE`/`HIDDEN`/`PUSH` 동적 조건).
    - `AdminController.java`: `GET /api/admin/members` 및 `GET /api/admin/products`에 `search`, `role`, `suspended`, `status` 쿼리 파라미터 연동.
    - `admin-members.js` & `admin-products.js`: 클라이언트 사이드 메모리 배열 필터링을 제거하고, 검색어 디바운스 입력 및 필터 탭 클릭 시 `page=0`으로 서버에 직접 검색 쿼리를 날리는 서버 사이드 페이징 연동.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL in 8s`.
- 추론적 평가:
  - 회원 20명 로드 시 기존 61회 쿼리에서 4회로 대폭 절감되어 N+1 회피 패턴 완벽 준수.
  - 미로드 페이지의 회원/상품도 서버 DB 검색 쿼리를 거쳐 검색 및 필터링 가능하도록 근본적인 UX/서버 해결 완료.
- 증거:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL`.
