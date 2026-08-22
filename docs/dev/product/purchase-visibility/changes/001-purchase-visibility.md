# 구매 불가 상황에서 구매 UI 노출 문제 정리

대상: product/purchase-visibility       <!-- 완료 시 이 기능의 changes/로 이동 -->
담당: 전용운

## 배경 / 요구

`docs/dev/todo-backlog.md`의 7·8번 항목.

### 7. 상품 상세 페이지 — 구매 불가 상황에서도 구매 액션이 그대로 보임

`product.html`의 구매 요약 카드(`.product-detail-summary`)에는 로그인 상태·역할과 무관하게
항상 다음이 노출된다(`src/main/resources/static/js/product.js` 확인 결과):

- `#create-team-btn`("🔥 신규 공구팀 신설하고 최저가 도전")
- `#buy-alone-btn`("혼자 바로 구매하기")
- `.btn-ghost`("계속 쇼핑하기")
- `#refund-notice-checkbox`("공구팀이 정원을 채워 성사되면... 확인했습니다")

그런데 백엔드는 구매(공구팀 신설/참가/탈퇴, 결제 시작)를 **`Role.BUYER`인 회원에게만** 허용한다
(`TeamService.requireBuyer`, `PaymentService.requireBuyer` — 그 외 역할은 무조건 403 `FORBIDDEN`).
상품은 `Role.SELLER`만 등록할 수 있어(`ProductService.requireSeller`) "자기 상품을 보는 판매자"는
이미 "역할이 SELLER인 상태"에 포함된다 — 즉 **로그인한 회원의 역할이 BUYER가 아니면(SELLER·ADMIN)
그 상품이 자기 것이든 아니든 어차피 구매가 불가능**하다.

→ 로그인 회원의 역할이 BUYER가 아닐 때, 위 버튼들과 체크박스를 안 보이게 하고 그만큼 줄어든
공간에 맞춰 카드가 자연스럽게 줄어들게 한다.

- 비로그인 사용자는 대상이 아니다(기존처럼 버튼을 보여주고 클릭 시 401 → 로그인 안내로 유도하는
  현재 흐름을 유지한다 — todo 7번이 지목한 대상은 "판매자·구매자[로 로그인한 회원]"이지 비로그인이
  아니다).

### 8. 구매자 전용 기능/문구가 판매자·관리자 화면에도 노출되는지 전체 점검 (+ 반대 방향도)

`product.html`/`product.js`, `seller/mypage.html`, `buyer/mypage.html`, `admin/*.html`을 확인했다.

**이미 올바르게 처리되어 있는 것들** (수정 불필요, 확인 결과만 기록):
- 헤더 nav(`partials/header.html`)의 역할별 링크(판매 물품 등록/판매자 마이페이지/구매자
  마이페이지·찜/관리자 대시보드)는 `data-role` + `header-auth.js`로 이미 정확히 분리되어 있다.
- AI 챗봇 위젯(`chat-widget.js`)은 이미 `role === 'BUYER'`일 때만 노출(그 외엔 숨김).
- 1:1 상담 위젯(`support-widget.js`)은 이미 "로그인 + 관리자가 아닐 때만" 노출.
- 리뷰/문의 작성 폼은 자격(구매 이력 등)을 클라이언트가 미리 예측하지 않고 항상 노출 후 서버
  응답을 그대로 보여주는 것이 기존 설계 원칙(product.js 상단 주석, "서버가 SSOT")이라 이건 버그가
  아니라 의도된 동작이다 — 이번 작업에서 건드리지 않는다.

**발견한 문제** (7번과 같은 원인 — "구매자 전용 액션인데 역할과 무관하게 노출"):
- `product.js`의 "모집 중인 공구팀" 목록(`#team-list`, `createTeamItem`)에서 각 팀의
  "참가하기" 버튼도 `TeamService.join`이 BUYER 전용이라 같은 문제를 갖는다 — 지금은 체크박스
  동의 여부로만 활성/비활성을 정하고, 역할은 전혀 안 본다. 7번과 동일한 원인이라 같은 방식(역할
  기반 판단)으로 함께 고친다.

**그 외 admin/*.html, seller·buyer mypage.html은 각 화면 자체가 이미 역할별로 분리된 페이지라
(다른 역할로 접근하면 서버가 401/403으로 걸러 해당 섹션에 에러를 보여주는 기존 방식) 화면 안에
잘못된 역할의 문구가 섞여 있는 경우는 못 찾았다.** "반대 상황"(판매자/관리자 전용 문구가 구매자
화면에 노출)도 위 파일들을 같은 기준으로 봤지만 별도로 찾지 못했다.

## 설계

- `product.js`가 이미 갖고 있는 `currentMemberId`(로그인한 회원 id, `gong9ri:auth-resolved`로
  채워짐) 패턴과 나란히, 같은 이벤트에서 회원의 `role`도 함께 저장한다. 다른 화면(header-auth.js,
  chat-widget.js)도 이미 `detail.member.role`을 이렇게 문자열로 비교하는 동일 패턴을 쓰고 있어
  일관된 방식이다.
- "지금 이 회원이 구매를 진행할 수 있는가"를 하나의 파생 상태로 두고, 이 상태가 바뀔 수 있는
  두 시점(상품 상세 로드 완료, 로그인 정보 확인 완료 — 둘 중 아무 때나 먼저 올 수 있음) 모두에서
  다시 계산해 요약 카드의 버튼/체크박스와 팀 목록의 "참가하기" 버튼에 반영한다.
- 요약 카드는 flex column + gap 레이아웃이라(`.product-detail-summary`) `hidden` 처리만으로
  아래 내용이 자연스럽게 붙는다 — CSS 쪽에 별도 고정 높이가 없어서(components.css 확인 완료)
  "서머리 영역 크기 조정"은 이 hidden 처리의 자연스러운 결과이지 별도 CSS 작업이 필요하지 않다.
  다만 목표 인원 선택(`#target-participants-field`)도 "신설하기" 버튼이 없으면 의미가 없는
  UI라 같이 숨긴다(라디오만 남고 제출 버튼이 없는 상태를 방지).
- 버튼을 못 보는 회원(SELLER/ADMIN)에게 "왜 안 보이는지" 안내 문구를 새로 넣을지는 정하지 않았다
  — 이번 계획은 "숨김"까지만이고, 별도 안내 문구 추가는 범위에 넣지 않는다(필요하면 후속 작업).

## 태스크

- [ ] `product.js`: 로그인 회원의 role을 저장하고, 구매 가능 여부에 따라 요약 카드의
      create-team-btn/buy-alone-btn/계속 쇼핑하기 링크/refund-notice-checkbox/
      target-participants-field 노출을 제어
- [ ] `product.js`: 팀 목록의 "참가하기" 버튼도 같은 기준으로 노출 제어
- [ ] 수동 확인: BUYER 로그인, SELLER 로그인(자기 상품/타인 상품), ADMIN 로그인, 비로그인 4가지
      상태에서 상품 상세 페이지 렌더 확인

## 평가(통과) 기준

- `./gradlew compileJava` (프론트만 변경이라 백엔드 영향 없음 확인용)
- 브라우저 수동 확인: BUYER/SELLER/ADMIN/비로그인 4가지 상태에서
  - BUYER: 기존과 동일하게 버튼·체크박스·팀 참가 버튼 모두 노출
  - SELLER/ADMIN: 위 요소 전부 숨김, 카드가 가격 정보까지만 보이고 빈 공간 없이 자연스럽게 마무리
  - 비로그인: 기존과 동일(버튼 노출, 클릭 시 로그인 유도)
