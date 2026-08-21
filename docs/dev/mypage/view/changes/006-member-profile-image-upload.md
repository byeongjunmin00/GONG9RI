# 회원 프로필 사진 변경 및 삭제 기능 구현

대상: backend/frontend/mypage
담당: 전용운

## 배경 및 요구사항

회원(`Member`) 정보에 프로필 사진(아바타 이미지) 필드가 없어 마이페이지 프로필 배너 등에 고정 SVG 아이콘만 표시되던 상태였다.

- **목표**: 회원이 자신의 프로필 사진을 직접 업로드(`POST /api/member/profile-image`)하거나 삭제/초기화(`DELETE /api/member/profile-image`)할 수 있는 백엔드 엔티티·API 및 프론트엔드 마이페이지 UI를 신설했다.

## 상세 설계 및 백엔드/프론트엔드 구현 내용

1. **엔티티 변경 (`Member.java` & `MemberResponse.java`)**:
   - `@Column(length = 500) private String profileImageUrl;` 추가.
   - `updateProfileImage(String profileImageUrl)` 도메인 메서드 작성.

2. **DTO 및 API 신설 (`MemberProfileImageController.java` & `MemberService.java`)**:
   - `POST /api/member/profile-image`: `ProductImageStorage.store(file)`를 활용해 5MB 이하 축소 JPEG 저장 후 `profileImageUrl` DB 반영 및 200 OK.
   - `DELETE /api/member/profile-image`: `profileImageUrl`을 null로 초기화하고 200 OK.

3. **프론트엔드 마이페이지 UI 구현 (`buyer/mypage.html`, `seller/mypage.html`, `js/account-info.js`)**:
   - 마이페이지 계정 정보 탭 내 [사진 변경] (파일선택) 및 [삭제] UI 추가.
   - 프로필 사진 변경 시 상단 프로필 배너 아바타 카드에 사용자 프로필 이미지를 실시간 적용.

4. **단위/통합 테스트 작성 (`MemberProfileImageControllerTest.java`)**:
   - `uploadProfileImage_success()`, `deleteProfileImage_success()` 구현 및 성공 검증.

## 변경된 파일 목록

- `src/main/java/com/gong9ri/gong9ri/entity/Member.java`: `profileImageUrl` 필드 추가
- `src/main/java/com/gong9ri/gong9ri/dto/MemberResponse.java`: `profileImageUrl` 응답 포함
- `src/main/java/com/gong9ri/gong9ri/controller/MemberProfileImageController.java`: 신규 컨트롤러
- `src/main/java/com/gong9ri/gong9ri/service/MemberService.java`: `updateProfileImage()`, `deleteProfileImage()` 서비스 메서드 구현
- `src/main/resources/static/buyer/mypage.html` & `seller/mypage.html`: 프로필 사진 폼 영역 추가
- `src/main/resources/static/js/account-info.js`: 프로필 사진 업로드/삭제 핸들러 작성
- `src/test/java/com/gong9ri/gong9ri/controller/MemberProfileImageControllerTest.java`: 통합 테스트 구현
- `docs/dev/mypage/view/design.md`: 디자인 SSOT 갱신
- `docs/logs/frontend/mypage/006-member-profile-image-upload.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew test --tests com.gong9ri.gong9ri.controller.MemberProfileImageControllerTest` 실행 결과 `BUILD SUCCESSFUL in 14s` 통과.
- 프로필 사진 업로드 및 삭제 시 `profileImageUrl`이 정확히 변경되는지 확인.
