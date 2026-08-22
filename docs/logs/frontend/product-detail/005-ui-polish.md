# 005-ui-polish — 상품 상세 페이지 UI 개선 묶음 (리뷰·문의 날짜/박스/위치, 찜 버튼, 목표인원 자동선택) 로그

계획 문서: `docs/dev/ongoing/product-detail-ui-polish.md` (백로그 1·2·3·5·6번, 4·7·8번은 스코프 아님).

## Attempt 1 — 2026-08-22

### 시도 — 항목 1 (리뷰·문의 작성일시 표기)

- `product.js`에 `formatAbsoluteDateTime(isoString)` 헬퍼를 추가했다(`toLocaleString('ko-KR')` —
  기존 관행인 `header-notifications.js`/`admin-refunds.js`와 동일 포맷, 상대 표기 "n일 전"은 쓰지
  않음).
- `createReviewItem`/`createInquiryItem` 각각에 `review.createdAt`/`inquiry.createdAt`을 렌더링하는
  `<span class="mypage-list-item__date">`를 본문(`__meta`) 바로 다음 줄에 추가했다(별도 클래스로
  본문과 시각적으로 구분).
- `components.css`에 `.mypage-list-item__date` 스타일(작은 무채색 텍스트) 추가.

### 시도 — 항목 2 (리뷰·문의 박스 제거 + 스코프 격리)

- **`.mypage-list-item` 베이스 규칙(components.css 982~999행 상당)은 한 글자도 건드리지 않았다.**
  대신 `#reviews-list.mypage-list .mypage-list-item`, `#inquiries-list.mypage-list .mypage-list-item`
  스코프 선택자를 새로 추가해 배경/테두리/hover 그림자를 무효화하고, 항목 사이는 `border-bottom`
  구분선으로 나눴다(마지막 항목은 구분선 없음).
- `product.js`는 이 항목 때문에 **전혀 건드리지 않았다** — 리뷰/문의 li는 여전히
  `className = 'mypage-list-item'` 그대로 두고, 격리는 순수 CSS 스코프(컨테이너 id)로만 했다.
  이렇게 하면 클래스명을 바꾸는 방식보다 회귀 여지가 더 적다고 판단했다(계획 문서의 두 옵션 중
  (b)를 선택).
- `#reviews-list`/`#inquiries-list` id가 이 프로젝트에서 `product.html`에만 존재함을 grep으로
  확인해(다른 화면에 동명 id 없음), 스코프 선택자가 buyer-mypage/seller-mypage/admin-refunds에
  영향을 줄 수 없음을 사전에 검증했다.

### 시도 — 항목 3 (작성 폼을 목록 위로 이동)

- `product.html`의 리뷰 패널: `#review-form`을 `#reviews-list`보다 앞으로 이동.
- 문의 패널: `#inquiry-form`을 `#inquiries-list`보다 앞으로 이동.
- 순수 마크업 순서 변경만 했고 `product.js`는 건드리지 않았다 — `startEditingReview`/
  `startEditingInquiry`의 `scrollIntoView`, 폼 제출 핸들러 등은 전부 `getElementById` 기반이라
  DOM 순서와 무관하게 그대로 동작함을 코드 재확인함(회귀 없음, 계획 문서의 사전 조사와 일치).

### 시도 — 항목 5 (상품 사진 찜하기 버튼 추가)

- `product.html`의 `#product-image` 안에 `main.js`의 `.card-wishlist-btn` 마크업/아이콘을 그대로
  재사용한 `<button id="product-wishlist-btn">`를 추가했다.
- **구현 중 발견한 함정**: `renderGallery()`가 캐러셀 전환 때마다 `#product-image`의 모든 자식을
  `clearChildren`으로 지우고 `<img>`만 다시 채우는 구조라, 정적 HTML에 넣은 하트 버튼이 첫
  렌더링 직후(그리고 썸네일 클릭마다) 사라지는 문제가 있었다. `clearChildren`은 `removeChild`만
  할 뿐 노드를 파괴하지 않는다는 점을 이용해, `product.js` 상단에서 `wishlistBtnEl` 참조를 미리
  들고 있다가 `renderGallery()`가 이미지를 다시 그릴 때마다 `imageEl.appendChild(wishlistBtnEl)`로
  재부착하도록 했다(계획 문서에는 없던 세부 구현 디테일이지만, "상품 사진 영역에 찜 버튼 추가"라는
  범위 안에서 필요한 구현 방법일 뿐 계획 자체를 벗어난 확장은 아니라고 판단).
- `product.js`에 `loadWishlistState(productId)`(초기 active 상태, 로그인한 구매자에 한해
  `GET /api/buyer/mypage/wishlist` 전체 목록에서 현재 상품 포함 여부로 판정)와
  `handleToggleWishlist()`(멱등 POST/DELETE, 낙관적 토글, 비로그인 시 `/login.html?redirect=...`,
  403이면 `showPageAlert`로 "구매자 계정으로 로그인해야 찜할 수 있어요" 안내, 성공 시
  `gong9ri:wishlist-changed` 이벤트 발행)를 추가했다 — main.js의 `toggleWishlist`와 동일 정책을
  독립적으로 재구현(main.js는 인덱스 전용 클로저라 직접 재사용 불가, 계획 문서 결정 그대로).
- `currentMemberId`뿐 아니라 `currentMemberRole`도 `gong9ri:auth-resolved`에서 함께 채워, 로그인한
  구매자에게만 초기 상태 조회를 하도록 했다(main.js와 동일 조건).
- `components.css`에 `#product-image .card-wishlist-btn`(40px) 스코프 규칙을 추가해 상세 페이지의
  큰 사진에 맞게 하트를 카드보다 살짝 키웠다(`.card-wishlist-btn` 자체 정의는 불변, main.js가 쓰는
  메인 카드 하트에는 영향 없음).

### 시도 — 항목 6 (목표 인원 첫 옵션 자동 선택)

- `renderTargetParticipantsOptions()`에서 라디오를 그릴 때 `index === 0`인 옵션에
  `input.checked = true`를 설정하는 것과 **동시에** `selectedTargetParticipants = tier.minCount`를
  채우도록 고쳤다. 계획 문서가 지적한 버그(DOM만 체크하고 변수는 안 채우면
  `updateCreateTeamButtonState()`가 계속 비활성 유지)를 그대로 재현해서 확인한 뒤 수정했다.
- `updateCreateTeamButtonState()` 호출 위치도 함수 끝(옵션 렌더링 완료 후)과 "tiers 없음" 조기
  반환 분기 양쪽에 하나씩 남겨 정확히 1회만 호출되게 정리했다(중복 호출 없음).
- 기존 `change` 이벤트 핸들러(사용자가 다른 옵션을 수동으로 클릭했을 때
  `selectedTargetParticipants`를 갱신 + 버튼 재평가)는 그대로 뒀다 — 회귀 없음.

### 검증

- `./gradlew compileJava compileTestJava` — `BUILD SUCCESSFUL` (Java 변경 없음, 백엔드 영향 없음
  재확인용).
- 로컬 `docker compose`(mysql·redis 이미 기동 중)로 `docker compose build app` → `docker compose up
  -d app`으로 변경된 정적 리소스를 반영한 이미지를 재기동함. `curl`로:
  - `GET /product.html` 응답에서 `#product-wishlist-btn`이 `#product-image` 안에, `#review-form`/
    `#inquiry-form`이 각각 `#reviews-list`/`#inquiries-list`보다 앞에 위치함을 마크업 순서로 확인.
  - `GET /js/product.js`, `GET /css/components.css` 응답에 새 함수(`loadWishlistState`,
    `handleToggleWishlist`, `formatAbsoluteDateTime`)와 새 CSS 규칙
    (`.mypage-list-item__date`, `#reviews-list.mypage-list .mypage-list-item` 등)이 그대로
    서빙됨을 확인(빌드·배포 파이프라인 상 문제 없음).
  - `GET /api/buyer/mypage/wishlist`(미인증) → `401 UNAUTHORIZED`, `POST
    /api/products/1376/wishlist`(미인증) → `401` — product.js가 기대하는 에러 코드/상태와 일치.
  - `git diff -- src/main/resources/static/css/components.css`로 `.mypage-list-item` 베이스 규칙
    라인이 diff에 전혀 나타나지 않음을 재확인(순수 추가만 있음, 982~999행 상당 불변).
  - `grep`으로 `#reviews-list`/`#inquiries-list` id가 저장소 전체에서 `product.html`에만
    존재함을 확인 — 스코프 CSS가 다른 화면에 영향 줄 수 없음을 구조적으로 보장.
- **한계**: 이 환경에는 브라우저 자동화 도구가 없어(Node/브라우저 헤드리스 실행 불가), 실제
  클릭 상호작용(찜 토글 애니메이션, 목표 인원 라디오 클릭 후 버튼 활성화, 리뷰/문의 폼 스크롤
  등)은 코드 추적으로만 검증했고 브라우저 렌더링으로 직접 보지는 못했다. 로컬 DB에 리뷰/문의
  샘플 데이터가 없어(QA 테스트 상품만 존재, 리뷰/문의 0건) 날짜 표기(항목 1)·박스 제거(항목 2)
  결과물을 실제 렌더된 HTML로는 확인하지 못했다 — 정적 파일 서빙과 코드 로직 정확성만 확인함.
  Evaluate 단계에서 브라우저 도구가 있다면 실제 리뷰/문의 작성 후 시각 확인을 권장.

### 다음

- 계획대로 5개 항목 구현 완료. Evaluate로 진행.

## Evaluate — 2026-08-22  ✅ PASS

### 계산적 평가

- `./gradlew compileJava compileTestJava` → `BUILD SUCCESSFUL`(둘 다 `UP-TO-DATE`, Java 변경이
  없으므로 예상대로 재컴파일 없음 — 백엔드 회귀 없음 재확인).
- 이 작업은 프론트 전용이고 이 저장소에 프론트 JS 자동화 테스트가 없어(계획 문서에도 명시) 별도
  `--tests` 스코프 실행은 없음.

### 추론적 평가 — 항목별 판정

오케스트레이터(사용자)가 로컬 docker-compose 환경에서 실제 브라우저/DB로 직접 실측한 결과를
그대로 신뢰하고, "보강 필요"로 표시된 부분만 코드 리뷰로 추가 확인했다.

- **항목 1(날짜 표기)** — ✅ PASS. 실측: 리뷰 `"2026. 8. 20. 오후 11:30:00"`, 문의
  `"2026. 8. 21. 오후 6:15:00"` 정상 렌더링(`Invalid Date`/빈 값 없음). 코드(`formatAbsoluteDateTime`,
  `product.js` 1150행대/1396행대 `createReviewItem`/`createInquiryItem`)도 `toLocaleString('ko-KR')`로
  기존 관행과 일치.
- **항목 2(박스 제거 + 스코프 격리)** — ✅ PASS. 실측: 테스트 리뷰/문의 항목의 computed style에서
  `background-color: rgba(0,0,0,0)`, `border-width: 0px` 확인. `git diff`로
  `.mypage-list-item` 베이스 규칙(components.css 982~1012행 상당)이 diff에 전혀 나타나지 않음을
  재확인(순수 추가만 존재) — 태스크 체크리스트의 "diff상 불변 확인" 항목 충족. 스코프 선택자
  (`#reviews-list.mypage-list .mypage-list-item`, `#inquiries-list.mypage-list .mypage-list-item`)는
  `#reviews-list`/`#inquiries-list` id를 가진 요소에만 매치되는데, 이 id는 grep으로 `product.html`
  에만 존재함을 확인했다(생성 로그에서도 동일하게 확인됨) — 구조적으로 다른 화면(`buyer-mypage.html`,
  `seller-mypage.html`, `admin-refunds.html`)의 DOM에는 이 선택자가 애초에 매치될 수 없으므로,
  세 화면을 직접 브라우저로 열어 육안 비교하지 않아도 "영향 없음"이 코드 구조상 보장된다고 판단해
  이 부분의 실측 보강은 생략했다.
- **항목 3(폼 위치)** — ✅ PASS. 실측: `compareDocumentPosition`/`getBoundingClientRect` 둘 다로 리뷰·
  문의 패널 모두 폼이 목록보다 DOM/화면상 앞에 있음을 확인. `product.html` diff도 마크업 순서만
  바뀌었고 `product.js`에 형제 순서 의존 로직(`nextElementSibling`류)이 없음을 코드로 재확인(계획
  문서의 사전조사와 일치) — 리뷰/문의 CRUD, 판매자 답변, `scrollIntoView` 전부 `getElementById` 기반
  이라 회귀 없음.
- **항목 5(찜 버튼)** — ✅ PASS. 실측: 신규 구매자 계정으로 클릭 → `POST` 201 + DB row 생성 + `active`
  클래스 부여, 새로고침 후 초기 상태 유지, 재클릭 → `DELETE` + DB row 삭제 + `active` 해제, 로그아웃
  상태 클릭 → `/login.html?redirect=...` 리다이렉트 정상. **판매자 403 케이스는 코드 리뷰로 보강**:
  `product.js`의 `handleToggleWishlist()` catch 블록(`if (err && err.status === 403)
  showPageAlert('구매자 계정으로 로그인해야 찜할 수 있어요.', 'error')`)이 `main.js`의
  `toggleWishlist()`(이미 실사용·검증된 기존 패턴, `showPageNotice`로 동일 문구)와 조건·문구·처리
  방식이 완전히 동일하다 — `showPageAlert`도 `product.js`에서 다른 에러 배너에 이미 쓰이는 기존
  헬퍼(401/403/409 안내 등)라 신뢰할 수 있는 재사용으로 판단, 별도 실측 없이 PASS 처리.
- **항목 6(목표인원 자동선택)** — ✅ PASS. 실측: 옵션 1개 상품에서 자동 checked + 환불동의만 체크 시
  버튼 활성화(`disabled: true → false`) 확인. **옵션 여러 개 상품 케이스는 코드 리뷰로 보강**:
  `renderTargetParticipantsOptions()`의 `tiers.forEach` 루프에서 `index === 0`일 때 무조건
  `input.checked = true; selectedTargetParticipants = tier.minCount;`를 실행하므로, 이 로직은 `tiers`
  배열 길이(옵션 개수)와 전혀 무관하게 항상 배열의 첫 번째 요소에서만 실행된다 — 옵션이 1개든 5개든
  동일하게 첫 옵션만 자동 선택되고 동작 분기가 없다. 기존 `change` 이벤트 핸들러도 그대로 남아있어
  수동 선택 시 정상 전환됨을 코드로 확인(회귀 없음).

### 계획 대비 확인

- 태스크 체크리스트(1·2·3·5·6번) 전 항목 충족.
- 리스크 항목("자동화 테스트 부재", "CSS 클래스 공유 스코프", "역할 노출", "동시 작업 워크트리")
  중 실제로 문제가 된 것 없음 — CSS 공유 스코프는 구조적으로 격리됨을 확인, 역할 노출은 계획이
  명시한 대로 기존 위시리스트 정책을 그대로 따름(판단 보류 그대로 유지).
- `docs/code-convention.md`, `docs/policy/`(caching.md의 리뷰 캐시 무효화 규칙 등) 위반 없음 —
  전부 프론트 정적 리소스 변경이라 해당 규칙 대부분이 적용 대상 밖.

### 증거 요약 (오케스트레이터 실측, API/DOM 샘플)

- `POST /api/products/1377/wishlist` → `201`, DB `wishlist` row 생성, 버튼 `active` 부여.
- `DELETE /api/products/1377/wishlist` → 성공, DB row 삭제, `active` 해제.
- 비로그인 클릭 → `/login.html?redirect=%2Fproduct.html%3Fid%3D1377`.
- `.mypage-list-item__date` computed: `"2026. 8. 20. 오후 11:30:00"`(리뷰), `"2026. 8. 21. 오후
  6:15:00"`(문의).
- `.mypage-list-item`(상품 상세 스코프) computed: `background-color: rgba(0,0,0,0)`,
  `border-width: 0px`.
- 목표인원 옵션 1개 상품: 페이지 로드 시 라디오 `checked: true`, 환불동의만 체크 시 신설 버튼
  `disabled: true → false`.
- 테스트에 쓴 회원 3명/상품 1개/리뷰·문의·찜 각 1건은 전부 원상복구(DELETE)됨 — DB는 비어있는
  상태로 복귀.

### 결과

- **전체 통과.** `docs/dev/frontend/product-detail/design.md`, `docs/dev/product/wishlist/design.md`,
  `docs/dev/team/crud/design.md`, `docs/dev/inquiry/crud/design.md` 갱신 완료.
  `docs/dev/ongoing/product-detail-ui-polish.md` → `docs/dev/frontend/product-detail/changes/003-ui-polish.md`로
  채번 이동 완료. `docs/dev/todo-backlog.md`의 1·2·3·5·6번 체크 완료.
