# 공구팀 미존재 시 상품 카드 안내 뱃지 — Design

## 개요
메인 페이지(`/`)의 상품 카드 중 진행 중인 공구 팀(RECRUITING 상태)이 없는 상품 카드에 안내 뱃지(`🔥 첫 공구팀 신설하고 최저가 도전!`)를 노출하여 하단 밋밋함을 해소하고 유저의 공구 팀 신설을 유도한다.

## 관련 코드 위치
- `src/main/resources/static/css/components.css`: `.card-no-team-badge`
- `src/main/resources/static/js/main.js`: `createEmptyTeamBadge()`, `createProductCard()`

## 규칙 / UI 상세
1. **표시 조건**:
   - `ProductSummaryResponse.activeTeamCurrentCount` 또는 `activeTeamTargetParticipants`가 없는 경우 (`null` 또는 0 이하).
   - 즉, 진행 중인 RECRUITING 팀이 없을 때만 그린다.
2. **뱃지 구성**:
   - Class: `.card-no-team-badge`
   - Text: `🔥 첫 공구팀 신설하고 최저가 도전!`
   - Style: 은은한 웜톤 서브 표면(`--color-surface-alt`), Pretendard 700 bold, `var(--color-pink)` 폰트 색상, `--radius-sm` (4px).
