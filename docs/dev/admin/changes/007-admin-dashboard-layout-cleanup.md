# 관리자 대시보드 중복 탭 제거 및 퀵 바로가기 2x2 그리드 레이아웃 개편

대상: frontend/admin
담당: 전용운

## 배경 / 요구

현재 관리자 대시보드(`admin/dashboard.html`)에는 상단 탭 네비게이션과 하단 '관리 기능 퀵 바로가기' 위젯/카드가 중복되어 존재했다.
- 대시보드 하단의 바로가기 위젯이 아이콘과 설명이 붙어 있어 사용성이 더 뛰어나므로, 대시보드 페이지에 한해 상단 중복 탭(`<nav class="mypage-nav-tabs">`)을 제거했다.
- 일렬(위아래)로 나열되어 보기가 안 좋았던 바로가기 카드들을 **좌우 2x2 그리드 레이아웃**으로 깔끔하게 재배치했다.

## 설계 및 구현 내용

1. **상단 중복 서브 탭 제거 (`admin/dashboard.html`)**:
   - `admin/dashboard.html`에서 `<nav class="mypage-nav-tabs">...</nav>` 마크업 제거.
   - 서브 관리자 페이지들(`members.html`, `products.html`, `refunds.html`, `support.html`)의 탭은 위치파악용으로 유지.

2. **퀵 바로가기 위젯 2x2 좌우 그리드 재정렬 (`admin/dashboard.html`)**:
   - 4개 관리 기능 바로가기 카드(회원 관리, 상품 현황, 환불 요청 현황, 상담 관리) 컨테이너의 그리드 스타일을 `display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: var(--space-4);`로 구성하여 데스크톱/태블릿 화면에서 좌우 2컬럼(2x2)으로 깔끔하게 배치.

## 변경된 파일 목록

- `src/main/resources/static/admin/dashboard.html`: 상단 서브 탭 제거 및 퀵 바로가기 카드 2x2 좌우 반응형 그리드 적용
- `docs/dev/admin/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/admin/007-admin-dashboard-layout-cleanup.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava` 빌드 검증 성공.
- `admin/dashboard.html`에서 상단 중복 탭이 정상 제거되고 4개 바로가기 카드가 2x2 좌우 그리드로 배치됨 확인.
