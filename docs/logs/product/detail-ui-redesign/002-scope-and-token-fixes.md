# 002-scope-and-token-fixes — 리디자인 후속 CSS 수정 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS
- 시도: 팀원 커밋 `824fe91`(상세 2열 리디자인) 코드리뷰에서 찾은 CSS 결함 2종 수정.
- 결과: `./gradlew test` 전체 **397케이스 통과**.
- 증거(실측):
  - **미정의 CSS 변수 5곳** — `--color-border-subtle`(2), `--radius-xl`(2), `--fs-md`(1)이 `tokens.css`에
    없어 선언이 무효화되고 있었다. 결과: `border-color`가 초기값 `currentColor`(글자색)로, `border-radius`가
    `0`(각진 모서리)으로, `font-size`가 부모값으로 떨어짐. 정의된 토큰(`--color-border`/`--radius-lg`/
    `--fs-base`)으로 교체 후 **정의되지 않은 `var()` 사용 0건** 확인(components/layout/base 전수 검사).
  - **공용 선택자 범위 누수 — 이미 발생한 회귀였음.** `.product-actions` 등이 상품 상세 전용이 아니라
    `checkout.html`·`seller/products/new.html`·`edit.html`도 함께 쓰는데, 리디자인이 뒤에 추가한
    `flex-direction: column`이 전부에 적용돼 **그 3개 페이지의 버튼이 나란히 놓이던 것에서 세로로 쌓이도록
    바뀌어 있었다.** 리디자인 규칙을 `.product-detail-summary` 하위로 범위를 좁혀 해결.
  - 검증: 중복 선택자 **0개**, 중괄호 균형 259:259, 로컬 서빙 확인(범위 좁힌 규칙 적용 / 공용 규칙 원본
    유지 / product·checkout·seller 페이지 전부 200).
- 특기: 문서의 "기존 DOM ID 100% 보존" 주장은 **그대로 믿지 않고 검증**했다 — `product.js` 참조 ID 41개와
  함께 로드되는 스크립트 7개까지 전수 대조 결과 실제로 누락 없음(주장은 사실이었고, 문제는 CSS에 있었다).
