# 003-sticky-overlap-fix — 썸네일 sticky 겹침 (로그)

## Attempt 1 — 2026-08-20  ✅ PASS (육안 확인은 사용자 몫)
- 시도: 사용자 리포트("스크롤 내리면 사진이 따라와서 다 가린다")에 대해, 전폭 섹션을 그리드 밖 형제로
  빼고 상단 2열만 별도 래퍼(`.product-detail-grid`)로 묶음.
- 원인: 리디자인 그리드에 **4개**가 들어 있었다 — sticky 썸네일(1행 1열), 요약(1행 2열), 그리고 전폭
  섹션 2개가 `grid-column: 1 / -1`로 2·3행에. **그리드에 넣어놓고 다시 그리드를 무효화하는 구조**라
  sticky가 아래 행 영역까지 따라 내려와 "모집 중인 공구팀"을 덮었다.
- 결과: `./gradlew test` 전체 **407케이스 통과**.
- 증거(프로덕션 실측, DOM 구조):
  ```
  #product-detail
  ├── product-detail-grid        ← 2열 그리드는 여기까지
  │     ├── product-detail-media    (sticky)
  │     └── product-detail-summary
  ├── team-list-section          ← 그리드 밖
  └── product-tabs-section       ← 그리드 밖
  ```
  `product-detail-wide` 잔여 0건(HTML·CSS 양쪽), sticky 규칙 유지, `/product.html?id=33` 200.
- 검증: 마크업을 옮겼으므로 `product.js` 참조 **ID 41개 전부 존재**(누락 0), 클래스 선택자 중
  `team-item-join-btn`은 JS가 동적 생성하는 것(`product.js:610`)으로 수정 전에도 HTML에 없었음을 확인,
  HTML 태그 균형(div 35:35), CSS 중괄호 263:263.
- **미검증 항목(정직하게 남김)**: 이 작업 환경엔 브라우저가 없어 **실제 스크롤 시 겹침이 사라졌는지는
  확인하지 못했다.** 구조적으로 겹칠 수 없게 만들고 서빙까지 확인했으나 최종 육안 확인은 사용자 몫.
