# 관리자 회원/상품 초고밀도 컴팩트 그리드 레이아웃 개편 (세로 높이 135px 고정, 한 화면 16개 피트)

대상: frontend/admin
담당: 전용운

## 배경 / 요구사항

기존 회원 관리(`admin/members.html`) 및 상품 현황(`admin/products.html`) 페이지는 데이터를 세로 1열 리스트(`ul.mypage-list`) 형태로만 보여주어 스크롤을 많이 내려야 하고 한 화면에 담기는 회원/상품 수가 적었다 (한 화면 약 4개~6개).

- **개선 목표**: 세로 1열 나열 방식을 탈피하여 **카드 1개의 세로 높이를 135px로 고정**하고 **가로 250px 규격의 초고밀도 컴팩트 멀티컬럼 그리드(`repeat(auto-fill, minmax(250px, 1fr))`)**로 개편했다.
- **결과**: 스크롤을 전혀 내리지 않는 대시보드 유효 뷰포트 영역(높이 약 600px) 안에서 **가로 4열 × 세로 4행 = 총 16개의 회원 및 상품 카드**가 세로 스크롤 없이 한 화면에 100% 꽉 들어차게 조망 및 관리가 가능해졌다.

## 설계 및 구현 내용

1. **CSS 초고밀도 컴팩트 그리드 및 세로 높이 고정 스타일 (`components.css`)**:
   - `.admin-grid-list`: `display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: var(--space-3); margin-bottom: var(--space-4);`
   - `.admin-card`: `height: 135px; padding: var(--space-3); display: flex; flex-direction: column; justify-content: space-between; overflow: hidden;`

2. **컨테이너 개편 (`admin/members.html` & `admin/products.html`)**:
   - `<ul id="members-list" class="mypage-list">` 및 `<ul id="products-list" class="mypage-list">`를 `<div id="members-list" class="admin-grid-list">` 및 `<div id="products-list" class="admin-grid-list">`로 변경.

3. **회원/상품 4행 카드 렌더링 (`admin-members.js` & `admin-products.js`)**:
   - 1행: [아바타/썸네일] + [이름/상품명 1줄 말줄임] + [역할/가격/상태 배지]
   - 2행: [이메일/판매자명]
   - 3행: [🛍️구매 · 👥공구 · 📦상품 수] / [⭐평점 · 👥활성팀 인라인 배지]
   - 4행: [정지/해제], [삭제], [상세보기] 초소형 관리 액션 버튼 (height: 26px)

## 변경된 파일 목록

- `src/main/resources/static/css/components.css`: `.admin-grid-list` 및 세로 135px 고정 `.admin-card` CSS 추가
- `src/main/resources/static/admin/members.html`: `<div id="members-list" class="admin-grid-list">` 적용
- `src/main/resources/static/admin/products.html`: `<div id="products-list" class="admin-grid-list">` 적용
- `src/main/resources/static/js/admin-members.js`: 초고밀도 카드 렌더링 작성
- `src/main/resources/static/js/admin-products.js`: 초고밀도 카드 렌더링 작성
- `docs/dev/admin/design.md`: 최종 SSOT 갱신
- `docs/logs/frontend/admin/009-admin-grid-card-layout.md`: 실행 로그 (Attempt 1)

## 평가 결과

- `./gradlew compileJava` 빌드 검증 성공.
- 대시보드 유효 뷰포트에서 스크롤을 내리지 않고도 16개(4열 × 4행) 카드가 세로 폭 135px로 피트되어 한눈에 가득 조망됨을 확인.
