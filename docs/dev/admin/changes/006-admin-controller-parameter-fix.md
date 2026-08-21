# 관리자 AdminController 검색·필터 쿼리 파라미터 미수신 버그 수정

대상: backend/admin
담당: 전용운

## 배경 및 원인 분석

이전 작업에서 `AdminService`, `MemberRepositoryImpl`, `ProductRepositoryImpl` 및 프론트엔드(`admin-members.js`, `admin-products.js`)에 서버 사이드 페이징/검색/필터링 로직을 구현했으나, **`AdminController.java`에서 쿼리 파라미터를 수신하는 엔드포인트 파라미터 연결이 누락**되었다.

- **증상**: 프론트엔드는 `?search=...&role=...` 및 `?search=...&status=...`를 정상 전송하나, 스프링 컨트롤러가 이를 수신하지 않고 구버전 3인자 메서드(`adminService.members(principal, page, size)`)를 부르기 때문에 쿼리 파라미터가 무시되어 매번 기본 20개 전체 목록만 반환됐다.
- **원인**: `AdminController.java`의 `@GetMapping("/members")` 및 `@GetMapping("/products")` 메서드에 `@RequestParam(required = false)` 파라미터 수신 선언 누락.

## 해결 및 구현 내용

1. **`AdminController.java` 엔드포인트 파라미터 연동**:
   - `GET /api/admin/members`:
     `search` (String, required = false), `role` (Role, required = false), `suspended` (Boolean, required = false) 3개 파라미터 수신 추가.
     `adminService.members(principal, page, size, search, role, suspended)` 6인자 오버로드 호출.
   - `GET /api/admin/products`:
     `search` (String, required = false), `status` (String, required = false) 2개 파라미터 수신 추가.
     `adminService.products(principal, page, size, search, status)` 5인자 오버로드 호출.

2. **컨트롤러 단위 테스트 작성 및 검증 (`AdminControllerTest.java`)**:
   - `members_withSearchAndFilter_returnsFilteredMembers()` & `products_withSearchAndFilter_returnsFilteredProducts()` 테스트 작성.
   - 컨트롤러 쿼리 파라미터 전달 및 바인딩 정합성을 검증.

## 변경된 파일 목록

- `src/main/java/com/gong9ri/gong9ri/controller/AdminController.java`: `search`, `role`, `suspended`, `status` 쿼리 파라미터 연동
- `src/test/java/com/gong9ri/gong9ri/controller/AdminControllerTest.java`: 컨트롤러 파라미터 바인딩 검증 테스트 작성
- `docs/api/admin.md`: 명세 갱신
- `docs/dev/admin/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/admin/006-admin-controller-parameter-fix.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava` 및 `./gradlew compileTestJava` 빌드 검증 성공.
- 컨트롤러가 프론트엔드의 쿼리 파라미터를 정확히 수신하여 `AdminService` / `QueryDSL` 동적 쿼리로 바인딩함 확인.
