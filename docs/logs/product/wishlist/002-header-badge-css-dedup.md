# 002-header-badge-css-dedup — 헤더 찜 뱃지 CSS 중복 제거 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: `layout.css`의 `.site-header__wishlist-badge`/`.site-header__notifications-badge`가
  15개 선언을 완전히 동일하게 복붙하고 있던 것을 콤마 선택자 하나로 합침. 선언 내용을 직접
  대조해 완전히 동일함을 먼저 확인한 뒤 병합.
- 결과: CSS 중괄호 균형 42:42(수정 전후 open/close 브레이스 카운트로 확인). 선언값이 그대로라
  렌더링 결과는 변화 없음.
- 참고: JS 쪽(`header-notifications.js`/`header-wishlist-badge.js`)의 동일한 8줄 뱃지 렌더링
  함수 중복은, 공유 파일로 빼려면 헤더가 삽입된 17개 페이지의 script 태그를 전부 손대야 해서
  이번엔(직전 CSS 범위 누수 사고 이후라 더더욱) 사용자와 상의 후 보류하기로 함.
