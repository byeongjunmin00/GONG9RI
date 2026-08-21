# 모바일 hover 가드 · 터치 타겟 후속 정리

대상: frontend/mobile-fixes
담당: 전용운

## 배경 / 요구

`changes/002-mobile-header-card-ux.md`에서 헤더/카드의 hover sticky 문제와 터치 타겟을 고치면서, 같은 패턴이 다른 페이지 컴포넌트에도 남아있을 가능성이 있어 전체를 코드 리딩으로 점검했다(사용자 요청 — "다른페이지들도 한번 싹 점검해봐"). Explore 에이전트로 19개 페이지의 `<style>` 블록 유무와 공용 CSS(`css/components.css`) 사용처를 대조한 결과, 002와 **완전히 같은 유형**의 문제가 몇 군데 남아있는 것을 확인했다.

## 설계 (접근 방향)

002에서 쓴 것과 동일한 두 가지 처리를 그대로 적용한다:

1. **hover 가드**(`@media (hover: hover)`) 누락분 추가 — transform/box-shadow로 시각적 리프트가 있는데 가드가 없는 것만 대상:
   - `.btn-ghost:hover`(components.css:64) — 같은 `.btn` 계열인 `.btn-primary`/`.btn-secondary`는 이미 가드됐는데 이것만 빠짐
   - `.summary-card:hover`(860줄) — buyer/seller mypage·admin dashboard 탭 전환 버튼(`button.summary-card[data-tab]`)
   - `.mypage-list-item:hover`(950줄) — 구매내역/판매내역/팀목록 항목(js가 동적 렌더링)
   - `.chat-widget__button:hover`, `.support-widget__button:hover`(1490줄, 2588줄) — 대부분 페이지 우하단 플로팅 버튼
2. **터치 타겟 확대**(모바일 전용, `@media (max-width: 767px)`):
   - `.chat-widget__close`, `.support-widget__close` — 패딩 없이 글자 크기만큼이라 32px 미만으로 추정, 채팅/고객센터 패널 닫기 버튼
   - `.image-preview-remove` — 22×22px 고정, 상품 등록/수정 페이지 이미지 삭제 버튼

**이번 범위에서 제외**(사용자 확인): `.btn-sm`(마이페이지·관리자 액션 버튼, ~28px 추정) — 사이트 전역에 광범위하게 쓰여 파급 범위가 크다고 판단해 이번엔 건드리지 않는다. 실측 후 별도 판단.

그 외 조사에서 확인한 항목(`.product-header-rating`/`.kakao-share-btn`/`.product-gallery-thumb`/`.admin-card`의 hover, 상품상세·결제·관리자 그리드/테이블/모달)은 이미 문제 없거나 임팩트가 낮아 범위에 넣지 않는다.

## 태스크
- [ ] `.btn-ghost:hover`에 `@media (hover: hover)` 가드 적용
- [ ] `.summary-card:hover`에 가드 적용
- [ ] `.mypage-list-item:hover`에 가드 적용
- [ ] `.chat-widget__button:hover`, `.support-widget__button:hover`에 가드 적용
- [ ] `.chat-widget__close`, `.support-widget__close` 모바일 터치 타겟 확대
- [ ] `.image-preview-remove` 모바일 터치 타겟 확대

## 평가(통과) 기준
- CSS 중괄호 균형 확인
- 002와 같은 방식으로 독립 정적 서버를 띄워 모바일(375px)/데스크톱(1280px) 뷰포트에서 `matchMedia('(hover: hover)')`에 따라 각 요소의 hover 규칙이 조건대로 적용/비적용되는지, 터치 타겟 크기(`getComputedStyle`)가 의도한 값인지 확인
- 데스크톱에서 기존 hover 동작 회귀 없는지 확인
