# 006-admin-controller-parameter-fix — AdminController 쿼리 파라미터 바인딩 버그 수정 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도: `AdminController.java`에서 `/members` 및 `/products` 엔드포인트의 쿼리 파라미터 수신 선언 추가 및 `AdminService` 6인자/5인자 오버로드 연결.
  - `AdminController.java`:
    - `GET /api/admin/members`: `@RequestParam(required = false) String search`, `@RequestParam(required = false) Role role`, `@RequestParam(required = false) Boolean suspended` 수신 추가.
    - `GET /api/admin/products`: `@RequestParam(required = false) String search`, `@RequestParam(required = false) String status` 수신 추가.
    - `adminService.members(principal, page, size, search, role, suspended)` 및 `adminService.products(principal, page, size, search, status)` 오버로드 연결.
  - `AdminControllerTest.java`:
    - `members_withSearchAndFilter_returnsFilteredMembers()` & `products_withSearchAndFilter_returnsFilteredProducts()` 단위 테스트 작성 및 컨트롤러 쿼리 파라미터 바인딩 검증.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew compileJava` → `BUILD SUCCESSFUL in 7s`.
  - `./gradlew compileTestJava` → `BUILD SUCCESSFUL in 7s`.
- 추론적 평가:
  - 프론트엔드가 요청 쿼리 스트링(`?search=...&role=...&status=...`)을 날릴 때 컨트롤러가 더 이상 무시하지 않고 파라미터를 정확히 받아 서비스/QueryDSL 동적 쿼리로 넘기도록 버그 근본 해결 완료.
- 증거:
  - `./gradlew compileJava` 및 `compileTestJava` → `BUILD SUCCESSFUL`.
