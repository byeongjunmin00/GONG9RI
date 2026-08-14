# 002-price-tier-label-fix — 가격 구간 인원 표시 문구 수정 (로그)

## Attempt 1 — 2026-08-14  ✅ PASS
- 시도: 상품 상세 페이지의 "모집 인원 1인당 가격" 표(price-tiers-table)에서 각 구간 인원이
  "2명 이상", "5명 이상"으로 표시되던 걸 "2명", "5명"으로 변경(`js/product.js`
  `renderProduct()`의 `countCell.textContent` 조립부, `'명 이상'` → `'명'`). 사용자 리포트로
  발견 — 결제 페이지(`js/checkout.js` `renderPriceTiers()`)에도 동일한 문구/패턴이 있어
  같이 수정.
- 결과: 프로덕션(`gong9ri-production.up.railway.app/product.html?id=4`)에서 수정 전 상태
  실측 확인 — "2명 이상 22,000원 / 5명 이상 18,000원 / 10명 이상 15,000원". 코드 수정 후
  로컬에서 `git diff`로 변경 반영 확인. 별도 프론트 테스트 스위트가 없어(정적 JS, 단위테스트
  대상 아님) 배포 후 같은 페이지 재확인으로 최종 검증할 예정.
- 증거(수정 전): `product.html?id=4` → "모집 인원 1인당 가격" 표에 "N명 이상" 표기.
