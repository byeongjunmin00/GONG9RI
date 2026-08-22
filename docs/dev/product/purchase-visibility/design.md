# 구매 UI 노출 제어 — Design

## 개요

상품 상세 페이지(`product.html`)의 구매 관련 UI(공구팀 신설/구매/계속 쇼핑하기, 환불 동의
체크박스, 목표 인원 선택, 각 공구팀의 "참가하기" 버튼)는 **로그인한 회원의 역할이 BUYER일 때만**
노출된다. 서버가 구매(공구팀 신설/참가/탈퇴, 결제 시작)를 `Role.BUYER`에게만 허용하므로
(`TeamService`/`PaymentService`의 `requireBuyer`), 그 외 역할(SELLER/ADMIN)에게는 클릭해도
항상 실패하는 액션을 아예 보여주지 않는다. 상품은 항상 SELLER가 등록하므로(`ProductService`의
`requireSeller`) "자기 상품을 보는 판매자"도 이 조건에 자연히 포함된다.

비로그인 사용자는 대상이 아니다 — 구매 UI를 그대로 보여주고, 클릭 시 서버 401 응답을 그대로
안내해 로그인을 유도하는 기존 흐름을 유지한다.

## 관련 파일

- `src/main/resources/static/product.html`: `#product-actions`(신설/구매/계속 쇼핑하기),
  `#refund-notice-field`(환불 동의 체크박스 `<label>`) — js가 통째로 `hidden` 토글하는 대상.
- `src/main/resources/static/js/product.js`:
  - `currentMemberRole` — 로그인한 회원의 role('BUYER'|'SELLER'|'ADMIN', 비로그인이면 null).
    `currentMemberId`와 같은 시점('gong9ri:auth-resolved')에 채워진다.
  - `isNonBuyerMember()` — 로그인은 했지만 role이 BUYER가 아닌지.
  - `applyPurchaseRoleVisibility()` — `isNonBuyerMember()`가 true면 `#product-actions`,
    `#refund-notice-field`, `#target-participants-field`를 숨긴다. `renderProduct()`
    이후(정확히는 `renderTargetParticipantsOptions()` 안)와 `gong9ri:auth-resolved` 도착 시
    둘 다 호출한다(어느 쪽이 먼저 올지 보장 없음). tier가 없어 이미 숨겨진
    `#target-participants-field`를 role 조건이 풀렸다고 다시 보이게 하지는 않는다(숨기는
    방향으로만 개입).
  - `createTeamItem()` — 각 공구팀의 "참가하기" 버튼도 `isNonBuyerMember()`면 아예 그리지
    않는다(참여 취소 버튼은 `team.joinedByCurrentMember` 기준이라 영향 없음). `auth-resolved`
    핸들러가 `loadTeams()`도 다시 호출해, 이 이벤트가 초기 팀 목록 로드보다 늦게 와도 참가
    버튼이 role 확정 전 상태로 남지 않게 한다.

## 규칙 / 검증

- 판단 기준은 로그인 회원의 **role**(`BUYER`/`SELLER`/`ADMIN`)이며, 상품의 `sellerId`와
  비교하는 별도 "자기 상품" 체크는 없다 — role이 SELLER/ADMIN이면 어차피 어떤 상품도 구매할 수
  없어(서버 `requireBuyer`) 그 체크가 항상 role 체크에 포함되기 때문. role은 회원가입 후
  불변이다(`Member` 엔티티에 role setter 없음).
- 요약 카드(`.product-detail-summary`)는 flex column + gap이라 위 요소를 `hidden` 처리하면
  별도 CSS 없이 카드 크기가 자연스럽게 줄어든다.
- 리뷰/문의 작성 폼은 이 규칙의 대상이 **아니다** — "서버가 SSOT"라는 기존 원칙에 따라 자격을
  클라이언트가 미리 예측하지 않고 항상 노출한 뒤 서버 응답을 그대로 보여준다(product.js 파일
  상단 주석 참고, 의도된 동작).
