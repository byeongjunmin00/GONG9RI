# 002-price-tier-label-fix — 가격 구간 인원 표시 문구 수정 (로그)

## Attempt 1 — 2026-08-14  ✅ PASS
- 시도: 결제 페이지의 가격 구간 표(`renderPriceTiers()`)에서 인원 표시를 "N명 이상" →
  "N명"으로 변경(`js/checkout.js`, `countCell.textContent` 조립부). 상품 상세 페이지
  (`js/product.js`)에서 같은 문제가 리포트되어 동일 패턴인 이 파일도 함께 수정 — 상세는
  `docs/logs/frontend/product-detail/002-price-tier-label-fix.md` 참고.
- 결과: `git diff`로 변경 반영 확인. 별도 프론트 테스트 스위트가 없어 배포 후 결제 페이지
  실제 화면으로 재확인할 예정.
