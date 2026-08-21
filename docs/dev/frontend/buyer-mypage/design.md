# 구매자 마이페이지 (buyer-mypage) — Design

## 개요

구매자가 본인의 계정 정보 관리, 구매 내역, 공구 참여 현황, 찜한 상품 목록, 환불 요청 내역을 한눈에 확인하고 관리하는 대시보드 형태의 마이페이지 화면이다.
기존 수직 스크롤 5개 섹션 구조를 상단 프로필 KPI 대시보드와 탭(Tab) 네비게이션으로 재구성하여 가독성과 탐색 효율성을 높였다.

## 관련 코드 위치

- **HTML**: `src/main/resources/static/buyer/mypage.html` (서브디렉토리 페이지이므로 CSS/JS/partial 참조는 절대경로 원칙 준수)
- **JS**: `src/main/resources/static/js/buyer-mypage.js`
- **CSS**: `src/main/resources/static/css/components.css` (`.mypage-*`, `.team-progress`, `.badge-time` 등)
- **API 계약**: `docs/api/mypage.md`, `docs/api/refund.md`, `docs/api/team.md`

## API / 인터페이스

- `GET /api/auth/me` — 상단 프로필 카드 정보 (이름/이메일)
- `GET /api/buyer/mypage/purchases` — 구매 완료 목록. 필드: `paymentId`, `productId`, `productName`, `amount`, `status`(`PAID`|`REFUNDED`), `paidAt`
- `GET /api/buyer/mypage/teams` — 본인이 참여한 팀 목록. 필드: `teamId`, `productId`, `productName`, `currentCount`, `maxParticipants`, `status`(`RECRUITING`|`SUCCESS`|`FAILED`), `deadline`, `joinedAt`
- `GET /api/buyer/mypage/wishlist` — 찜한 상품 목록
- `GET /api/buyer/mypage/refund-requests` — 환불 요청 내역 및 처리 상태 (`APPROVED`, `REJECTED`, `WAITING`)
- `POST /api/payments/{paymentId}/refund-requests` — 혼자 구매 건(teamId null) 환불 요청
- `POST /api/teams/{teamId}/leave` — 모집 중(`RECRUITING`) 공구팀 참여 취소
- `DELETE /api/products/{productId}/wishlist` — 찜 해제

## UI / 컴포넌트 구조

1. **상단 프로필 & 요약 대시보드 (`.mypage-profile-card`)**:
   - 프로필 카드: 사용자 이름 및 이메일 표시 (`#summary-user-name`, `#summary-user-email`)
   - 4종 핵심 KPI 요약 카드 (`.summary-card`): 구매 내역, 참여 공구, 찜한 상품, 환불 내역 수치 표시 (클릭 시 해당 탭 스위칭)
2. **탭 네비게이션 (`.mypage-nav-tabs`)**:
   - `[전체 현황]`, `[구매 내역]`, `[공구 참여]`, `[찜한 상품]`, `[환불 내역]`, `[계정 설정]` 탭 메뉴
   - 활성 탭 강조 (`--color-brand` 색상 및 밑줄), URL hash 연동 (`#purchases`, `#teams` 등)
3. **카드 시각 요소 (Visual Hierarchy)**:
   - 상품 썸네일/아이콘 영역 (`.mypage-list-item__thumb`): 텍스트 위주 카드에 시각적 구분 부여
   - 모집중 공구팀 달성률 프로그레스 바 (`.team-progress`): `currentCount/maxParticipants` 게이지 시각화
   - 잔여 시간 배지 (`.badge-time`): 모집중 팀에 `⏱️ 마감까지 ...` 배지 노출

## 규칙 / 검증 및 한계

- **401/403 에러 처리 분기**:
  - `401 UNAUTHORIZED` 수신 시 상단 공통 배너(`page-alert`)에 로그인 안내 및 로그인 링크 노출 후 마이페이지 섹션 전체 숨김.
  - `403 FORBIDDEN` (판매자 계정 접속 시) 등 기타 에러는 해당 섹션 내 독자적 상태 노출 후 타 섹션 독립적 로드 유지.
- **성사 팀의 결제 상세 매칭 (best-effort 한계)**:
  - `purchases` API와 `teams` API는 서로 다른 엔드포인트이며 팀 응답에는 `paymentId`가 없음.
  - 따라서 `SUCCESS` 상태 팀 항목은 `productId` 기준으로 `purchases` 목록의 `PAID` 결제 항목을 최선 노력(best-effort)으로 매칭하여 금액/결제일시를 표시하며, 매칭 실패 시에도 팀 기본 정보만 안정적으로 표시한다.
- **환불 및 참여 취소 정책**:
  - 혼자 구매(teamId null) 건은 결제 항목에 "환불 요청" 버튼 노출.
  - 팀이 딸린 결제는 "참여 취소"로만 환불 처리 가능 (`RECRUITING` 상태만 취소 허용).
- **`RECRUITING`의 남은 기간은 페이지 로드 시점 1회 계산** — 새로고침 없이는 실시간 갱신되지 않는다(후속 과제).

## 변경 이력

- 경위: `docs/dev/frontend/buyer-mypage/changes/001-buyer-mypage.md`(최초 구현), `docs/dev/frontend/buyer-mypage/changes/002-buyer-mypage-redesign.md`(탭/대시보드 리디자인)
- 실행 로그: `docs/logs/frontend/buyer-mypage/001-buyer-mypage.md`, `docs/logs/frontend/buyer-mypage/002-buyer-mypage-redesign.md`
