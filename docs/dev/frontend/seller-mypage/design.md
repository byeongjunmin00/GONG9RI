# 판매자 마이페이지 (frontend/seller-mypage) — Design

## 개요

판매자가 자신의 등록 상품 목록/수정·삭제/수익 현황/공구 참여 현황/환불 요청 관리를 한 페이지(`/seller/mypage.html`)에서 확인·관리한다. 상단에는 판매자 프로필과 핵심 수익 KPI 요약 카드가 배치되며, 하단에는 탭 네비게이션을 통해 각 관리 섹션([전체 현황], [등록 상품], [공구 현황], [환불 관리], [계정 설정])을 빠르게 전환할 수 있다. 상품 수정은 별도 페이지(`/seller/products/edit.html?id={productId}`)에서 처리한다.

## 인터페이스 / 산출물

```
src/main/resources/static/
├── seller/
│   ├── mypage.html               # 마이페이지 본체(프로필/수익 KPI 대시보드, 탭 바, 상품/공구/환불/계정 탭)
│   └── products/
│       └── edit.html             # 상품 수정 폼
└── js/
    ├── seller-mypage.js          # API 호출·렌더링, 탭 스위칭, 삭제, 환불 승인/거절, 프로그레스바/배지
    └── seller-product-edit.js    # 기존 값 로드, 가격구간 프리필, PUT 제출
```

- `css/components.css`: `.mypage-profile-card`, `.mypage-summary-grid`, `.summary-card`(`button.summary-card` 리셋 포함), `.mypage-nav-tabs`, `.mypage-tab-btn`, `.mypage-tab-panel`, `.mypage-list-item__thumb`, `.team-progress`, `.badge-time`, `.refund-reject-panel` 공용 컴포넌트 스타일 적용.
- `partials/header.html`: nav("판매 물품 등록" 옆)에 "판매자 마이페이지"(`/seller/mypage.html`) 링크 노출.
- `SecurityConfig.java`: 서브디렉토리 html permitAll 허용.

## 데이터 연동

- **프로필 & 수익 대시보드**:
  - `GET /api/auth/me`: 판매자 이름 및 이메일 표시.
  - `GET /api/seller/mypage/revenue`: 총 매출(`totalRevenue`), 결제 완료 건수(`paidCount`), 환불 건수(`refundedCount`) 요약 카드 렌더링.
  - `GET /api/seller/mypage/refund-requests`: 응답 중 `PENDING` 상태 건수를 계산하여 "대기 환불" 카운터 노출 (클릭 시 [환불 관리] 탭으로 즉시 전환).
- **등록 상품 목록 (`GET /api/seller/mypage/products`)**:
  - 상품명, 기본 가격, 최대 정원, 상품 대표 썸네일(`imageUrl`) 노출.
  - "수정" 링크는 `/seller/products/edit.html?id={productId}`(API 호출 없음).
  - "삭제"는 `window.confirm` 확인 후 `DELETE /api/products/{productId}` 호출 → 성공(204) 시 해당 항목만 DOM에서 제거.
- **공구 참여 현황 (`GET /api/seller/mypage/teams`)**:
  - 상품 대표 썸네일(`imageUrl`), 상품명, 참여/정원 수, 팀장/참여자 명단 노출.
  - `RECRUITING` 상태: 인원 달성률 프로그레스 바(`.team-progress`) 및 잔여 시간 배지(`.badge-time`, ⏱️ N일 N시간 남음) 표시.
  - `SUCCESS` / `FAILED`: 해당 상태 배지 표시.
- **환불 요청 관리 (`GET /api/seller/mypage/refund-requests`)**:
  - 요청자명(`requesterName`)을 타이틀 라인으로 분리, 상품 썸네일(`imageUrl`), 상품명, 요청 금액, 요청 일시, 사유를 메타 라인으로 노출.
  - `PENDING` 항목: "승인"(`POST /api/refund-requests/{id}/approve`) 및 "거절"(`POST /api/refund-requests/{id}/reject` - 사유 템플릿 드롭다운) 액션 패널 제공. 승인/거절 완료 시 해당 카드만 인플레이스 갱신 및 대기 환불 카운터 차감.
- **계정 정보 수정**:
  - `js/account-info.js` 연동 (이름/이메일 변경 폼).
- **에러 핸들링**:
  - 401(UNAUTHORIZED): 공통 배너(`#page-alert`)로 로그인 안내+링크 노출, 세부 섹션 숨김.
  - 403(FORBIDDEN, 구매자 계정)/기타: 각 섹션별 상태 영역에 에러 메시지 표시, 독립적 로드 유지.
  - XSS 방지: 서버 응답 문자열은 `textContent`로만 대입.

## 규칙 / 검증

- 서브디렉토리 페이지(`seller/mypage.html`, `seller/products/edit.html`)의 CSS/JS/partial/페이지 이동 참조는 반드시 절대경로 사용.
- 삭제는 되돌릴 수 없으므로 `window.confirm` 확인 절차를 거친 뒤에만 `DELETE` 호출.
- 상품 썸네일은 `Product` 엔티티의 `imageUrl` 컬럼을 DTO에서 직접 내려주며, 이미지가 없는 경우 SVG 플레이스홀더 아이콘으로 안전하게 fallback.

## 관련 코드 위치

- `src/main/resources/static/seller/mypage.html`, `js/seller-mypage.js`
- `src/main/resources/static/seller/products/edit.html`, `js/seller-product-edit.js`
- `src/main/resources/static/css/components.css`
- `src/main/java/com/gong9ri/gong9ri/dto/SellerProductResponse.java`
- `src/main/java/com/gong9ri/gong9ri/dto/SellerTeamResponse.java`
- `src/main/java/com/gong9ri/gong9ri/dto/RefundRequestResponse.java`
- `src/main/java/com/gong9ri/gong9ri/dto/PurchaseResponse.java`
- `src/main/java/com/gong9ri/gong9ri/dto/BuyerTeamResponse.java`
- 경위: `docs/dev/frontend/seller-mypage/changes/002-seller-mypage-redesign.md`, 실행 로그: `docs/logs/frontend/seller-mypage/002-seller-mypage-redesign.md`
