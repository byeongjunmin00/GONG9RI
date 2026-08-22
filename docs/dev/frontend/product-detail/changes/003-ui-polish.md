# 상품 상세 페이지 UI 개선 묶음 (리뷰·문의 표시/디자인/위치, 찜 버튼, 목표인원 자동선택)

대상: frontend/product-detail (주) · product/wishlist · team/crud · inquiry/crud (참조, 완료 시 각 design.md 갱신)
담당: 전용운
관련 백로그: `docs/dev/todo-backlog.md` 1, 2, 3, 5, 6번 (4, 7, 8번은 이번 스코프 아님 — 건드리지 않는다)

## 배경

`docs/dev/todo-backlog.md`에 쌓인 UI 개선 항목 중 사용자가 1·2·3·5·6번을 이번에 진행해달라고
요청했다. 전부 주로 `product.html`/`js/product.js`를 건드리는 작은 프론트 개선이라, 개별 계획
문서 5개로 쪼개지 않고 **문서 하나에 항목별 섹션으로 나눠서** 진행한다(리뷰/재작업 오버헤드
절감). 대신 태스크 체크리스트와 평가 기준은 항목별로 명확히 구분해서, 완료 후
`docs/dev/todo-backlog.md`에서 항목별로 개별 체크할 수 있게 한다.

**공통 전제**: 5개 항목 모두 프론트(정적 HTML/CSS/JS) 전용 변경이고 백엔드(Java) 변경은 필요
없다(아래 항목별 조사 결과 참고). 이 저장소에는 프론트 JS 전용 자동화 테스트 스위트가 없어서
(`./gradlew`는 백엔드 JUnit만 돈다) 평가 기준은 전부 **브라우저 수동 확인**이 된다 — 이 프로젝트의
기존 관행과 동일(예: `team/crud`의 WebSocket 실시간 갱신도 "브라우저 자동화 도구가 없어 직접
확인은 못 했다"는 동일한 한계를 명시하고 있다).

## 사전 조사로 확인된 사실 (다시 조사할 필요 없음)

- **리뷰/문의 응답 DTO에 `createdAt`(LocalDateTime) 필드가 이미 존재한다** —
  `dto/ReviewResponse.java`, `dto/InquiryResponse.java` 둘 다. API는 이미 날짜를 내려주고 있고
  프론트(`createReviewItem`/`createInquiryItem`, `product.js` 1029~1080행/1276~1371행)가 그냥
  안 쓰고 버리는 중이었다. **백엔드 변경 불필요.**
- 이 코드베이스의 날짜 표기 기존 관행: `toLocaleString('ko-KR')`(날짜+시간), `toLocaleDateString('ko-KR')`(날짜만)을
  `admin-members.js`/`seller-mypage.js`/`header-notifications.js` 등 여러 곳에서 이미 쓰고 있다.
  반면 팀 참여자 목록(`formatApproxJoinedAt`, 632~642행)은 "n일 전 참여"처럼 의도적으로 대략적
  표기를 쓴다 — 참여 여부·순서 참고용이라 정확한 시각이 중요하지 않기 때문. 리뷰/문의는 "언제
  쓴 글인지"가 CS·신뢰성 관점에서 의미가 있으므로 이번엔 절대 시각(기존 관행과 통일)으로 표기한다.
- **`.mypage-list-item` 클래스는 product.js 외에 `buyer-mypage.js`, `seller-mypage.js`,
  `admin-refunds.js`도 재사용 중**이다(grep 확인). `components.css:982~999`의 이 클래스 정의
  자체(배경색/테두리/hover 그림자)를 고치면 저 세 화면의 리스트도 함께 바뀐다 — 스코프 격리가
  반드시 필요하다.
- **product.html 전체를 다시 훑어 찜(위시리스트) 관련 요소가 정말 없는지 재확인했다** — `product.html`
  281줄 전체에 `wishlist`/하트 관련 마크업이 전혀 없다. `docs/dev/product/wishlist/design.md`도
  "상품 상세 페이지(product.html)에는 아직 하트를 추가하지 않았다(스코프 밖, 메인 페이지 카드 +
  마이페이지 목록만) — 필요해지면 이후 확장"이라고 명시적으로 기록돼 있다. 즉 상품 사진 쪽
  찜하기는 **다른 곳에 이미 있는 걸 사진 쪽에도 추가하는 게 아니라, 상품 상세 페이지 전체에서
  최초로 도입**하는 것이다 — 기존 찜 상태와의 "동기화 문제"는 없다(같은 API·같은 판정 기준을
  재사용하면 자연히 일치한다).
  - 부가로 확인: `.card-image`(`components.css:142~148`)가 이미 `position: relative`를 갖고
    있고, `#product-image`는 `class="card-image product-detail-image"`로 이 클래스를 그대로
    상속한다. `.card-wishlist-btn`은 `position: absolute`로 이 컨테이너 기준 배치되므로,
    **컨테이너 position 관련 CSS를 새로 추가할 필요는 없어 보인다**(이미 만족됨).
  - 위시리스트 API에는 "이 상품이 지금 찜 상태인지" 개별 조회 엔드포인트가 없다 —
    `GET /api/buyer/mypage/wishlist`(내 찜 전체 목록)만 있다(`docs/api/wishlist.md`). 메인
    페이지 카드(`main.js`)는 로그인한 구매자에 한해 이 전체 목록을 한 번 불러와 이미 렌더링된
    카드에 매칭시키는 방식을 쓴다.
- **목표 인원 라디오(`renderTargetParticipantsOptions`, `product.js` 584~626행)는 옵션 개수와
  무관하게 항상 `selectedTargetParticipants = null`로 시작**하고, 사용자가 라디오를 클릭해야만
  `change` 이벤트로 이 변수가 채워지며 `updateCreateTeamButtonState()`(538~541행)가 버튼을
  활성화한다. 이 함수는 `selectedTargetParticipants === null`이면 무조건 버튼을 비활성 유지한다.
- **동시 작업 참고**: `.claude/worktrees/product-page-visibility-control-c23ec5`라는 이름의
  git worktree가 이미 존재한다(백로그 7·8번과 관련된 것으로 추정 — 판매자/구매자 화면 노출 조건
  작업). 이번 스코프는 아니지만, 같은 `product.html`/`product.js`를 건드릴 가능성이 있어 이후
  병합 시 충돌 여지가 있다는 점만 리스크로 남겨둔다.

---

## 항목 1 — 리뷰·문의 작성일시 표기

**현재 상태**: `createReviewItem`/`createInquiryItem` 둘 다 작성자 아바타+이름, 별점/답변상태,
본문만 렌더링하고 `createdAt`은 응답에 있는데도 화면에 그리지 않는다.

**변경 내용**: 두 함수 모두 항목별로 `createdAt`을 절대 날짜(+필요시 시:분)로 렌더링한다. 표기
위치는 기존 제목/메타 영역(작성자 이름·별점·답변상태와 같은 줄 또는 바로 아래 메타 줄) 중 자연스러운
곳으로 하되, 본문 내용과는 시각적으로 구분되게 한다. 표기 포맷의 정확한 문자열(구분자, 시:분 포함
여부 등)은 Generate 재량이나, **기존 관행(절대 날짜/시각, `toLocaleString`/`toLocaleDateString`
계열)을 따르고 "n일 전" 같은 상대 표기는 쓰지 않는다.**

**영향 파일**: `src/main/resources/static/js/product.js`만. HTML·CSS·백엔드 변경 없음(단, 날짜
텍스트가 들어갈 요소에 최소한의 스타일이 필요하면 `components.css`에 소폭 추가 가능 — 새 클래스
추가 여부는 Generate 재량).

**통과 기준**: 리뷰 탭/문의 탭을 열었을 때 각 리뷰·문의 항목에 작성 시점을 나타내는 날짜(2026년
같은 절대 연도 포함)가 보인다. 값이 비어있거나 "Invalid Date", "NaN" 등이 뜨지 않는다. 오늘
작성된 새 리뷰/문의도 정상적으로 표시된다(방금 작성해서 확인).

---

## 항목 2 — 리뷰·문의 디자인 변경 (박스 제거) + 스코프 격리

**현재 상태**: 리뷰/문의 항목 둘 다 `li.className = 'mypage-list-item'`로 마이페이지용 카드형
박스 스타일(배경색·테두리·hover 시 그림자, `components.css:982~999`)을 그대로 재사용 중이다. 이
클래스는 `buyer-mypage.js`/`seller-mypage.js`/`admin-refunds.js`에서도 쓰고 있어, 정의 자체를
고치면 그 화면들의 리스트도 함께 바뀐다.

**변경 내용**: 상품 상세의 리뷰/문의 항목에서만 "박스" 느낌(배경 채움·테두리·hover 그림자)을
제거한다. **스코프 격리 방법(반드시 지킬 것)**:
- `components.css`의 `.mypage-list-item` **베이스 규칙 자체는 고치지 않는다** — 배경/테두리/
  hover 정의(982~999행)를 그대로 둔다.
- 상품 상세 전용 시각 처리는 (a) 리뷰/문의 항목에 부여하는 클래스 자체를 상품 상세 전용 이름으로
  바꾸거나, (b) `.mypage-list-item`은 그대로 두되 상품 상세 리뷰/문의 리스트 컨테이너
  (`#reviews-list`/`#inquiries-list`)로 한정된 하위 선택자에서만 박스 스타일을 무효화하는 방식
  중 하나로 구현한다 — 어느 쪽을 택할지는 Generate 재량이나, **다른 화면(`buyer-mypage.html`,
  `seller-mypage.html`, 관리자 환불 관리 화면)에 쓰이는 `.mypage-list-item` 렌더링 결과가 이번
  변경 전후로 시각적으로 달라지면 안 된다**는 제약은 고정이다.
- 박스를 뺀 뒤의 정확한 대체 시각 처리(구분선 유무, 여백 등)는 Generate 재량 — 다만 항목 사이가
  전혀 구분되지 않는 형태(줄글처럼 뭉개짐)는 피한다.

**영향 파일**: `src/main/resources/static/js/product.js`(리뷰/문의 li에 부여하는 클래스명, 필요
시), `src/main/resources/static/css/components.css`(신규 클래스 또는 스코프 선택자 추가, 기존
`.mypage-list-item` 정의 라인은 불변).

**통과 기준**:
- 상품 상세 페이지 리뷰/문의 항목이 더 이상 카드형 박스(배경·테두리·hover 그림자)로 보이지 않는다.
- `components.css`에서 `.mypage-list-item` 베이스 규칙(982~999행 상당) 자체의 내용이 diff상
  바뀌지 않았다.
- 구매자 마이페이지, 판매자 마이페이지, 관리자 환불 관리 화면의 `.mypage-list-item` 기반 리스트가
  이번 변경 전후로 동일하게 보인다(직접 열어 비교).

---

## 항목 3 — 리뷰·문의 작성 폼을 목록 위로 이동

**현재 상태**: `product.html` 리뷰 패널은 `#reviews-list`(203행) → `#review-form`(207행) 순서,
문의 패널도 `#inquiries-list`(238행) → `#inquiry-form`(244행) 순서로, 작성 폼이 DOM상 목록
아래에 있다.

**변경 내용**: 두 패널 모두 작성 폼을 목록보다 **위**로 옮긴다(리뷰 패널: `#review-form`이
`#reviews-list`보다 앞, 문의 패널: `#inquiry-form`이 `#inquiries-list`보다 앞). 순수 마크업
순서 변경이다 — `product.js`의 모든 참조가 `getElementById`(순서 독립적)이고 형제 순서에
의존하는 DOM 탐색(`nextElementSibling`류)이 없음을 사전 확인했으므로 JS 로직 변경은 필요 없을
것으로 예상된다(Generate 단계에서 실제로 회귀가 없는지 재확인).

**영향 파일**: `src/main/resources/static/product.html`만(리뷰·문의 패널 내부 순서). `product.js`는
변경 불필요할 것으로 예상되나 확정은 Generate 몫.

**통과 기준**: 리뷰 탭에서 작성 폼이 화면상 목록보다 위에 보인다. 문의 탭도 동일. 기존 기능
(리뷰 작성/수정/삭제, 문의 작성/수정/삭제, 판매자 답변 등록/수정/삭제, 수정 시작 시 폼으로
스크롤되는 `startEditingReview`의 `scrollIntoView` 동작 포함)이 전부 이전과 동일하게 동작한다.

---

## 항목 5 — 상품 사진 영역 찜하기 기능 추가

**현재 상태**: 위 "사전 조사" 참고 — 상품 상세 페이지 어디에도 찜 버튼이 없다(최초 도입, 기존
상태와의 동기화 이슈 없음). 기존 구현 참고 대상: `main.js`의 `toggleWishlist()`(281~316행),
`.card-wishlist-btn`(카드 이미지 위 절대배치, `components.css:169~214`),
`POST`/`DELETE /api/products/{productId}/wishlist`(`docs/api/wishlist.md`).

**변경 내용**: 상품 사진 영역(`#product-image`, 내부에 `.card-image` 클래스를 이미 갖고 있어
`position: relative` 컨테이너 조건은 이미 만족됨)에 찜(하트) 버튼을 추가한다.
- 기존 `.card-wishlist-btn` 클래스와 아이콘 마크업 패턴을 재사용해 시각적 일관성을 유지한다.
- 클릭 동작은 `main.js`의 `toggleWishlist` 패턴(멱등 POST/DELETE, 낙관적으로 아이콘 상태 토글,
  비로그인이면 `/login.html?redirect=...`로 이동, 403이면 "구매자 계정으로 로그인해야 찜할 수
  있어요" 같은 안내)과 동일한 정책을 따른다 — `main.js`는 인덱스 페이지 전용 스크립트라 그
  클로저 내부 함수를 직접 호출할 수는 없으므로, `product.js`에 동등한 로직을 독립적으로 둔다
  (구현 배치는 Generate 재량).
- 페이지 진입 시 하트의 초기 active 상태 판정: 로그인한 구매자에 한해 `GET
  /api/buyer/mypage/wishlist`(전체 목록, 개별 조회 API가 없음을 확인함)로 현재 상품 ID가
  포함돼 있는지 확인해 초기 상태를 채운다. 비로그인/판매자/관리자는 이 조회를 생략한다(main.js와
  동일 조건).

**영향 파일**: `src/main/resources/static/product.html`(하트 버튼 마크업 추가), `js/product.js`
(토글 로직·초기 상태 조회 추가). `components.css`는 기존 `.card-wishlist-btn` 재사용이 목표라
변경 불필요로 예상(위치 상속이 이미 되므로) — 다만 상세 페이지 사진 크기가 카드보다 커서 아이콘
크기 미세조정이 필요하면 Generate 재량으로 소폭 추가 가능.

**통과 기준**:
- 로그인한 구매자가 사진 위 하트를 클릭하면 실제로 `POST`/`DELETE
  /api/products/{id}/wishlist`가 호출되고, 성공 시 하트의 active 상태가 즉시 토글된다.
- 새로고침 후에도 이미 찜한 상품이면 하트가 처음부터 active 상태로 보인다(초기 상태 반영).
- 비로그인 상태에서 클릭하면 로그인 페이지로 이동한다(복귀 경로 포함).
- 판매자 계정으로 클릭하면 403 안내가 뜬다(참가/신설 버튼과 같은 결이 아니라, 위시리스트 기존
  에러 안내 패턴).
- 메인 페이지 카드에서 이미 찜한 상품을 상세 페이지에서 열었을 때도 하트가 active로 보인다(같은
  API·같은 판정 기준을 쓰므로 자연히 일치해야 함 — 별도 동기화 로직 불필요).

---

## 항목 6 — 신설팀 목표 인원 선택 토글: 첫 번째 옵션 자동 선택

**현재 상태**: `renderTargetParticipantsOptions()`가 옵션 개수와 무관하게 `selectedTargetParticipants
= null`로 시작하고, 사용자가 라디오를 직접 클릭해야만 `change` 이벤트로 이 변수가 채워지며
`updateCreateTeamButtonState()`가 버튼을 활성화한다.

**변경 내용**: 옵션을 렌더링하는 시점에 **첫 번째 옵션을 자동으로 선택된 상태로 만든다** —
옵션이 1개든 여러 개든 동일하게 적용한다. 이때 **DOM의 `checked` 속성만 켜는 것으로는
부족하다는 점을 명시한다** — `updateCreateTeamButtonState()`는 DOM이 아니라 JS 변수
`selectedTargetParticipants`(현재 `null`로 초기화됨)를 검사하므로, 라디오를 `checked`로
만드는 것과 **별개로** `selectedTargetParticipants`에 그 옵션의 값(`tier.minCount`)을 함께
채워야 한다. 둘 중 하나만 하면 "라디오는 체크돼 보이는데 버튼은 계속 비활성"이라는 불일치
버그가 난다. 이후 사용자가 다른 옵션을 수동으로 클릭하면 기존 `change` 이벤트 핸들러가 정상
동작해야 한다(회귀 없음).

**영향 파일**: `src/main/resources/static/js/product.js`의 `renderTargetParticipantsOptions()`
함수 범위만. HTML/CSS/백엔드 변경 없음.

**통과 기준**:
- 목표 인원 옵션이 **1개뿐인 상품**의 상세 페이지를 열었을 때, 그 옵션이 처음부터 선택된
  상태(라디오 checked)로 보이고, 환불 동의 체크박스만 마저 체크하면(추가 클릭 없이) "신규
  공구팀 신설하기" 버튼이 활성화된다.
- 목표 인원 옵션이 **여러 개인 상품**의 상세 페이지를 열었을 때도 첫 번째 옵션이 자동으로
  선택돼 있고, 마찬가지로 환불 동의만 체크하면 버튼이 활성화된다.
- 자동 선택된 상태에서 사용자가 다른(두 번째 이후) 옵션을 클릭하면 정상적으로 그 옵션으로
  전환되고 버튼 활성 조건도 그 값 기준으로 재평가된다.
- 페이지를 새로고침해 상품을 다시 열 때마다(팀 목록 재조회가 아니라 `renderTargetParticipantsOptions`가
  다시 호출되는 시점마다) 동일하게 첫 옵션이 자동 선택된다.

---

## 태스크 체크리스트 (항목별 — 완료 후 `todo-backlog.md` 개별 체크용)

### 1. 리뷰·문의 날짜/시간 표기
- [ ] `createReviewItem`에 `review.createdAt` 렌더링 추가
- [ ] `createInquiryItem`에 `inquiry.createdAt` 렌더링 추가
- [ ] 절대 날짜(+필요시 시:분) 표기, 기존 코드베이스 관행과 통일 확인
- [ ] 브라우저에서 리뷰/문의 각각 날짜 노출 확인

### 2. 리뷰·문의 디자인 변경 (박스 제거)
- [ ] 리뷰/문의 항목 전용 클래스 또는 스코프 선택자 결정·적용
- [ ] `.mypage-list-item` 베이스 규칙 불변 확인(diff)
- [ ] 상품 상세 리뷰/문의에서 박스 스타일 제거 확인
- [ ] 마이페이지(구매자/판매자)·관리자 환불 관리 화면 영향 없음 확인

### 3. 리뷰·문의 작성 박스 위치 조정
- [ ] `product.html` 리뷰 패널: 폼을 목록 위로 이동
- [ ] `product.html` 문의 패널: 폼을 목록 위로 이동
- [ ] 리뷰/문의 CRUD 및 판매자 답변 기능 회귀 없음 확인

### 5. 제품 사진 쪽 찜하기 기능 추가
- [ ] `product.html`에 찜 버튼 마크업 추가(`#product-image` 영역)
- [ ] `product.js`에 토글 로직(POST/DELETE 연동) 추가
- [ ] 페이지 진입 시 초기 찜 상태 조회·반영 로직 추가
- [ ] 로그인/비로그인/판매자 시나리오 각각 확인
- [ ] 메인 카드 찜 상태와의 일치 확인

### 6. 신설팀 목표 인원 선택 토글 자동 선택
- [ ] `renderTargetParticipantsOptions()`에서 첫 옵션 DOM `checked` 처리
- [ ] 동시에 `selectedTargetParticipants` 변수 초기값 설정 + `updateCreateTeamButtonState()` 재호출
- [ ] 옵션 1개 상품에서 자동 선택+버튼 활성화 확인
- [ ] 옵션 여러 개 상품에서 첫 옵션 자동 선택+버튼 활성화 확인
- [ ] 수동으로 다른 옵션 선택 시 정상 전환 확인(회귀 없음)

---

## 리스크 / 전제

- **자동화 테스트 부재**: 5개 항목 전부 프론트 전용이고 이 저장소에 프론트 JS 테스트 스위트가
  없어(`./gradlew test`는 백엔드만 검증), 평가는 전적으로 브라우저 수동 확인에 의존한다. 회귀
  여부(특히 항목 3의 DOM 순서 변경, 항목 6의 상태 변수 변경)를 자동으로 잡아낼 안전망이 없다.
- **항목 2, CSS 클래스 공유 스코프**: `.mypage-list-item`을 잘못 건드리면 마이페이지·관리자
  화면까지 조용히 깨질 수 있다(빌드는 통과하지만 시각적 회귀라 `./gradlew`로 잡히지 않음) — 위
  "스코프 격리 방법"을 반드시 지켜야 한다.
- **항목 5, 역할 노출**: 판매자·관리자 계정으로 자기 상품(또는 임의 상품) 상세를 봐도 하트 버튼
  자체는 노출되고 클릭 시에만 403으로 걸러진다(메인 페이지 카드와 동일한 기존 정책 재사용) —
  이 동작이 이상하게 느껴진다면(예: 백로그 8번 "판매자 전용 화면에 구매자 전용 기능 노출" 우려와
  결이 비슷함) 이번 스코프에서 판단하지 않고 기존 위시리스트 정책을 그대로 따른다. 필요시 별도
  백로그 항목으로 다룬다.
- **동시 작업 워크트리**: `.claude/worktrees/product-page-visibility-control-c23ec5`(백로그
  7·8번 관련 추정)가 존재해 같은 `product.html`/`product.js`를 동시에 건드릴 가능성이 있다 —
  merge 시 충돌 여지, 이번 계획의 실행 자체를 막지는 않지만 사용자에게 인지시켜 둔다.
- **평점/문의 항목 레이아웃 재조정**: 항목 1(날짜 추가)과 항목 2(박스 제거)가 같은 `createReviewItem`/
  `createInquiryItem`/같은 CSS 영역을 함께 건드린다 — 순서상 같은 PR/커밋 안에서 함께 처리되는
  것이 자연스럽다(따로 나눠 하면 중간 상태가 어색할 수 있음). Generate 시 두 항목을 이어서
  처리할 것을 권장(강제는 아님).

## 완료(Evaluate 통과) 시 갱신 대상 문서

- `docs/dev/frontend/product-detail/design.md` — 리뷰/문의 항목 표시(날짜·박스 제거)와 폼 위치,
  찜 버튼 추가를 반영.
- `docs/dev/product/wishlist/design.md` — "상품 상세 페이지에는 아직 하트를 추가하지 않았다"
  문장을 걷어내고 실제 배치·연동 방식으로 갱신.
- `docs/dev/team/crud/design.md` — "목표 인원 선택" 섹션에 첫 옵션 자동 선택 동작 반영.
- `docs/dev/inquiry/crud/design.md` — 프론트엔드 섹션의 폼/목록 배치 서술 갱신(필요 시).
- 이 문서(`ongoing/product-detail-ui-polish.md`)는 완료 후 어느 `changes/`로 보낼지 애매할 수
  있다(5개 항목이 서로 다른 개념에 걸쳐 있음) — 주 대상인 `frontend/product-detail/changes/`로
  채번 이동하고, 나머지 design.md 갱신 사실은 그 changes 문서 안에 함께 기록하는 방식을 제안한다
  (Evaluate 단계에서 최종 확정).
