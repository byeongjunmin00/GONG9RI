# 006-member-profile-image-upload — 회원 프로필 사진 변경 및 삭제 기능 구현 (로그)

## Attempt 1 — 2026-08-21  ✅ PASS

- 시도: 회원(`Member`) 프로필 사진(아바타 이미지) 업로드 및 삭제 기능 구현.
  - `Member.java` & `MemberResponse.java`:
    - `@Column(length = 500) private String profileImageUrl;` 추가 및 `updateProfileImage()` 도메인 메서드 작성.
  - `MemberProfileImageController.java` & `MemberService.java`:
    - `POST /api/member/profile-image`: 5MB 이하 축소 JPEG 인코딩 저장(`ProductImageStorage`) 후 URL 반환.
    - `DELETE /api/member/profile-image`: `profileImageUrl = null` 초기화.
  - `buyer/mypage.html`, `seller/mypage.html`, `js/account-info.js`:
    - 계정 정보 탭에 [사진 변경], [삭제] 버튼 및 미리보기 추가.
    - 마이페이지 상단 프로필 배너 아바타 카드에 사용자 프로필 이미지 실시간 반영.
  - `MemberProfileImageControllerTest.java`:
    - `uploadProfileImage_success()`, `deleteProfileImage_success()` 통합 테스트 구현.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.MemberProfileImageControllerTest` → `BUILD SUCCESSFUL in 14s`.
- 추론적 평가:
  - 회원이 프로필 사진을 자유롭게 업로드하고 삭제/초기화할 수 있는 안전한 이미지 업로드 파이프라인 및 UI/UX 환경 구축 완료.
- 증거:
  - `./gradlew test --tests MemberProfileImageControllerTest` → `BUILD SUCCESSFUL`.

## Attempt 2 — 2026-08-21  ✅ PASS (리뷰 보완 — 테스트 커버리지)

- 시도: 코드 리뷰 중 이 컨트롤러에 비로그인(401) 케이스 테스트가 없는 것을 확인(다른 마이페이지 엔드포인트들은 전부 `_unauthorized` 테스트를 가짐) — 추가. 파일 검증(확장자 위장/용량 초과/빈 파일 등)은 이미 `ProductImageStorageTest`에서 서비스 레벨로 충분히 커버되고 있어 컨트롤러 레벨 중복 작성은 생략.
  - `MemberProfileImageControllerTest.java`: `uploadProfileImage_unauthorized`, `deleteProfileImage_unauthorized` 추가.
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.MemberProfileImageControllerTest` → `BUILD SUCCESSFUL in 15s` (4개 테스트 전체 통과).

## Attempt 3 — 2026-08-21  ✅ PASS (리뷰 발견 버그 수정 + 회귀 테스트)

- 시도: 리뷰에서 발견된 버그 2건 수정.
  - `ProductImageStorage.delete(String url)` 신설 — `store()`와 대칭. `/uploads/` 접두사가 아니거나 정규화 후 저장 루트를 벗어나는 URL은 조용히 무시(경로 탈출 방지). `MemberService.updateProfileImage()`/`deleteProfileImage()`가 새 값 반영 전 이전 `profileImageUrl` 파일을 삭제하도록 수정 — 이전엔 사진을 바꾸거나 지워도 디스크의 예전 파일이 안 지워져 고아 파일이 쌓이던 문제(안티그래비티가 수정, 이 세션이 검증).
  - `account-info.js`의 `updateProfileAvatar()`가 `innerHTML` 문자열 조합 대신 `createElement('img')` + `.src=`로 아바타를 그리도록 수정 — 당장 뚫리는 값은 아니었지만 코드베이스 나머지(예: `seller-mypage.js`)와 다른 패턴이라 통일(안티그래비티가 수정).
  - `MemberProfileImageController`의 도달 불가능한 `principal == null` 수동 체크 제거 — `SecurityConfig`가 이미 이 경로를 인증 필수로 막고 있어 죽은 코드였음(안티그래비티가 수정).
  - `ProductImageStorageTest.java`: `delete_removesStoredFile`, `delete_ignoresInvalidOrTraversalUrls`, `delete_doesNotThrowWhenFileAlreadyGone` 추가(신규 `delete()` 메서드 자체의 테스트가 없었음).
  - `MemberProfileImageControllerTest.java`: `uploadProfileImage_replacingDeletesOldFile` 추가 — 두 번째 업로드 후 첫 번째 파일이 실제로 디스크에서 지워지는지 검증(교체 시 삭제 동작 자체를 검증하는 테스트가 없었음).
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.MemberProfileImageControllerTest --tests com.gong9ri.gong9ri.service.ProductImageStorageTest` → `BUILD SUCCESSFUL in 16s` (5개 + 10개 테스트 전체 통과).

## Attempt 4 — 2026-08-21  ✅ PASS (로컬 실기동 검증 중 발견한 버그 수정)

- 시도: `./gradlew bootRun`으로 로컬에 실제로 띄워서(MySQL/Redis는 기존 docker 그대로) 브라우저로 직접 업로드/삭제를 눌러보다 발견한 버그 수정.
  - 재현: 로그인 → 사진 업로드(200 OK, 새 URL 응답) → 같은 세션으로 `GET /api/auth/me` 호출 → **여전히 이전 `profileImageUrl`을 반환**. 재로그인해야 새 값이 보임. DB에는 새 값이 정확히 저장돼 있어 영속성 문제가 아니라 세션 문제로 확인.
  - 원인: 세션의 `SecurityContext`가 들고 있는 principal은 로그인 시점 스냅샷이라, DB만 바꾸고 세션을 안 갱신하면 `GET /me`가 예전 값을 계속 보여준다 — `AuthController.updateMe()`가 이미 이 문제를 알고(주석 남아있음) 매번 `SecurityContextHolder`를 새 `MemberUserDetails`로 교체하는데, `MemberProfileImageController`는 신규 컨트롤러라 이 패턴을 안 따라서 재발.
  - 수정: `MemberProfileImageController`의 업로드/삭제 성공 후 `AuthController.updateMe()`와 동일하게 `SecurityContextHolder`를 새 `MemberUserDetails`로 교체 + `securityContextRepository.saveContext()` 호출.
  - `MemberProfileImageControllerTest.java`: `uploadProfileImage_refreshesSessionPrincipalImmediately`, `deleteProfileImage_refreshesSessionPrincipalImmediately` 추가 — 실제 로그인 세션(`MockHttpSession`)으로 업로드/삭제 직후 같은 세션에서 `GET /api/auth/me`를 호출해 새 값이 바로 보이는지 검증(재로그인 없이).
- 결과: ✅ **PASS**
- 계산적 평가:
  - `./gradlew test --tests com.gong9ri.gong9ri.controller.MemberProfileImageControllerTest` → `BUILD SUCCESSFUL in 18s` (7개 테스트 전체 통과).
  - `./gradlew test` 전체 실행 시 무관한 사전 데이터(제품 2건, 오늘 오전 다른 세션이 등록)로 인한 `AdminControllerTest`/`ProductControllerTest`/`ProductCachingTest` 9건 실패 확인 — 이 기능과 무관, 조치하지 않음.
- 증거:
  - 로컬 서버(`localhost:8080`)에서 실제 로그인 → 업로드 → `GET /api/auth/me` 호출로 재로그인 없이 새 `profileImageUrl` 확인.
