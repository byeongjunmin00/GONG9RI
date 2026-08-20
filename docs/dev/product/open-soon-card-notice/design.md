# 메인 페이지 오픈예정 상품 카드 안내 텍스트 — Design

## 개요
메인 페이지(`/`)의 상품 카드 중 `openAt`이 미래 시각인 "오픈예정" 상품에 대해, 일반 공구팀 미존재 카드의 공구팀 신설 유도 뱃지 대신 얌전하고 차분한 서브 텍스트 라인(`8월 25일 14:00 오픈 예정`)을 표기한다.

## 관련 코드 위치
- `src/main/resources/static/css/components.css`: `.card-open-soon-notice`
- `src/main/resources/static/js/main.js`: `isUpcomingProduct()`, `formatOpenAtDate()`, `createOpenSoonNotice()`, `createProductCard()`

## UI 사양
- **표시 조건**: `product.openAt != null` 이고 `new Date(product.openAt) > Date.now()` 인 상품 카드.
- **문구**: `M월 D일 HH:mm 오픈 예정` (예: `8월 25일 14:00 오픈 예정`, 이모지 없음)
- **스타일**:
  - Class: `.card-open-soon-notice`
  - Style: `color: var(--color-text-muted)` (`#7A7280`), Pretendard 600, font-size 12px (`var(--fs-xs)`). 배경 및 테두리 박스 없음.
