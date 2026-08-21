# 관리자 대시보드 및 UI/UX 개선

대상: frontend/admin
담당: 전용운

## 배경 / 요구

현재 관리자 대시보드(`admin/dashboard.html`) 및 관리자 서브 페이지들(`members.html`, `products.html`, `refunds.html`, `support.html`)은 inline style로 작성된 투박한 5개 내비게이션 버튼과 숫자만 나열된 요약 카드로 이루어져 있어 가독성과 디자인 일관성이 떨어졌다.
구매자 및 판매자 마이페이지에서 확립된 브랜드 디자인 시스템(.mypage-profile-card, .mypage-summary-grid, .mypage-nav-tabs)을 관리자 영역에도 적용하여 한눈에 주요 지표를 파악하고 각 관리 메뉴로 빠르게 접근할 수 있도록 UI/UX를 개선했다.

## 설계 및 구현 내용

### 1. 관리자 프로필 & 대시보드 상단 대시보드 카드
- 관리자 계정 이름 및 이메일 노출 (`AdminGuard.requireAdmin()`이 반환한 `member` 객체 재활용, 중복 `/api/auth/me` 호출 제거 컨벤션 준수)
- `.mypage-profile-card` 패턴을 통한 상단 대시보드 카운터/프로필 헤더 영역 구성

### 2. KPI 요약 카드 개선 및 퀵 내비게이션 (Quick Action)
- 브랜드 디자인 컴포넌트인 `.mypage-summary-grid` + `.summary-card` 구조 적용
- KPI 카드(회원, 구매자, 판매자, 상품, 대기 환불 건수) 클릭 시 대응하는 관리 페이지(`/admin/members.html`, `/admin/products.html`, `/admin/refunds.html` 등)로 즉시 이동하는 클릭 액션 연동
- "전체 결제" 카드는 전용 관리 페이지가 없는 점을 고려해 불필요한 환불 페이지 이동을 제거하고 지표 전용 카드로 명확화

### 3. 일관된 서브 탭 내비게이션 UI 적용
- ad-hoc 인라인 스타일 `<nav style="...">`을 브랜드 공용 탭 컴포넌트(`.mypage-nav-tabs`, `.mypage-tab-btn`)로 일괄 교체
- 대시보드(`dashboard.html`), 회원 관리(`members.html`), 상품 현황(`products.html`), 환불 요청 현황(`refunds.html`), 상담 관리(`support.html`) 5개 페이지 전체에 세련되고 통일된 탭 네비게이션 적용

### 4. 대시보드 퀵 섹션 바로가기 카드 배치
- 대시보드 하단에 주요 관리 기능(회원 관리, 상품 현황, 환불 요청, 고객 지원 상담)으로 빠르게 이동할 수 있는 퀵 관리 카드 파트 추가

## 변경된 파일 목록

- `src/main/resources/static/admin/dashboard.html`: 관리자 프로필 카드, KPI 대시보드 그리드, 탭 내비게이션, 퀵 관리 카드 마크업 작성
- `src/main/resources/static/admin/members.html`: 브랜드 탭 내비게이션 적용
- `src/main/resources/static/admin/products.html`: 브랜드 탭 내비게이션 적용
- `src/main/resources/static/admin/refunds.html`: 브랜드 탭 내비게이션 적용
- `src/main/resources/static/admin/support.html`: 브랜드 탭 내비게이션 적용
- `src/main/resources/static/js/admin-dashboard.js`: member 객체 재활용 프로필 로드, KPI 카운터 대입, 클릭 페이지 이동 개선
- `docs/dev/admin/design.md`: 관리자 디자인 SSOT 갱신
- `docs/logs/frontend/admin/003-admin-dashboard-redesign.md`: 실행 로그 (Attempt 1, 2)

## 평가 결과

- `./gradlew compileJava` 빌드 검증 성공.
- 5개 관리자 페이지 전체 탭 네비게이션 통일 및 대시보드 프로필/KPI 카운터/퀵 링크 이동 정상 동작 확인.
