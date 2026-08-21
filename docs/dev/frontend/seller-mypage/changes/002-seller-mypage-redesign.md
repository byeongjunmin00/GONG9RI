# 판매자 마이페이지 가독성 및 UI/UX 개선

대상: frontend/seller-mypage
담당: 전용운

## 배경 / 요구

현재 판매자 마이페이지(`seller/mypage.html`)는 계정 정보·등록 상품 목록·수익 현황·공구 참여 현황·환불 요청 관리 5개 섹션이 세로로 길게 나열되어 있어 구매자 마이페이지와 같은 가독성 문제가 있었다.
특히 판매자는 **상품 관리(수정/삭제)**·**수익 파악**·**환불 처리(승인/거절)** 세 가지 핵심 업무를 하나의 화면에서 수행하므로 섹션 간 전환 비용이 크고, 수익 현황 카드(`revenue-cards`)는 현재 총 매출/건수를 단순 나열하는 데 그쳐 **판매자 관점의 인사이트**가 부족했다.

## 설계 및 구현 내용

### 1. 상단 판매자 프로필 & 핵심 수익 요약 카드
- 판매자 이름/이메일 노출 (`GET /api/auth/me` 연동)
- 핵심 KPI 4종을 한눈에: **총 매출**, **결제 완료 건수**, **환불 건수**, **대기 환불 건수**
  - 구매자 마이페이지와 동일한 `.mypage-profile-card` + `.mypage-summary-grid` 컴포넌트 패턴 재사용
  - 기존 `#revenue-cards` 섹션을 상단 프로필 옆으로 흡수하여 수익 및 대기 환불 정보가 즉각적으로 눈에 들어오게 개선
  - "대기 환불" 카드 클릭 시 [환불 관리] 탭으로 즉시 전환 지원

### 2. 탭(Tab) 네비게이션 구조 도입
- 구매자 마이페이지와 동일한 탭 컴포넌트(`.mypage-nav-tabs`, `.mypage-tab-btn`, `.mypage-tab-panel`) 적용
- 탭 구성:
  - `[전체 현황]` — 모든 섹션 표시
  - `[등록 상품]` — 등록 상품 목록 + 수정/삭제 액션
  - `[공구 현황]` — 공구 참여 현황 (모집중/성사/실패)
  - `[환불 관리]` — 환불 요청 관리 (대기/승인/거절)
  - `[계정 설정]` — 계정 정보 수정 폼
- URL hash 연동 (`#products`, `#teams`, `#refunds`, `#account`)

### 3. 등록 상품 & 공구 & 환불 카드 썸네일 실제 이미지 연동
- 백엔드 DTO 5종에 `Product.imageUrl` 필드 추가:
  - `SellerProductResponse`: 등록 상품 썸네일
  - `SellerTeamResponse`: 공구 현황 썸네일
  - `RefundRequestResponse`: 환불 요청 썸네일
  - `PurchaseResponse`: 구매 내역 썸네일 (구매자 마이페이지)
  - `BuyerTeamResponse`: 공구 참여 썸네일 (구매자 마이페이지)
- 이미지가 없는 상품의 경우 안전한 fallback SVG 아이콘 렌더링

### 4. 공구 현황 카드 — 진행률 프로그레스 바 & 잔여 시간 배지
- `RECRUITING` 상태 공구팀 카드에 `.team-progress` 게이지 적용
- `RECRUITING` 팀에 `.badge-time` 잔여 시간 배지(⏱️ N일 N시간 남음) 노출

### 5. 환불 관리 섹션 — 정보 구조 개선 & 긴급도 시각화
- `PENDING` 상태의 환불 요청에 처리 대기 배지 및 승인/거절 액션 노출
- 요청자명을 타이틀 라인(`.mypage-list-item__title`)으로 분리하고 상품명·금액·날짜·사유를 메타 줄로 깔끔하게 정리
- 승인/거절 처리 시 해당 카드 인플레이스 갱신 및 상단 대기 환불 카운터 실시간 차감

## 변경된 파일 목록

- `src/main/resources/static/seller/mypage.html`: 프로필 카드/수익 KPI 대시보드 마크업, 탭 바, 섹션 탭 패널화, `#revenue-cards` 제거
- `src/main/resources/static/js/seller-mypage.js`: 탭 전환, 프로필/KPI 로드, 썸네일 래퍼, 공구 진행률 바, 잔여시간 배지, 환불 정보 구조 개선
- `src/main/resources/static/css/components.css`: `button.summary-card` 리셋 추가
- `src/main/java/com/gong9ri/gong9ri/dto/SellerProductResponse.java`: `imageUrl` 필드 추가
- `src/main/java/com/gong9ri/gong9ri/dto/SellerTeamResponse.java`: `imageUrl` 필드 추가
- `src/main/java/com/gong9ri/gong9ri/dto/RefundRequestResponse.java`: `imageUrl` 필드 추가
- `src/main/java/com/gong9ri/gong9ri/dto/PurchaseResponse.java`: `imageUrl` 필드 추가
- `src/main/java/com/gong9ri/gong9ri/dto/BuyerTeamResponse.java`: `imageUrl` 필드 추가
- `docs/api/mypage.md`, `docs/api/refund.md`: API 응답 스키마 갱신
- `docs/dev/frontend/seller-mypage/design.md`: 최종 설계 문서 갱신
- `docs/logs/frontend/seller-mypage/002-seller-mypage-redesign.md`: 실행 로그 (Attempt 1, 2)

## 평가 결과

- `./gradlew compileJava` 및 `compileTestJava` 빌드 성공.
- 컨트롤러 테스트(`SellerMypageControllerTest`, `BuyerMypageControllerTest`)에서 `imageUrl` 응답 assertion 검증.
- 탭 스위칭, KPI 연동, 잔여 시간 배지, 썸네일 이미지 및 fallback 처리, 환불 요청자명 분리 구조 정상 반영.
